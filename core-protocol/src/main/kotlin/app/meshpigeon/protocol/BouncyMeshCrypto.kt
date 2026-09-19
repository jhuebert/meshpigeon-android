package app.meshpigeon.protocol

import java.math.BigInteger
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.math.ec.rfc7748.X25519
import org.bouncycastle.math.ec.rfc8032.Ed25519

/**
 * BouncyCastle-backed [MeshCrypto] matching MeshCore's schemes byte-for-byte
 * (see [Crypto] docs and MeshCore `lib/ed25519/key_exchange.c`,
 * `src/Utils.cpp encryptThenMAC`).
 */
class BouncyMeshCrypto : MeshCrypto {

    override fun newIdentity(): IdentityKeyPair {
        val seed = Crypto.random(32)
        val public = ByteArray(Ed25519.PUBLIC_KEY_SIZE)
        Ed25519.generatePublicKey(seed, 0, public, 0)
        // We store seed‖pub internally (BC signs with the seed). The
        // MeshCore-compatible 64-byte prv (clamp(sha512(seed))‖hash-tail) is
        // derived on demand — see meshCorePrivateKey().
        return IdentityKeyPair(public, seed + public)
    }

    /**
     * MeshCore's 64-byte prv_key layout (orlp ed25519_create_keypair):
     * [0..32) = SHA-512(seed) with (h[0]&=248, h[31]&=63, h[31]|=64),
     * [32..64) = raw SHA-512(seed) tail. ECDH scalar = clamped first half.
     */
    fun meshCorePrivateKey(seed: ByteArray): ByteArray {
        require(seed.size == 32)
        val hash = Crypto.sha512(seed)
        hash[0] = (hash[0].toInt() and 248).toByte()
        hash[31] = (hash[31].toInt() and 63).toByte()
        hash[31] = (hash[31].toInt() or 64).toByte()
        return hash
    }

    override fun sign(privateKey: ByteArray, vararg chunks: ByteArray): ByteArray {
        val size = chunks.sumOf { it.size }
        val msg = ByteArray(size)
        var i = 0
        for (c in chunks) {
            c.copyInto(msg, i)
            i += c.size
        }
        val sig = ByteArray(Ed25519.SIGNATURE_SIZE)
        // BC takes the 32-byte seed (first half of the MeshCore 64-byte prv)
        Ed25519.sign(privateKey, 0, msg, 0, msg.size, sig, 0)
        return sig
    }

    override fun verify(publicKey: ByteArray, signature: ByteArray, vararg chunks: ByteArray): Boolean {
        val size = chunks.sumOf { it.size }
        val msg = ByteArray(size)
        var i = 0
        for (c in chunks) {
            c.copyInto(msg, i)
            i += c.size
        }
        // BC 1.86 order: (sig, sigOff, pk, pkOff, m, mOff, mLen)
        return Ed25519.verify(signature, 0, publicKey, 0, msg, 0, msg.size)
    }

    /**
     * MeshCore ed25519_key_exchange: clamp the Ed25519 private key, convert
     * the peer's Ed25519 public key to its Montgomery u-coordinate
     * (u = (1 + y)/(1 − y) mod p), then run the X25519 ladder.
     */
    override fun sharedSecret(myPrivateKey: ByteArray, theirPublicKey: ByteArray): ByteArray {
        require(theirPublicKey.size == 32) { "public key must be 32 bytes" }
        val scalar = ecdhScalar(myPrivateKey)
        val u = edToMontgomery(theirPublicKey)
        val shared = ByteArray(32)
        X25519.calculateAgreement(scalar, 0, u, 0, shared, 0)
        return shared
    }

    /**
     * The ECDH scalar for an identity. Accepts either our internal
     * seed‖pub layout or a MeshCore 64-byte prv — both resolve to
     * clamp(sha512(seed)[0..32)), matching MeshCore's ed25519_key_exchange.
     */
    fun ecdhScalar(privateKey: ByteArray): ByteArray = when (privateKey.size) {
        64 -> meshCorePrivateKey(privateKey.copyOf(32)) // internal: [0..32) = seed
        32 -> meshCorePrivateKey(privateKey)
        else -> throw IllegalArgumentException("private key must be 32 or 64 bytes")
    }

    override fun encryptThenMac(secret: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Aes128Ecb(secret)
        val ct = cipher.encrypt(plaintext)
        val macKey = macKey(secret)
        val mac = Crypto.hmacSha256(macKey, ct)
        return byteArrayOf(mac[0], mac[1]) + ct
    }

    override fun macThenDecrypt(secret: ByteArray, framed: ByteArray): ByteArray? {
        if (framed.size <= MAC_SIZE) return null
        val macKey = macKey(secret)
        val mac = Crypto.hmacSha256(macKey, framed.copyOfRange(2, framed.size))
        if (mac[0] != framed[0] || mac[1] != framed[1]) return null
        val cipher = Aes128Ecb(secret)
        return cipher.decrypt(framed, 2)
    }

    companion object {
        const val MAC_SIZE = 2

        /**
         * HMAC key = the full 32-byte secret buffer. Channel keys are 16
         * bytes, zero-padded to 32 — matching MeshCore's fixed 32-byte
         * GroupChannel.secret buffer.
         */
        fun macKey(secret: ByteArray): ByteArray {
            val key = secret.copyOf(32)
            for (i in secret.size until 32) key[i] = 0
            return key
        }

        /**
         * Ed25519 public key → Curve25519 u-coordinate (little-endian,
         * 32 bytes): u = (1 + y) · (1 − y)⁻¹ mod p. Bit 255 of the Ed25519
         * key is the sign bit of x and must be masked, not reduced.
         */
        fun edToMontgomery(edPublicKey: ByteArray): ByteArray {
            val masked = edPublicKey.copyOf(32).also { it[31] = (it[31].toInt() and 0x7F).toByte() }
            var y = BigInteger(1, masked.reversedArray())
            val p = BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffed", 16)
            if (y >= p) y = y.mod(p)
            val one = BigInteger.ONE
            val u = y.add(one).multiply(one.subtract(y).modInverse(p)).mod(p)
            val raw = u.toByteArray() // big-endian, may have a leading zero
            val le = ByteArray(32)
            for (i in raw.indices) {
                val pos = raw.size - 1 - i
                if (pos < 32) le[i] = raw[pos]
            }
            return le
        }
    }
}

/** AES-128-ECB with zero-padded final block — MeshCore's exact scheme. */
class Aes128Ecb(secret: ByteArray) {
    private val key = secret.copyOf(16)

    fun encrypt(plaintext: ByteArray): ByteArray {
        val engine = AESEngine()
        engine.init(true, KeyParameter(key))
        val blocks = (plaintext.size + 15) / 16
        val out = ByteArray(blocks * 16)
        for (b in 0 until blocks) {
            val block = ByteArray(16)
            val len = minOf(16, plaintext.size - b * 16)
            plaintext.copyInto(block, 0, b * 16, b * 16 + len)
            engine.processBlock(block, 0, out, b * 16)
        }
        return out
    }

    /**
     * Returns the decrypted bytes including the sender's zero padding —
     * MeshCore plaintexts are NUL-terminated (text) or length-prefixed
     * (GRP_DATA), and the NUL/len marks the real end. Stripping zeros here
     * would eat the terminator (and any binary data ending in 0x00).
     */
    fun decrypt(framed: ByteArray, offset: Int): ByteArray {
        val ctLen = framed.size - offset
        require(ctLen % 16 == 0) { "ciphertext not block-aligned" }
        val engine = AESEngine()
        engine.init(false, KeyParameter(key))
        val out = ByteArray(ctLen)
        for (b in 0 until ctLen / 16) {
            engine.processBlock(framed, offset + b * 16, out, b * 16)
        }
        return out
    }
}
