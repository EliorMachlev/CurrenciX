package com.eliormachlev.currencix.view.preference

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.BackupResult
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.preference.compose.BackupScreen
import com.eliormachlev.currencix.viewmodel.preference.BackupViewModel

/**
 * Compose-hosted backup/restore screen. Was a `PreferenceFragmentCompat` with
 * hand-rolled dialog scaffolding; now a plain [Fragment] that mounts a
 * ComposeView rendering [BackupScreen]. SAF launchers still live here because
 * [androidx.activity.result.ActivityResultContracts] must be registered on a
 * lifecycle owner, and Toast wants a live Context — everything else (dialog
 * state, password stashing) sits inside [BackupViewModel].
 */
class BackupFragment : Fragment() {
    private lateinit var viewModel: BackupViewModel
    private lateinit var exportLauncher: ActivityResultLauncher<Intent>
    private lateinit var importLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[BackupViewModel::class.java]
        exportLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val uri = result.data?.data ?: return@registerForActivityResult
                val outcome = viewModel.runExport(uri)
                toast(exportResultMessage(outcome))
            }
        importLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                result.data?.data?.let(viewModel::beginImport)
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        activity?.setTitle(R.string.backup_manager_title)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    BackupScreen(
                        viewModel = viewModel,
                        onLaunchExport = ::launchExport,
                        onLaunchImport = ::launchImport,
                        onImportConfirmed = ::runImport,
                    )
                }
            }
        }
    }

    private fun launchExport() {
        val intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = getString(R.string.backup_mime_json)
                putExtra(Intent.EXTRA_TITLE, getString(R.string.backup_default_filename))
            }
        exportLauncher.launch(intent)
    }

    private fun launchImport() {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = getString(R.string.backup_mime_json)
            }
        importLauncher.launch(intent)
    }

    private fun runImport(
        uri: Uri,
        password: CharArray?,
    ) {
        when (val result = viewModel.runImport(uri, password)) {
            is BackupResult.Success -> toast(getString(R.string.backup_import_success))
            is BackupResult.Failure -> toast(getString(R.string.backup_import_failed, result.message))
            is BackupResult.PasswordRequired -> viewModel.promptPasswordRetry(uri)
            is BackupResult.WrongPassword -> viewModel.promptPasswordRetry(uri)
        }
    }

    private fun exportResultMessage(result: BackupResult): String =
        when (result) {
            is BackupResult.Success -> getString(R.string.backup_export_success)
            is BackupResult.Failure -> getString(R.string.backup_export_failed, result.message)
            // Export never asks for / rejects a password; treat these as bugs.
            is BackupResult.PasswordRequired,
            is BackupResult.WrongPassword,
            -> getString(R.string.backup_export_failed, "unexpected state")
        }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }
}
