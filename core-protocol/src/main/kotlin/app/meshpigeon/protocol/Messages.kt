package app.meshpigeon.protocol

/**
 * End-to-end message construction (03 §2–§4). These builders produce the
 * on-air packet bytes for direct messages and group messages, and decode
 * incoming ones. They know nothing about storage or UI.
 */
object Messages {

    /**
     * Build a flood-routed DM (TXT_MSG) to a known contact.
     *
     * Payload: `[ts:4][attempt&3|txt_type<<2:1][text\0]`, encrypted with the
     * X25519 shared secret in the direct format
     * `[dest_hash:1][src_hash:1][MAC:2][ciphertext]`.
     *
     * Returns the raw packet bytes and the expected ACK checksum.
     */
    fun buildDirectMessage(
        crypto: MeshCrypto,
        myIdentity: IdentityKeyPair,
        destPubKey: ByteArray,
        timestamp: Long,
        text: String,
        attempt: Int = 0,
        txtType: Int = PacketSpec.TXT_TYPE_PLAIN,
    ): DirectMessage {
        val plaintext = TxtMsgPayload(timestamp, txtType, attempt, text).encode()
        val expectedAck = Ack.compute(plaintext, myIdentity.publicKey)

        val secret = crypto.sharedSecret(myIdentity.privateKey, destPubKey)
        val framed = crypto.encryptThenMac(secret, plaintext)

        val payload = byteArrayOf(
            destPubKey[0], // dest hash
            myIdentity.publicKey[0], // src hash
        ) + framed

        val destHash = listOf(destPubKey.copyOf(3))
        val raw = PacketCodec.encode(
            PacketSpec.ROUTE_FLOOD,
            PacketSpec.PAYLOAD_TXT_MSG,
            Path(PathHashSize.THREE, emptyList()), // flood: path tracks hops
            payload,
        )
        return DirectMessage(raw, expectedAck, destHash)
    }

    /**
     * Build a direct-routed DM using a learned path to the destination.
     * `route` holds the per-hop hash prefixes toward the destination as
     * learned from adverts/ACK paths/PATH replies (03 §4).
     */
    fun buildDirectMessageRouted(
        crypto: MeshCrypto,
        myIdentity: IdentityKeyPair,
        destPubKey: ByteArray,
        route: Path,
        timestamp: Long,
        text: String,
        attempt: Int = 0,
    ): ByteArray {
        val flood = buildDirectMessage(crypto, myIdentity, destPubKey, timestamp, text, attempt)
        return reRouteToDirect(flood.raw, route)
    }

    /** Build an encrypted channel text message (GRP_TXT, flood). */
    fun buildGroupMessage(
        channel: Channels.Channel,
        timestamp: Long,
        senderAndText: String,
    ): ByteArray {
        val plain = Crypto.leU32(timestamp) + byteArrayOf(0) +
            senderAndText.toByteArray(Charsets.UTF_8)
        val framed = channel.encryptThenMac(BouncyMeshCrypto(), plain)
        val payload = byteArrayOf(channel.hash.toByte()) + framed
        return PacketCodec.encode(
            PacketSpec.ROUTE_FLOOD,
            PacketSpec.PAYLOAD_GRP_TXT,
            Path(PathHashSize.THREE, emptyList()),
            payload,
        )
    }

    /** Build a GRP_DATA sub-message (reactions, receipts, typing…). */
    fun buildGroupData(
        channel: Channels.Channel,
        dataType: Int,
        data: ByteArray,
    ): ByteArray {
        val framed = channel.encryptThenMac(
            BouncyMeshCrypto(),
            GroupDataPayload.encodePlaintext(dataType, data),
        )
        val payload = byteArrayOf(channel.hash.toByte()) + framed
        return PacketCodec.encode(
            PacketSpec.ROUTE_FLOOD,
            PacketSpec.PAYLOAD_GRP_DATA,
            Path(PathHashSize.THREE, emptyList()),
            payload,
        )
    }

    /** Build an ACK packet answering a received direct TXT_MSG. */
    fun buildAck(checksum: ByteArray): ByteArray =
        PacketCodec.encode(
            PacketSpec.ROUTE_FLOOD,
            PacketSpec.PAYLOAD_ACK,
            Path(PathHashSize.THREE, emptyList()),
            checksum,
        )

    /** Build a flood advert (share-my-contact). */
    fun buildAdvert(
        crypto: MeshCrypto,
        identity: IdentityKeyPair,
        timestamp: Long,
        appData: AdvertAppData,
    ): ByteArray {
        val appBytes = appData.encode()
        val signature = crypto.sign(identity.privateKey, identity.publicKey, Crypto.leU32(timestamp), appBytes)
        val payload = identity.publicKey + Crypto.leU32(timestamp) + signature + appBytes
        require(payload.size <= PacketSpec.MAX_PAYLOAD) { "advert payload too long" }
        return PacketCodec.encode(
            PacketSpec.ROUTE_FLOOD,
            PacketSpec.PAYLOAD_ADVERT,
            Path(PathHashSize.THREE, emptyList()),
            payload,
        )
    }

    /**
     * Build a zero-hop advert (07 §5 "Say hi nearby"): same ADVERT payload
     * but routed DIRECT with an empty path — a single broadcast the mesh
     * does not relay (MeshCore's sendZeroHop).
     */
    fun buildZeroHopAdvert(
        crypto: MeshCrypto,
        identity: IdentityKeyPair,
        timestamp: Long,
        appData: AdvertAppData,
    ): ByteArray {
        val raw = buildAdvert(crypto, identity, timestamp, appData)
        // route type lives in header bits 0–1; keep payload type + version
        raw[0] = ((raw[0].toInt() and 0b11111100) or PacketSpec.ROUTE_DIRECT).toByte()
        return raw
    }

    /**
     * Convert a flood-routed packet to a direct-routed one, preserving
     * transport codes if present and setting the route's path.
     */
    fun reRouteToDirect(raw: ByteArray, route: Path): ByteArray {
        val packet = PacketCodec.decode(raw)
            ?: throw IllegalArgumentException("cannot decode packet to re-route")
        val header = (packet.header and 0b11111100) or PacketSpec.ROUTE_DIRECT
        val routed = RawPacket(header, packet.transportCodes, route, packet.payload)
        return PacketCodec.encode(routed)
    }

    /**
     * Open an incoming direct TXT_MSG addressed to us. Receivers try the
     * shared secret of each known contact whose 1-byte node hash matches the
     * packet's src hash (MeshCore DM scheme — the packet carries no sender
     * pubkey). Returns null when it isn't for us / no key opens it.
     */
    fun decodeDirectMessage(
        crypto: MeshCrypto,
        myIdentity: IdentityKeyPair,
        packet: RawPacket,
        candidatePubKeys: List<ByteArray>,
    ): DecodedDirectMessage? {
        if (packet.payloadType != PacketSpec.PAYLOAD_TXT_MSG || packet.payload.size < 2 + 2 + 16) {
            return null
        }
        val destHash = packet.payload[0].toInt() and 0xFF
        if (destHash != myIdentity.nodeHash) return null // not addressed to us
        val srcHash = packet.payload[1].toInt() and 0xFF
        for (pub in candidatePubKeys) {
            if ((pub[0].toInt() and 0xFF) != srcHash) continue
            val secret = crypto.sharedSecret(myIdentity.privateKey, pub)
            val plain = crypto.macThenDecrypt(secret, packet.payload.copyOfRange(2, packet.payload.size))
                ?: continue
            val msg = TxtMsgPayload.decode(plain)
            return DecodedDirectMessage(srcHash, msg.timestamp, msg.txtType, msg.attempt, msg.text)
        }
        return null
    }

    data class DirectMessage(
        val raw: ByteArray,
        val expectedAck: ByteArray,
        /** 3-byte prefix of the destination (path bookkeeping). */
        val destHash: List<ByteArray>,
    )

    data class DecodedDirectMessage(
        val srcHash: Int,
        val timestamp: Long,
        val txtType: Int,
        val attempt: Int,
        val text: String,
    )
}
