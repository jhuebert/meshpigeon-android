package app.meshpigeon.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import app.meshpigeon.domain.AesGcmSecretSealer
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Seals key material with an AES-256-GCM key generated inside the Android
 * Keystore (StrongBox where the device offers it, 06-android-app §2).
 * The key never leaves secure hardware; only seal/unseal run app-side.
 */
class KeystoreSecretSealer(context: Context) : AesGcmSecretSealer({ getOrCreateKey(context) }) {
    companion object {
        private const val ALIAS = "meshpigeon_master"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"

        private fun getOrCreateKey(context: Context): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }

            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE,
            )
            if (Build.VERSION.SDK_INT >= 28 &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            ) {
                try {
                    generator.init(strongBoxSpec())
                    return generator.generateKey()
                } catch (_: ProviderException) {
                    // StrongBox refused the key — fall back to the TEE.
                }
            }
            generator.init(plainSpec())
            return generator.generateKey()
        }

        @RequiresApi(28)
        private fun strongBoxSpec() = KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setIsStrongBoxBacked(true)
            .build()

        private fun plainSpec() = KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
    }
}
