package app.meshhop.protocol

/**
 * Channel keys, MeshCore-compatible (companion_protocol.md §Channel Types):
 *
 * - **Public**: the well-known 16-byte key `8b3387e9c5cdea6a c9e5edbaa115cd72`.
 * - **Hashtag ("shared")**: key = first 16 bytes of SHA-256("#name") —
 *   anyone who knows the name can derive it; not private.
 * - **Private**: random 16 bytes, shared out-of-band via QR/link.
 *
 * The on-air channel hash is the first byte of SHA-256(secret).
 */
object Channels {
    const val PUBLIC_KEY_HEX = "8b3387e9c5cdea6ac9e5edbaa115cd72"
    const val PUBLIC_NAME = "Public"

    val publicKey: ByteArray = Crypto.unhex(PUBLIC_KEY_HEX)

    fun channelHash(secret: ByteArray): Int = Crypto.sha256(secret)[0].toInt() and 0xFF

    /** Hashtag ("shared") channel: name-derived key. */
    fun hashtagKey(name: String): ByteArray {
        val clean = name.trim().removePrefix("#")
        return Crypto.sha256("#$clean".toByteArray(Charsets.UTF_8)).copyOf(16)
    }

    fun privateKey(): ByteArray = Crypto.random(16)

    data class Channel(
        val name: String,
        val secret: ByteArray, // 16 bytes
    ) {
        val hash: Int get() = channelHash(secret)

        fun encryptThenMac(crypto: MeshCrypto, plaintext: ByteArray): ByteArray =
            crypto.encryptThenMac(macKeyBuffer(secret), plaintext)

        fun macThenDecrypt(crypto: MeshCrypto, framed: ByteArray): ByteArray? =
            crypto.macThenDecrypt(macKeyBuffer(secret), framed)

        companion object {
            fun macKeyBuffer(secret: ByteArray): ByteArray =
                BouncyMeshCrypto.macKey(secret) // 16 bytes → zero-padded 32

            fun public(): Channel = Channel(PUBLIC_NAME, publicKey)
            fun hashtag(name: String): Channel = Channel(name, hashtagKey(name))
            fun private(name: String): Channel = Channel(name, privateKey())
        }
    }
}

/**
 * Message-level operations shared by send and receive paths: the MeshCore
 * expected-ACK computation and the DM send format.
 */
object Ack {
    /**
     * expected ACK = first 4 bytes of SHA-256(plaintext_without_nul ‖ sender_pubkey),
     * exactly as MeshCore's `composeMsgPacket` computes it.
     */
    fun compute(plaintext: ByteArray, senderPublicKey: ByteArray): ByteArray {
        var end = plaintext.size
        while (end > 0 && plaintext[end - 1] == 0.toByte()) end--
        return Crypto.sha256(plaintext.copyOf(end), senderPublicKey).copyOf(4)
    }
}
