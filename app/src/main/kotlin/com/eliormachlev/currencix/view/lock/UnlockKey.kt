package com.eliormachlev.currencix.view.lock

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

private const val KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "app_lock"
private const val MAC_ALGORITHM = "HmacSHA256"

// What an unlock signs: anything will do, only being able to is the proof.
private val CHALLENGE = "CurrenciX unlock".toByteArray()

/**
 * The key an unlock has to use (Keystore, HMAC-SHA256): the phone's secure
 * hardware lets it sign once, right after the person passes the prompt with
 * strong biometrics or the screen lock, and never otherwise. So the app
 * opens only when that signature works, not merely when the prompt reports
 * success, which code injected into the app on a rooted phone could fake. A
 * MAC rather than a cipher: nothing is kept secret, so there's no IV to get
 * right, only a keyed operation to prove.
 */
internal object UnlockKey {
    /** A MAC for the prompt to authorize; a key spoiled by new biometrics is made again. */
    fun mac(): Mac {
        val mac = Mac.getInstance(MAC_ALGORITHM)
        try {
            mac.init(key())
        } catch (_: KeyPermanentlyInvalidatedException) {
            keyStore().deleteEntry(KEY_ALIAS)
            mac.init(key())
        }
        return mac
    }

    /** Whether [mac], from a passed prompt, can sign: the hardware's word that the person unlocked. */
    fun proves(mac: Mac?): Boolean = mac != null && runCatching { mac.doFinal(CHALLENGE) }.isSuccess

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun key(): SecretKey = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: generate()

    private fun generate(): SecretKey =
        KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setUserAuthenticationRequired(true)
                        // 0: for one use each time, never for a while after.
                        .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
                        .build(),
                )
            }.generateKey()
}
