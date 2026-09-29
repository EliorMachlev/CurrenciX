package com.eliormachlev.currencix.view.cart

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.repository.CartExporter
import com.eliormachlev.currencix.repository.CartFileResult
import com.eliormachlev.currencix.util.filenameTimestampNow
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel

private const val EXPORT_FILE_MIME = "application/json"
private const val EXPORT_FILE_EXT = ".json"

// Registry keys for the two document pickers. Fixed, so a result that
// lands after the Activity was recreated (the picker outlived a rotation or
// process death) is delivered to the cart screen that re-registers them.
private const val EXPORT_KEY = "cart_export_json"
private const val IMPORT_KEY = "cart_import_json"

/**
 * Owns the SAF launcher pair for cart JSON export / import. The cart is a
 * screen inside the single Activity, created long after the Activity
 * started, so the launchers are registered on the Activity's result
 * registry directly (no lifecycle owner) — the host must [unregister] when
 * the cart screen leaves. The [snackbar] callback bridges result messages
 * back to the host.
 */
class CartFileIo(
    private val activity: ComponentActivity,
    private val viewModel: CartViewModel,
    private val exporter: CartExporter,
    private val flushPendingCommits: () -> Unit,
    private val snackbar: (String) -> Unit,
) {
    private val exportLauncher: ActivityResultLauncher<String> =
        activity.activityResultRegistry.register(
            EXPORT_KEY,
            ActivityResultContracts.CreateDocument(EXPORT_FILE_MIME),
        ) { uri -> uri?.let(::doExport) }

    private val importLauncher: ActivityResultLauncher<Array<String>> =
        activity.activityResultRegistry.register(
            IMPORT_KEY,
            ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let(::doImport) }

    fun unregister() {
        exportLauncher.unregister()
        importLauncher.unregister()
    }

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
