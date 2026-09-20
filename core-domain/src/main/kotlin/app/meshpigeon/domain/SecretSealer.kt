package app.meshpigeon.domain

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals key material at rest (06-android-app §2: `privkey_enc` / `key_enc`
 * are sealed with the Android Keystore, StrongBox where available). Room
 * repositories seal on write and unseal on read; domain entities always
 * hold usable key bytes.
 *
 * Sealed wire format: `[magic "MPG"][version:1=0x01][iv:12][ciphertext ‖ GCM tag]`.
 * `unseal` passes bytes without the magic through unchanged — installs from
 * before sealing stored raw keys, and they migrate to sealed form the next
 * time the row is written. Tampered ciphertexts throw (AEADBadTagException)
 * rather than degrading silently.
 */
interface SecretSealer {
    fun seal(plaintext: ByteArray): ByteArray
    fun unseal(sealed: ByteArray): ByteArray
}

/**
 * AES-256-GCM sealing over a caller-supplied key. :core-data binds the key
 * to the Android Keystore; plain JVM keys back the tests.
 */
open class AesGcmSecretSealer(keyProvider: () -> SecretKey) : SecretSealer {
    private val key: SecretKey by lazy(keyProvider)
    private val random = SecureRandom()

    @Synchronized
    override fun seal(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(IV_LEN).also(random::nextBytes)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val sealed = cipher.doFinal(plaintext)
        return MAGIC + iv + sealed
    }

    @Synchronized
    override fun unseal(sealed: ByteArray): ByteArray {
        if (!hasMagic(sealed)) return sealed
        val ivStart = MAGIC.size
        val iv = sealed.copyOfRange(ivStart, ivStart + IV_LEN)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(sealed, ivStart + IV_LEN, sealed.size - ivStart - IV_LEN)
    }

    companion object {
        /** "MPG" + version — long enough that raw keys never collide with it. */
        val MAGIC = byteArrayOf('M'.code.toByte(), 'P'.code.toByte(), 'G'.code.toByte(), 0x01)

        fun hasMagic(bytes: ByteArray): Boolean =
            bytes.size > MAGIC.size && bytes.copyInto(ByteArray(MAGIC.size), 0, 0, MAGIC.size).contentEquals(MAGIC)

        const val IV_LEN = 12
        const val TAG_BITS = 128
    }
}
