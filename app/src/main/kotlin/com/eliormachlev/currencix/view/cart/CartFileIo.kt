package com.eliormachlev.currencix.view.cart

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.CartExporter
import com.eliormachlev.currencix.repository.CartFileResult
import com.eliormachlev.currencix.util.filenameTimestampNow
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel

private const val EXPORT_FILE_MIME = "application/json"
private const val EXPORT_FILE_EXT = ".json"

/**
 * Owns the SAF launcher pair for cart JSON export / import. Registration must
 * happen before the host activity reaches STARTED, so construct this in the
 * activity's onCreate before observe() runs. The [snackbar] callback bridges
 * result messages back to the host's snackbar host.
 */
class CartFileIo(
    private val activity: AppCompatActivity,
    private val viewModel: CartViewModel,
    private val exporter: CartExporter,
    private val flushPendingCommits: () -> Unit,
    private val snackbar: (String) -> Unit,
) {
    private val exportLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(
            ActivityResultContracts.CreateDocument(EXPORT_FILE_MIME),
        ) { uri -> uri?.let(::doExport) }

    private val importLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let(::doImport) }

    fun launchExport() {
        flushPendingCommits()
        val name =
            viewModel
                .getCurrentCart()
                .value
                ?.name
                ?.ifBlank { null } ?: "cart"
        exportLauncher.launch("$name-${filenameTimestampNow()}$EXPORT_FILE_EXT")
    }

    fun launchImport() {
        importLauncher.launch(arrayOf(EXPORT_FILE_MIME))
    }

    private fun doExport(uri: Uri) {
        val cart = viewModel.getCurrentCart().value ?: return
        // Copy so the exported file always has a real name, even if the
        // user hasn't gone through Save-as yet.
        val toExport =
            cart.copy(
                name = cart.name.ifBlank { activity.getString(R.string.cart_default_saved_name) },
                createdAt = System.currentTimeMillis(),
            )
        when (val res = exporter.export(uri, toExport)) {
            is CartFileResult.Success -> snackbar(activity.getString(R.string.cart_export_ok))
            is CartFileResult.Failure ->
                snackbar(activity.getString(R.string.cart_export_error, res.message))
            is CartFileResult.Loaded -> Unit
        }
    }

    private fun doImport(uri: Uri) {
        when (val res = exporter.import(uri)) {
            is CartFileResult.Loaded -> {
                viewModel.setCurrent(res.cart)
                snackbar(activity.getString(R.string.cart_import_ok))
            }
            is CartFileResult.Failure ->
                snackbar(activity.getString(R.string.cart_import_error, res.message))
            is CartFileResult.Success -> Unit
        }
    }
}
