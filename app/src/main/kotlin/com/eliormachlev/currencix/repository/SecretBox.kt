package com.eliormachlev.currencix.repository

import com.google.crypto.tink.Aead
import com.google.crypto.tink.integration.android.AndroidKeystore
import timber.log.Timber
import java.security.GeneralSecurityException
import java.util.Base64

/** Seals a short secret (an API key) for storage, and opens it again. */
interface SecretBox {
    /** [plain], sealed into a string that's safe to store. */
    fun seal(plain: String): String

    /** What [seal] made of a secret, opened; null when it can't be (a key the system no longer has). */
    fun open(sealed: String): String?
}

private const val KEY_ALIAS = "currencix_secrets"

// What a sealed value starts with: the format's name, so a later one can be told apart.
private const val SEALED_PREFIX = "ks1:"

// Bound into every seal as associated data, so a value sealed for something
// else under the same key doesn't open as one of these.
private val ASSOCIATED_DATA = SEALED_PREFIX.toByteArray(Charsets.UTF_8)

/**
 * AES-256-GCM under a key made in the Android Keystore, which never leaves
 * it (in the phone's secure hardware where it has some): what's stored is
 * useless off this phone, or from a copy of the app's files. Tink's
 * Keystore AEAD runs the cipher and owns the nonce (a fresh random one per
 * seal, carried in its output), so no IV is chosen or reused here. Stored
 * as `ks1:` + Base64 of that output.
 */
object KeystoreSecretBox : SecretBox {
    private const val TAG = "SecretBox"

    // Made on first use and kept: the Keystore key outlives the process.
    private val aead: Aead by lazy {
        if (!AndroidKeystore.hasKey(KEY_ALIAS)) AndroidKeystore.generateNewAes256GcmKey(KEY_ALIAS)
        AndroidKeystore.getAead(KEY_ALIAS)
    }

    override fun seal(plain: String): String {
        val sealed = aead.encrypt(plain.toByteArray(Charsets.UTF_8), ASSOCIATED_DATA)
        return SEALED_PREFIX + Base64.getEncoder().encodeToString(sealed)
    }

    override fun open(sealed: String): String? =
        try {
            val bytes = Base64.getDecoder().decode(sealed.removePrefix(SEALED_PREFIX))
            String(aead.decrypt(bytes, ASSOCIATED_DATA), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            // The key is gone (the phone's lock-screen keys were reset, the app's
            // data moved to another phone): the user enters the API key again.
            Timber.tag(TAG).w(e, "A stored secret couldn't be opened")
            null
        } catch (e: IllegalArgumentException) {
            Timber.tag(TAG).w(e, "A stored secret isn't valid Base64")
            null
        }
}
