package com.eliormachlev.currencix.view.preference

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.BackupResult
import com.eliormachlev.currencix.repository.FileFailure
import com.eliormachlev.currencix.repository.fileFailureMessage
import com.eliormachlev.currencix.view.compose.AppSnackbar
import com.eliormachlev.currencix.view.compose.LocalAppSnackbar
import com.eliormachlev.currencix.view.compose.ScreenScaffold
import com.eliormachlev.currencix.view.compose.currentApplication
import com.eliormachlev.currencix.view.compose.rememberDatabase
import com.eliormachlev.currencix.view.compose.retainWithApplication
import com.eliormachlev.currencix.view.compose.showOrToast
import com.eliormachlev.currencix.view.navigation.Screen
import com.eliormachlev.currencix.view.navigation.SettingsTopic
import com.eliormachlev.currencix.view.preference.compose.BackupScreen
import com.eliormachlev.currencix.view.preference.compose.FeesScreen
import com.eliormachlev.currencix.view.preference.compose.PreferenceScreen
import com.eliormachlev.currencix.view.preference.compose.PreferenceScreenCallbacks
import com.eliormachlev.currencix.view.preference.compose.ReceiptLanguagesScreen
import com.eliormachlev.currencix.view.preference.compose.titleRes
import com.eliormachlev.currencix.viewmodel.preference.BackupStateHolder
import com.eliormachlev.currencix.viewmodel.preference.PreferenceSettings

/**
 * Settings: its list of categories ([topic] null), or one category's own
 * screen. [onOpen] goes on to a nested screen (a category, fees, backup).
 * [onThemeRequiresRestart] recreates the Activity so the XML theme (pure
 * black) is reapplied everywhere; the back stack is saved state, so the user
 * lands back where they were.
 */
@Composable
fun SettingsRoute(
    topic: SettingsTopic?,
    onBack: () -> Unit,
    onOpen: (Screen) -> Unit,
    onThemeRequiresRestart: () -> Unit,
) {
    val context = LocalContext.current
    val settings = rememberPreferenceSettings()
    val callbacks =
        remember(context, onOpen, onThemeRequiresRestart) {
            PreferenceScreenCallbacks(
                onOpen = onOpen,
                onRateApp = rateApp?.let { rate -> { rate(context) } },
                onThemeRequiresRestart = onThemeRequiresRestart,
            )
        }
    SettingsScaffold(titleRes = topic?.titleRes ?: R.string.title_preferences, onBack = onBack) {
        PreferenceScreen(settings = settings, callbacks = callbacks, topic = topic)
    }
}

/** The stored settings, for the screens that read and change them (built from the application, so kept across recompositions). */
@Composable
internal fun rememberPreferenceSettings(): PreferenceSettings {
    val application = currentApplication()
    return remember(application) { PreferenceSettings(application) }
}

/** The fee manager — from Settings, the converter's drawer and fee chip, or the cart. */
@Composable
fun FeesRoute(onBack: () -> Unit) {
    val fees = rememberDatabase().fees
    SettingsScaffold(titleRes = R.string.fee_manager_title, onBack = onBack) {
        FeesScreen(store = fees)
    }
}

/** The languages receipts are read in: add one (for good or a trip), keep it longer, or delete it. */
@Composable
fun ReceiptLanguagesRoute(onBack: () -> Unit) {
    SettingsScaffold(titleRes = R.string.receipt_languages_title, onBack = onBack) {
        ReceiptLanguagesScreen()
    }
}

/** Backup / restore, with the document pickers it hands off to. */
@Composable
fun BackupRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalAppSnackbar.current
    val holder = retainWithApplication { BackupStateHolder(it) }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            holder.runExport(uri) { exported -> snackbar.showOrToast(context, exportResultMessage(context, exported)) }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.data?.let(holder::beginImport)
        }
    SettingsScaffold(titleRes = R.string.backup_manager_title, onBack = onBack) {
        BackupScreen(
            holder = holder,
            onLaunchExport = { exportLauncher.launch(backupDocumentIntent(context, Intent.ACTION_CREATE_DOCUMENT)) },
            onLaunchImport = { importLauncher.launch(backupDocumentIntent(context, Intent.ACTION_OPEN_DOCUMENT)) },
            onImportConfirmed = { uri, password -> runImport(context, snackbar, holder, uri, password) },
        )
    }
}

// The settings screens share one frame: a medium top bar over a list
// that scrolls under it.
@Composable
private fun SettingsScaffold(
    @StringRes titleRes: Int,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    ScreenScaffold(title = { Text(stringResource(titleRes)) }, onBack = onBack) { padding: PaddingValues ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) { content() }
    }
}

// A backup is one JSON document; export suggests a file name.
private fun backupDocumentIntent(
    context: Context,
    action: String,
): Intent =
    Intent(action).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = context.getString(R.string.backup_mime_json)
        if (action == Intent.ACTION_CREATE_DOCUMENT) {
            putExtra(Intent.EXTRA_TITLE, context.getString(R.string.backup_default_filename))
        }
    }

private fun runImport(
    context: Context,
    snackbar: AppSnackbar?,
    holder: BackupStateHolder,
    uri: Uri,
    password: CharArray?,
) {
    holder.runImport(uri, password) { result -> showImportResult(context, snackbar, holder, uri, result) }
}

private fun showImportResult(
    context: Context,
    snackbar: AppSnackbar?,
    holder: BackupStateHolder,
    uri: Uri,
    result: BackupResult,
) {
    when (result) {
        is BackupResult.Success -> {
            snackbar.showOrToast(context, context.getString(R.string.backup_import_success))
        }

        is BackupResult.Failure -> {
            snackbar.showOrToast(
                context,
                context.getString(R.string.backup_import_failed, context.fileFailureMessage(result.reason, result.detail)),
            )
        }

        is BackupResult.PasswordRequired, is BackupResult.WrongPassword -> {
            holder.promptPasswordRetry(uri)
        }
    }
}

private fun exportResultMessage(
    context: Context,
    result: BackupResult,
): String =
    when (result) {
        is BackupResult.Success -> {
            context.getString(R.string.backup_export_success)
        }

        is BackupResult.Failure -> {
            context.getString(
                R.string.backup_export_failed,
                context.fileFailureMessage(result.reason, result.detail),
            )
        }

        // Export never asks for / rejects a password; treat these as bugs.
        is BackupResult.PasswordRequired,
        is BackupResult.WrongPassword,
        -> {
            context.getString(R.string.backup_export_failed, context.fileFailureMessage(FileFailure.UNEXPECTED, result.toString()))
        }
    }
