package com.eliormachlev.currencix.view.lock

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

private const val KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "app_lock"
private const val KEY_BITS = 256
private const val TRANSFORMATION = "AES/GCM/NoPadding"

// What an unlock encrypts: anything will do, only being able to is the proof.
private val CHALLENGE = "CurrenciX unlock".toByteArray()

/**
 * The key an unlock has to use (Keystore, AES): the phone's secure hardware
 * lets it encrypt once, right after the person passes the prompt with strong
 * biometrics or the screen lock, and never otherwise. So the app opens only
 * when that encryption works, not merely when the prompt reports success,
 * which code injected into the app on a rooted phone could fake.
 */
internal object UnlockKey {
    /** A cipher for the prompt to authorize; a key spoiled by new biometrics is made again. */
    fun cipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key())
        } catch (_: KeyPermanentlyInvalidatedException) {
            keyStore().deleteEntry(KEY_ALIAS)
            cipher.init(Cipher.ENCRYPT_MODE, key())
        }
        return cipher
    }

    /** Whether [cipher], from a passed prompt, can encrypt: the hardware's word that the person unlocked. */
    fun proves(cipher: Cipher?): Boolean = cipher != null && runCatching { cipher.doFinal(CHALLENGE) }.isSuccess

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun key(): SecretKey = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: generate()

    private fun generate(): SecretKey =
        KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_BITS)
                        .setUserAuthenticationRequired(true)
                        // 0: for one use each time, never for a while after.
                        .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                        .build(),
                )
            }.generateKey()
}
