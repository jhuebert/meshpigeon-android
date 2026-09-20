package app.meshpigeon.protocol

/**
 * Payload structs for the types MeshPigeon v1 uses (03-mesh-protocol §1).
 * All 16/32-bit fields little-endian. These encode/decode the *payload*
 * bytes that ride inside a [RawPacket].
 */

/** TXT_MSG plaintext: [timestamp:4][attempt&3|txt_type<<2:1][text\0]. */
data class TxtMsgPayload(
    val timestamp: Long,
    val txtType: Int,
    val attempt: Int,
    val text: String,
) {
    fun encode(): ByteArray {
        val textBytes = (text + "\u0000").toByteArray(Charsets.UTF_8)
        val out = ByteArray(5 + textBytes.size)
        Crypto.leU32(timestamp).copyInto(out)
        out[4] = (((txtType and 0x3F) shl 2) or (attempt and 0x3)).toByte()
        textBytes.copyInto(out, 5)
        return out
    }

    companion object {
        fun decode(payload: ByteArray): TxtMsgPayload {
            require(payload.size >= 6) { "TXT_MSG too short" }
            val ts = Crypto.leU32At(payload, 0)
            val meta = payload[4].toInt() and 0xFF
            val text = nulTerminated(payload, 5)
            return TxtMsgPayload(ts, meta shr 2, meta and 0x3, text)
        }

        /** String up to the first NUL (AES zero-padding may follow). */
        fun nulTerminated(bytes: ByteArray, start: Int): String {
            var end = bytes.size
            for (i in start until bytes.size) {
                if (bytes[i] == 0.toByte()) {
                    end = i
                    break
                }
            }
            return bytes.decodeToString(start, end)
        }
    }
}

/** ACK payload: 4-byte checksum (§Ack.compute). */
data class AckPayload(val checksum: ByteArray) {
    init {
        require(checksum.size == 4) { "ACK checksum is 4 bytes" }
    }

    fun encode(): ByteArray = checksum.copyOf()
}

/**
 * ADVERT payload (node advertisement): pubkey(32) + timestamp(4) +
 * signature(64) + appdata. Signature covers pubkey, timestamp and appdata.
 */
data class AdvertPayload(
    val publicKey: ByteArray,
    val timestamp: Long,
    val signature: ByteArray,
    val appData: AdvertAppData,
) {
    fun encode(): ByteArray =
        publicKey + Crypto.leU32(timestamp) + signature + appData.encode()

    companion object {
        fun decode(payload: ByteArray): AdvertPayload {
            require(payload.size >= 32 + 4 + 64 + 1) { "ADVERT too short" }
            val pub = payload.copyOfRange(0, 32)
            val ts = Crypto.leU32At(payload, 32)
            val sig = payload.copyOfRange(36, 100)
            val app = AdvertAppData.decode(payload.copyOfRange(100, payload.size))
            return AdvertPayload(pub, ts, sig, app)
        }
    }
}

/**
 * Advert appdata: flags + optional lat/lon (deg × 1e6) + optional name.
 * Flags per MeshCore: 0x01 chat node, 0x02 repeater, 0x03 room server,
 * 0x04 sensor, 0x10 has location, 0x20/0x40 features, 0x80 has name.
 */
data class AdvertAppData(
    val flags: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val name: String? = null,
) {
    fun encode(): ByteArray {
        require(name == null || name.toByteArray(Charsets.UTF_8).size <= MAX_NAME_BYTES) {
            "advert name exceeds $MAX_NAME_BYTES bytes"
        }
        val out = ArrayList<ByteArray>()
        out.add(byteArrayOf(flags.toByte()))
        if (latitude != null && longitude != null) {
            // deg × 1e6 as signed 32-bit, two's complement for negatives
            out.add(Crypto.leU32((latitude * 1_000_000).toLong().toInt().toLong() and 0xFFFFFFFFL))
            out.add(Crypto.leU32((longitude * 1_000_000).toLong().toInt().toLong() and 0xFFFFFFFFL))
        }
        name?.let { out.add(it.toByteArray(Charsets.UTF_8)) }
        return out.reduce { a, b -> a + b }
    }

    companion object {
        const val MAX_NAME_BYTES = 67
        const val FLAG_CHAT_NODE = 0x01
        const val FLAG_REPEATER = 0x02
        const val FLAG_ROOM_SERVER = 0x03
        const val FLAG_SENSOR = 0x04
        const val FLAG_HAS_LOCATION = 0x10
        const val FLAG_HAS_NAME = 0x80

        fun decode(app: ByteArray): AdvertAppData {
            require(app.isNotEmpty()) { "empty appdata" }
            var flags = app[0].toInt() and 0xFF
            var i = 1
            var lat: Double? = null
            var lon: Double? = null
            if (flags and FLAG_HAS_LOCATION != 0 && app.size >= i + 8) {
                lat = Crypto.leU32At(app, i).toInt() / 1_000_000.0   // signed i32
                lon = Crypto.leU32At(app, i + 4).toInt() / 1_000_000.0
                i += 8
            }
            if (flags and FLAG_HAS_NAME != 0 && i < app.size) {
                return AdvertAppData(flags, lat, lon, app.decodeToString(i, app.size))
            }
            return AdvertAppData(flags, lat, lon, null)
        }
    }
}

/**
 * GRP_TXT/GRP_DATA share the same outer framing:
 * `[channel_hash:1][MAC:2][ciphertext]` where the ciphertext decrypts to
 * - GRP_TXT: [timestamp:4][flags:1]["sender: text"]
 * - GRP_DATA: [data_type:2][data_len:1][data]
 */
data class GroupTxtPayload(
    val channelHash: Int,
    val timestamp: Long,
    val senderAndText: String,
) {
    companion object {
        fun decodeOuter(payload: ByteArray): Pair<Int, ByteArray>? {
            if (payload.size < 3 + 16) return null
            val hash = payload[0].toInt() and 0xFF
            return hash to payload.copyOfRange(1, payload.size)
        }
    }
}

/** GRP_DATA sub-message types MeshPigeon layers on top (03 §6, app-to-app). */
object GroupDataTypes {
    const val REACTION = 0x0001
    const val REPLY_HINT = 0x0002
    const val READ_RECEIPT = 0x0003
    const val TYPING = 0x0004
}

/**
 * REACTION GRP_DATA body (03 §6): `[target_tag:4][name_len:1][name][emoji]`,
 * targeted at the packet tag (SHA-256 prefix over the raw packet, `PacketCodec
 * .packetTag`) of the reacted-to message. Group traffic carries only display
 * names, so the sender's name rides along.
 */
object ReactionData {
    fun encode(targetTag: ByteArray, senderName: String, emoji: String): ByteArray {
        require(targetTag.size == PacketCodec.PACKET_TAG_SIZE) { "target tag must be ${PacketCodec.PACKET_TAG_SIZE} bytes" }
        val name = senderName.toByteArray(Charsets.UTF_8)
        require(name.size <= 255) { "sender name too long" }
        return targetTag + byteArrayOf(name.size.toByte()) + name +
            emoji.toByteArray(Charsets.UTF_8)
    }

    /** Returns (target tag, sender name, emoji) or null on malformed input. */
    fun decode(data: ByteArray): Triple<ByteArray, String, String>? {
        if (data.size < PacketCodec.PACKET_TAG_SIZE + 2) return null
        val nameLen = data[PacketCodec.PACKET_TAG_SIZE].toInt() and 0xFF
        val nameEnd = PacketCodec.PACKET_TAG_SIZE + 1 + nameLen
        if (data.size <= nameEnd) return null
        return Triple(
            data.copyOfRange(0, PacketCodec.PACKET_TAG_SIZE),
            data.decodeToString(PacketCodec.PACKET_TAG_SIZE + 1, nameEnd),
            data.decodeToString(nameEnd, data.size),
        )
    }
}

data class GroupDataPayload(
    val channelHash: Int,
    val dataType: Int,
    val data: ByteArray,
) {
    companion object {
        /** GRP_DATA plaintext: [data_type:2][data_len:1][data]. */
        fun encodePlaintext(dataType: Int, data: ByteArray): ByteArray {
            require(data.size <= 255) { "GRP_DATA data too long" }
            return byteArrayOf(
                (dataType and 0xFF).toByte(),
                ((dataType shr 8) and 0xFF).toByte(),
                data.size.toByte(),
            ) + data
        }

        fun decodePlaintext(plain: ByteArray): Pair<Int, ByteArray>? {
            if (plain.size < 3) return null
            val dtype = (plain[0].toInt() and 0xFF) or ((plain[1].toInt() and 0xFF) shl 8)
            val len = plain[2].toInt() and 0xFF
            if (plain.size < 3 + len) return null
            return dtype to plain.copyOfRange(3, 3 + len)
        }
    }
}
