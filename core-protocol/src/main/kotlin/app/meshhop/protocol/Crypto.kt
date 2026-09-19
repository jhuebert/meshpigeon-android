package app.meshhop.protocol

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Small primitives shared by the codec and crypto implementations. */
object Crypto {
    fun sha256(vararg chunks: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        for (c in chunks) md.update(c)
        return md.digest()
    }

    fun sha512(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(data)

    fun hmacSha256(key: ByteArray, vararg chunks: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        for (c in chunks) mac.update(c)
        return mac.doFinal()
    }

    fun random(len: Int): ByteArray = ByteArray(len).also { SecureRandom().nextBytes(it) }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun unhex(s: String): ByteArray {
        val clean = s.replace(" ", "").replace("-", "").replace(":", "")
        require(clean.length % 2 == 0) { "odd-length hex" }
        return ByteArray(clean.length / 2) { i ->
            ((Character.digit(clean[i * 2], 16) shl 4) or Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
    }

    fun leU32(v: Long): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte(),
    )

    fun leU32At(b: ByteArray, off: Int): Long =
        (b[off].toLong() and 0xFF) or
            ((b[off + 1].toLong() and 0xFF) shl 8) or
            ((b[off + 2].toLong() and 0xFF) shl 16) or
            ((b[off + 3].toLong() and 0xFF) shl 24)
}

/**
 * MeshCore-compatible crypto surface (03-mesh-protocol §2):
 *
 * - Identity = Ed25519 keypair. Keys live only in the app (keystore);
 *   never on the radio.
 * - Shared secret (DMs): X25519-style ECDH with the Ed25519 keys converted
 *   to Montgomery form, exactly as MeshCore's `ed25519_key_exchange`:
 *   scalar = clamped Ed25519 private key, u = (1 + y)/(1 − y) mod p from the
 *   peer's Ed25519 public key.
 * - Symmetric layer: AES-128-ECB (zero-padded final block) with the first
 *   16 bytes of the secret as the key, MAC = first 2 bytes of
 *   HMAC-SHA256(secret) over the ciphertext. Wire layout: [MAC:2][ciphertext].
 * - Channels: 16-byte shared keys; the 2-byte MAC key is the 16-byte key
 *   zero-padded to 32 (matching MeshCore's 32-byte channel secret buffer).
 */
interface MeshCrypto {
    /** Ed25519 keypair: 32-byte public key, 64-byte private key (seed+pub). */
    fun newIdentity(): IdentityKeyPair

    fun sign(privateKey: ByteArray, vararg chunks: ByteArray): ByteArray

    fun verify(publicKey: ByteArray, signature: ByteArray, vararg chunks: ByteArray): Boolean

    /** 32-byte ECDH shared secret between my Ed25519 private and their public. */
    fun sharedSecret(myPrivateKey: ByteArray, theirPublicKey: ByteArray): ByteArray

    /** MeshCore encryptThenMAC: [MAC:2][AES-128-ECB(secret[0..16], zero-padded)]. */
    fun encryptThenMac(secret: ByteArray, plaintext: ByteArray): ByteArray

    /** Inverse of [encryptThenMac]; null when the MAC check fails. */
    fun macThenDecrypt(secret: ByteArray, framed: ByteArray): ByteArray?
}

data class IdentityKeyPair(val publicKey: ByteArray, val privateKey: ByteArray) {
    /** First byte of the public key — the node hash used on-air. */
    val nodeHash: Int get() = publicKey[0].toInt() and 0xFF
}
