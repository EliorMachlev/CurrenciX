package com.eliormachlev.currencix.view.lock

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.PrivacySettings
import timber.log.Timber

// Away for less than this (the camera for a receipt, the file picker for a
// backup, a glance at another app), the app opens without asking again.
private const val GRACE_MILLIS = 60_000L

private const val TAG = "AppLock"

// The phone's strong biometrics, or its screen lock (PIN, pattern or
// password) — whichever the person prefers at the prompt.
private const val AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

/**
 * Settings → Security & privacy → App lock, for the whole process: locked
 * when the app starts, and again when it comes back after more than a
 * minute away, until the person proves they can unlock the phone. Only with
 * a screen lock on the phone ([isAvailable]): without one there would be
 * nothing to unlock with, so nothing locks.
 */
object AppLock : DefaultLifecycleObserver {
    /** Whether the app's screens are covered by the lock right now. */
    var locked by mutableStateOf(false)
        private set

    // Whether the setting is on and the phone can unlock; set by install.
    private var shouldLock: () -> Boolean = { false }
    private var leftAt: Long? = null

    /** Starts following the app coming and going; locked at once when the setting is on. */
    fun install(
        context: Context,
        settings: PrivacySettings,
    ) {
        val app = context.applicationContext
        shouldLock = { settings.appLock.get() && isAvailable(app) }
        locked = shouldLock()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        leftAt = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        val left = leftAt ?: return
        leftAt = null
        if (SystemClock.elapsedRealtime() - left >= GRACE_MILLIS && shouldLock()) locked = true
    }

    /** Asks for biometrics or the screen lock; unlocks on success. */
    fun unlock(context: Context) = authenticate(context) { locked = false }

    /** Whether the phone has a screen lock (or strong biometrics) to unlock with. */
    fun isAvailable(context: Context): Boolean =
        context.getSystemService(BiometricManager::class.java)?.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * The system prompt for biometrics or the screen lock, titled for this
     * app; [onSuccess] once the person is through, as the Keystore attests
     * ([UnlockKey]). Cancelling or failing leaves things as they were, and
     * so does a phone whose Keystore can't make the key: turning the lock on
     * goes through here, so it can't be turned on there.
     */
    fun authenticate(
        context: Context,
        onSuccess: () -> Unit,
    ) {
        val cipher = runCatching(UnlockKey::cipher).onFailure { Timber.tag(TAG).w(it, "No unlock key") }.getOrNull() ?: return
        val prompt =
            BiometricPrompt
                .Builder(context)
                .setTitle(context.getString(R.string.app_lock_prompt_title))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
        prompt.authenticate(
            BiometricPrompt.CryptoObject(cipher),
            CancellationSignal(),
            context.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (UnlockKey.proves(result.cryptoObject?.cipher)) onSuccess()
                }
            },
        )
    }
}
