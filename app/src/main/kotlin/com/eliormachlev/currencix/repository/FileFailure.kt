package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.annotation.StringRes
import com.eliormachlev.currencix.R
import timber.log.Timber

/**
 * Why reading or writing a user-chosen file (a cart, a backup) failed, as
 * something the UI can say in the app's language. The technical detail
 * (an exception's message, the version found) travels next to it for the
 * log, never to the screen.
 */
enum class FileFailure(
    @StringRes val message: Int,
) {
    CANNOT_OPEN(R.string.file_error_cannot_open),
    READ_WRITE(R.string.file_error_read_write),
    NO_PERMISSION(R.string.file_error_no_permission),
    DAMAGED(R.string.file_error_damaged),
    UNSUPPORTED_VERSION(R.string.file_error_unsupported_version),
    NOT_A_CART(R.string.file_error_not_a_cart),
    ENCRYPTION(R.string.backup_error_encryption),
    DECRYPTION(R.string.backup_error_decryption),

    // A state the flow should never reach (e.g. export asked for a password).
    UNEXPECTED(R.string.file_error_unexpected),
}

/**
 * [reason]'s message in the app's language. [detail], the technical cause,
 * goes to the log only.
 */
fun Context.fileFailureMessage(
    reason: FileFailure,
    detail: String? = null,
): String {
    if (detail != null) Timber.tag(TAG).w("%s: %s", reason, detail)
    return getString(reason.message)
}

private const val TAG = "FileFailure"
