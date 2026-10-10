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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.BackupResult
import com.eliormachlev.currencix.repository.FileFailure
import com.eliormachlev.currencix.repository.fileFailureMessage
import com.eliormachlev.currencix.view.compose.AppSnackbar
import com.eliormachlev.currencix.view.compose.LocalAppSnackbar
import com.eliormachlev.currencix.view.compose.ScreenScaffold
import com.eliormachlev.currencix.view.compose.showOrToast
import com.eliormachlev.currencix.view.preference.compose.BackupScreen
import com.eliormachlev.currencix.view.preference.compose.FeesScreen
import com.eliormachlev.currencix.view.preference.compose.PreferenceScreen
import com.eliormachlev.currencix.view.preference.compose.PreferenceScreenCallbacks
import com.eliormachlev.currencix.viewmodel.preference.BackupViewModel
import com.eliormachlev.currencix.viewmodel.preference.FeeManagerViewModel
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel

/**
 * Settings. [onThemeRequiresRestart] recreates the Activity so the XML theme
 * (pure black) is reapplied everywhere; the back stack is saved state, so
 * the user lands back on Settings.
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenFees: () -> Unit,
    onOpenBackup: () -> Unit,
    onThemeRequiresRestart: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: PreferenceViewModel = viewModel()
    val callbacks =
        remember(context, onOpenFees, onOpenBackup, onThemeRequiresRestart) {
            PreferenceScreenCallbacks(
                onOpenFees = onOpenFees,
                onOpenBackup = onOpenBackup,
                onRateApp = rateApp?.let { rate -> { rate(context) } },
                onThemeRequiresRestart = onThemeRequiresRestart,
            )
        }
    SettingsScaffold(titleRes = R.string.title_preferences, onBack = onBack) {
        PreferenceScreen(viewModel = viewModel, callbacks = callbacks)
    }
}

/** The fee manager — from Settings, the converter's drawer and fee chip, or the cart. */
@Composable
fun FeesRoute(onBack: () -> Unit) {
    val viewModel: FeeManagerViewModel = viewModel()
    SettingsScaffold(titleRes = R.string.fee_manager_title, onBack = onBack) {
        FeesScreen(viewModel = viewModel)
    }
}

/** Backup / restore, with the document pickers it hands off to. */
@Composable
fun BackupRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = LocalAppSnackbar.current
    val viewModel: BackupViewModel = viewModel()
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            snackbar.showOrToast(context, exportResultMessage(context, viewModel.runExport(uri)))
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.data?.let(viewModel::beginImport)
        }
    SettingsScaffold(titleRes = R.string.backup_manager_title, onBack = onBack) {
        BackupScreen(
            viewModel = viewModel,
            onLaunchExport = { exportLauncher.launch(backupDocumentIntent(context, Intent.ACTION_CREATE_DOCUMENT)) },
            onLaunchImport = { importLauncher.launch(backupDocumentIntent(context, Intent.ACTION_OPEN_DOCUMENT)) },
            onImportConfirmed = { uri, password -> runImport(context, snackbar, viewModel, uri, password) },
        )
    }
}

// The three settings screens share one frame: a medium top bar over a list
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
    viewModel: BackupViewModel,
    uri: Uri,
    password: CharArray?,
) {
    when (val result = viewModel.runImport(uri, password)) {
        is BackupResult.Success -> {
            snackbar.showOrToast(context, context.getString(R.string.backup_import_success))
        }

        is BackupResult.Failure -> {
            snackbar.showOrToast(
                context,
                context.getString(R.string.backup_import_failed, context.fileFailureMessage(result.reason, result.detail)),
            )
        }

        is BackupResult.PasswordRequired -> {
            viewModel.promptPasswordRetry(uri)
        }

        is BackupResult.WrongPassword -> {
            viewModel.promptPasswordRetry(uri)
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
