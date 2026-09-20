package app.meshpigeon.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.AEADBadTagException

/**
 * Sealing contract for at-rest key material (06-android-app §2). The
 * Android Keystore supplies the key in production (:core-data); the tests
 * use a plain JVM AES key — the cipher path and wire format are shared.
 */
class SecretSealerTest {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val sealer = AesGcmSecretSealer { key }

    @Test
    fun `round-trip preserves the secret`() {
        val secret = ByteArray(64) { it.toByte() } // MeshCore identity layout: seed + pub
        assertArrayEquals(secret, sealer.unseal(sealer.seal(secret)))
    }

    @Test
    fun `sealing is randomized per call`() {
        val secret = "channel key".toByteArray()
        val a = sealer.seal(secret)
        val b = sealer.seal(secret)
        assertNotEquals(a.toList(), b.toList()) // fresh IV each seal
        assertArrayEquals(secret, sealer.unseal(a))
        assertArrayEquals(secret, sealer.unseal(b))
    }

    @Test
    fun `sealed bytes carry the magic prefix`() {
        val sealed = sealer.seal(ByteArray(16))
        assertTrue(AesGcmSecretSealer.hasMagic(sealed))
        assertTrue(sealed.size > AesGcmSecretSealer.MAGIC.size + AesGcmSecretSealer.IV_LEN + AesGcmSecretSealer.TAG_BITS / 8)
    }

    @Test
    fun `unseal passes raw (pre-sealing) bytes through unchanged`() {
        val legacy = ByteArray(64) { (it + 7).toByte() }
        assertFalse(AesGcmSecretSealer.hasMagic(legacy))
        assertArrayEquals(legacy, sealer.unseal(legacy))
    }

    @Test
    fun `tampered ciphertext fails loudly`() {
        val sealed = sealer.seal(ByteArray(64))
        sealed[sealed.size - 1] = (sealed.last() + 1).toByte()
        assertThrows(AEADBadTagException::class.java) { sealer.unseal(sealed) }
    }
}
