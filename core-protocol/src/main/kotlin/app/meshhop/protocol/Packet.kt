package app.meshhop.protocol

/**
 * MeshCore-compatible v1 packet framing (03-mesh-protocol §1).
 *
 * Wire layout:
 * ```
 * [header:1][transport_codes:0/4][path_length:1][path:0..64][payload:0..184]
 * ```
 *
 * Header `0bVVPPPPRR`: V = payload version (v1 = 0), P = payload type,
 * R = route type.
 *
 * CRITICAL (a real bug class in meshcore-open): the path_length byte packs
 * hop count (bits 0–5) and hash size (bits 6–7, stored as size − 1). The
 * effective path byte length is `hop_count * hash_size` — it is NEVER the
 * raw byte value of path_length.
 */
object PacketSpec {
    const val MAX_PAYLOAD = 184
    const val MAX_PATH_BYTES = 64
    const val MAX_PACKET = 1 + 4 + 1 + MAX_PATH_BYTES + MAX_PAYLOAD

    // Route types (bits 0–1)
    const val ROUTE_TRANSPORT_FLOOD = 0x00
    const val ROUTE_FLOOD = 0x01
    const val ROUTE_DIRECT = 0x02
    const val ROUTE_TRANSPORT_DIRECT = 0x03

    // Payload types (bits 2–5)
    const val PAYLOAD_REQ = 0x00
    const val PAYLOAD_RESPONSE = 0x01
    const val PAYLOAD_TXT_MSG = 0x02
    const val PAYLOAD_ACK = 0x03
    const val PAYLOAD_ADVERT = 0x04
    const val PAYLOAD_GRP_TXT = 0x05
    const val PAYLOAD_GRP_DATA = 0x06
    const val PAYLOAD_ANON_REQ = 0x07
    const val PAYLOAD_PATH = 0x08
    const val PAYLOAD_TRACE = 0x09
    const val PAYLOAD_MULTIPART = 0x0A
    const val PAYLOAD_CONTROL = 0x0B
    const val PAYLOAD_RAW_CUSTOM = 0x0F

    // Payload versions (bits 6–7): v1 = 1-byte src/dest hashes, 2-byte MAC
    const val PAYLOAD_VERSION_1 = 0x00

    fun header(routeType: Int, payloadType: Int, payloadVersion: Int = PAYLOAD_VERSION_1): Int =
        ((payloadVersion and 0x3) shl 6) or ((payloadType and 0xF) shl 2) or (routeType and 0x3)

    fun routeType(header: Int): Int = header and 0x3
    fun payloadType(header: Int): Int = (header shr 2) and 0xF
    fun payloadVersion(header: Int): Int = (header shr 6) and 0x3

    fun isFlood(header: Int): Boolean {
        val r = routeType(header)
        return r == ROUTE_FLOOD || r == ROUTE_TRANSPORT_FLOOD
    }

    fun hasTransportCodes(header: Int): Boolean {
        val r = routeType(header)
        return r == ROUTE_TRANSPORT_FLOOD || r == ROUTE_TRANSPORT_DIRECT
    }

    // TXT_MSG plaintext types (upper 6 bits of the attempt byte)
    const val TXT_TYPE_PLAIN = 0x00
    const val TXT_TYPE_CLI_DATA = 0x01
    const val TXT_TYPE_SIGNED_PLAIN = 0x02
    const val TXT_TYPE_CLI_COMMAND = 0x03
}

/** Hash size used in the path byte; MeshHop defaults to 3 bytes (plan 03 §2). */
enum class PathHashSize(val bytes: Int) {
    ONE(1), TWO(2), THREE(3);

    val code: Int get() = bytes - 1

    companion object {
        fun fromCode(code: Int): PathHashSize = when (code and 0x3) {
            0 -> ONE
            1 -> TWO
            2 -> THREE
            else -> throw PacketDecodeException("hash size code 3 is reserved")
        }
    }
}

/**
 * A parsed path. `hashes` are the per-hop node hash prefixes.
 * Hop count is ALWAYS derived as `path bytes ÷ hash size` — never from a
 * byte length.
 */
data class Path(
    val hashSize: PathHashSize,
    val hashes: List<ByteArray>,
) {
    val hopCount: Int get() = hashes.size

    override fun equals(other: Any?): Boolean =
        other is Path && other.hashSize == hashSize &&
            hashes.size == other.hashes.size &&
            hashes.zip(other.hashes).all { (a, b) -> a.contentEquals(b) }

    override fun hashCode(): Int = hashSize.hashCode() * 31 + hashes.size
}

/** Raw on-air packet, fully decoded but payload-agnostic. */
data class RawPacket(
    val header: Int,
    val transportCodes: IntArray?, // 2 u16s when present
    val path: Path,
    val payload: ByteArray,
) {
    val routeType: Int get() = PacketSpec.routeType(header)
    val payloadType: Int get() = PacketSpec.payloadType(header)
    val payloadVersion: Int get() = PacketSpec.payloadVersion(header)
    val isFlood: Boolean get() = PacketSpec.isFlood(header)
    val hasTransportCodes: Boolean get() = PacketSpec.hasTransportCodes(header)

    fun encodeInto(out: ByteArray = ByteArray(PacketSpec.MAX_PACKET)): Int =
        PacketCodec.encodeInto(this, out)

    override fun equals(other: Any?): Boolean =
        other is RawPacket && other.header == header &&
            (other.transportCodes?.contentEquals(transportCodes ?: IntArray(0)) == true ||
                transportCodes == null && other.transportCodes == null) &&
            other.path == path && other.payload.contentEquals(payload)

    override fun hashCode(): Int = header * 31 + path.hashCode()
}

/** Malformed input: the codec drops packets, it never throws mid-stream. */
class PacketDecodeException(message: String) : Exception(message)

object PacketCodec {

    /**
     * Decode raw on-air bytes. Returns null (never throws) for anything not
     * decodable: unknown payload version, truncated, oversized — corrupt
     * packets are dropped, never crash (09 §1).
     */
    fun decode(raw: ByteArray): RawPacket? = try {
        decodeOrNull(raw)
    } catch (_: PacketDecodeException) {
        null
    }

    private fun decodeOrNull(raw: ByteArray): RawPacket {
        if (raw.isEmpty()) throw PacketDecodeException("empty")
        val header = raw[0].toInt() and 0xFF
        if (PacketSpec.payloadVersion(header) != PacketSpec.PAYLOAD_VERSION_1) {
            throw PacketDecodeException("unsupported payload version")
        }
        var i = 1
        var transportCodes: IntArray? = null
        if (PacketSpec.hasTransportCodes(header)) {
            if (raw.size < i + 4) throw PacketDecodeException("truncated transport codes")
            transportCodes = intArrayOf(
                leU16(raw, i),
                leU16(raw, i + 2),
            )
            i += 4
        }
        if (raw.size <= i) throw PacketDecodeException("missing path length")
        val pathLengthByte = raw[i].toInt() and 0xFF
        i += 1

        val hashSize = PathHashSize.fromCode(pathLengthByte shr 6)
        val hopCount = pathLengthByte and 0x3F
        val pathBytes = hopCount * hashSize.bytes
        if (pathBytes > PacketSpec.MAX_PATH_BYTES) {
            throw PacketDecodeException("path exceeds 64 bytes")
        }
        if (raw.size < i + pathBytes) throw PacketDecodeException("truncated path")
        val hashes = ArrayList<ByteArray>(hopCount)
        for (h in 0 until hopCount) {
            hashes.add(raw.copyOfRange(i, i + hashSize.bytes))
            i += hashSize.bytes
        }

        val payload = if (raw.size > i) {
            if (raw.size - i > PacketSpec.MAX_PAYLOAD) {
                throw PacketDecodeException("payload exceeds 184 bytes")
            }
            raw.copyOfRange(i, raw.size)
        } else {
            ByteArray(0)
        }

        return RawPacket(header, transportCodes, Path(hashSize, hashes), payload)
    }

    /** Encode into `out`; returns the number of bytes written. */
    fun encodeInto(packet: RawPacket, out: ByteArray): Int {
        var i = 0
        out[i++] = packet.header.toByte()
        packet.transportCodes?.let {
            require(it.size == 2) { "transport codes must be 2 u16s" }
            out[i++] = (it[0] and 0xFF).toByte()
            out[i++] = ((it[0] shr 8) and 0xFF).toByte()
            out[i++] = (it[1] and 0xFF).toByte()
            out[i++] = ((it[1] shr 8) and 0xFF).toByte()
        }
        val hashSize = packet.path.hashSize.bytes
        val hopCount = packet.path.hopCount
        require(hopCount <= 63) { "hop count must fit in 6 bits" }
        val pathBytes = hopCount * hashSize
        require(pathBytes <= PacketSpec.MAX_PATH_BYTES) { "path exceeds 64 bytes" }
        out[i++] = (((hashSize - 1) shl 6) or hopCount).toByte()
        for (h in packet.path.hashes) {
            require(h.size == hashSize) { "hash size mismatch within path" }
            h.copyInto(out, i)
            i += hashSize
        }
        require(packet.payload.size <= PacketSpec.MAX_PAYLOAD) { "payload exceeds 184 bytes" }
        packet.payload.copyInto(out, i)
        i += packet.payload.size
        return i
    }

    /** Encode a parsed packet to fresh bytes. */
    fun encode(packet: RawPacket): ByteArray {
        val out = ByteArray(PacketSpec.MAX_PACKET)
        return out.copyOf(encodeInto(packet, out))
    }

    /** Encode a fresh packet with no transport codes. */
    fun encode(
        routeType: Int,
        payloadType: Int,
        path: Path,
        payload: ByteArray,
        payloadVersion: Int = PacketSpec.PAYLOAD_VERSION_1,
    ): ByteArray {
        val p = RawPacket(
            PacketSpec.header(routeType, payloadType, payloadVersion),
            null,
            path,
            payload,
        )
        val out = ByteArray(PacketSpec.MAX_PACKET)
        return out.copyOf(encodeInto(p, out))
    }

    internal fun leU16(raw: ByteArray, off: Int): Int =
        (raw[off].toInt() and 0xFF) or ((raw[off + 1].toInt() and 0xFF) shl 8)

    /**
     * The MeshHop/MeshCore dedup tag: a content-derived 4-byte identifier
     * computed by the mesh firmware over the packet. The app only needs a
     * stable per-packet identity — we use SHA-256 over the raw bytes.
     */
    fun packetTag(raw: ByteArray): ByteArray =
        Crypto.sha256(raw).copyOf(4)
}
