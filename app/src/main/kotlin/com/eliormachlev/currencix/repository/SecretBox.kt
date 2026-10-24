package com.eliormachlev.currencix.repository

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import timber.log.Timber
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Seals a short secret (an API key) for storage, and opens it again. */
interface SecretBox {
    /** [plain], sealed into a string that's safe to store. */
    fun seal(plain: String): String

    /** What [seal] made of a secret, opened; null when it can't be (a key the system no longer has). */
    fun open(sealed: String): String?
}

private const val KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "currencix_secrets"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val KEY_BITS = 256
private const val TAG_BITS = 128
private const val IV_BYTES = 12

// What a sealed value starts with: the format's name, so a later one can be told apart.
private const val SEALED_PREFIX = "ks1:"

/**
 * AES-256-GCM under a key made in the Android Keystore, which never leaves
 * it (in the phone's secure hardware where it has some): what's stored is
 * useless off this phone, or from a copy of the app's files. Stored as
 * `ks1:` + Base64 of the IV and the ciphertext.
 */
object KeystoreSecretBox : SecretBox {
    private const val TAG = "SecretBox"

    override fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return SEALED_PREFIX + Base64.getEncoder().encodeToString(sealed)
    }

    override fun open(sealed: String): String? =
        try {
            val bytes = Base64.getDecoder().decode(sealed.removePrefix(SEALED_PREFIX))
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            // The key is gone (the phone's lock-screen keys were reset, the app's
            // data moved to another phone): the user enters the API key again.
            Timber.tag(TAG).w(e, "A stored secret couldn't be opened")
            null
        } catch (e: IllegalArgumentException) {
            Timber.tag(TAG).w(e, "A stored secret isn't valid Base64")
            null
        }

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val spec =
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }
}
