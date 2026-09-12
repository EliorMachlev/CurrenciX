package com.eliormachlev.currencix.viewmodel.preference

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.eliormachlev.currencix.repository.BackupManager
import com.eliormachlev.currencix.repository.BackupResult

/**
 * Minimum password length for encrypted export. A four-char password is
 * broken in seconds; PBKDF2 stretching buys time but not that much time.
 * Kept here (not in the composable) so the confirm-side validation and the
 * user-facing error string draw from the same constant.
 */
const val BACKUP_MIN_PASSWORD_LENGTH = 8

/**
 * Backup ViewModel — owns the dialog state machine, the pending export
 * password (survives the SAF round-trip), and the [BackupManager] handle.
 * Compose owns nothing that has to survive rotation; the fragment owns the
 * SAF launchers because ActivityResult must be registered in a lifecycle
 * owner. State is a plain [BackupDialog] sealed hierarchy so both surfaces
 * agree on shape without stringly-typed keys.
 */
class BackupViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val backupManager = BackupManager(app.applicationContext)

    var dialog by mutableStateOf<BackupDialog?>(null)
        private set

    private var pendingExportPassword: CharArray? = null

    fun openExportPasswordPrompt() {
        dialog = BackupDialog.ExportPassword
    }

    fun dismissDialog() {
        // Any password sitting in the dialog state gets zeroed on dismiss so
        // a cancelled prompt doesn't leave secrets in memory longer than it
        // takes to close the dialog.
        (dialog as? BackupDialog.ImportConfirm)?.password?.fill('\u0000')
        dialog = null
    }

    /**
     * Confirm the export password prompt: stash the (possibly null) password
     * for the launcher round-trip and clear the dialog so the SAF picker can
     * come up unobstructed. Returns true iff the caller should now fire the
     * export launcher; false means validation failed and the dialog is now
     * showing the error state (currently just short-password reject via a
     * toast — the caller supplies the string).
     */
    fun stashExportPasswordAndDismiss(password: CharArray?) {
        pendingExportPassword?.fill('\u0000')
        pendingExportPassword = password
        dialog = null
    }

    fun consumePendingExportPassword(): CharArray? {
        val out = pendingExportPassword
        pendingExportPassword = null
        return out
    }

    fun runExport(uri: Uri): BackupResult {
        val password = consumePendingExportPassword()
        return backupManager.export(uri, password)
    }

    /**
     * Kick off the import state machine for a freshly-picked [uri]. Encrypted
     * files bounce into a password prompt; plaintext files jump straight to
     * the confirm dialog.
     */
    fun beginImport(uri: Uri) {
        dialog =
            if (backupManager.isEncrypted(uri)) {
                BackupDialog.ImportPassword(uri = uri, isRetry = false)
            } else {
                BackupDialog.ImportConfirm(uri = uri, password = null)
            }
    }

    fun confirmImportPassword(
        uri: Uri,
        password: CharArray,
    ) {
        dialog = BackupDialog.ImportConfirm(uri = uri, password = password)
    }

    fun runImport(
        uri: Uri,
        password: CharArray?,
    ): BackupResult = backupManager.import(uri, password)

    /**
     * Re-open the password prompt in retry state after an import result of
     * [BackupResult.WrongPassword]. Kept here so the fragment doesn't have to
     * construct the sealed value directly.
     */
    fun promptPasswordRetry(uri: Uri) {
        dialog = BackupDialog.ImportPassword(uri = uri, isRetry = true)
    }

    override fun onCleared() {
        pendingExportPassword?.fill('\u0000')
        pendingExportPassword = null
        (dialog as? BackupDialog.ImportConfirm)?.password?.fill('\u0000')
        dialog = null
        super.onCleared()
    }
}

/**
 * Which backup dialog is currently on screen. `null` = nothing open. All
 * sensitive material (passwords) lives inside the sealed values so dismissing
 * a dialog is enough to release the reference — the VM zero-fills what it
 * had to hold across the SAF round-trip separately.
 */
sealed interface BackupDialog {
    data object ExportPassword : BackupDialog

    data class ImportPassword(
        val uri: Uri,
        val isRetry: Boolean,
    ) : BackupDialog

    data class ImportConfirm(
        val uri: Uri,
        val password: CharArray?,
    ) : BackupDialog {
        override fun equals(other: Any?): Boolean = other is ImportConfirm && uri == other.uri && password.contentEqualsSafe(other.password)

        override fun hashCode(): Int = 31 * uri.hashCode() + (password?.size ?: 0)

        private fun CharArray?.contentEqualsSafe(other: CharArray?): Boolean =
            when {
                this == null && other == null -> true
                this == null || other == null -> false
                else -> contentEquals(other)
            }
    }
}
