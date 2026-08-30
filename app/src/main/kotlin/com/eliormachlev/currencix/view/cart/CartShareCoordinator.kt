package com.eliormachlev.currencix.view.cart

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.buildCartShareChooser
import com.eliormachlev.currencix.util.filenameTimestampNow
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.sanitizeForFilename
import com.eliormachlev.currencix.util.toCartDisplayString
import com.eliormachlev.currencix.util.toCartFeePercentDisplay
import com.eliormachlev.currencix.util.toCsv
import com.eliormachlev.currencix.util.toPdfBytes
import com.eliormachlev.currencix.viewmodel.cart.CartSnapshot
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel

private const val CSV_MIME = "text/csv"
private const val CSV_EXT = ".csv"
private const val PDF_MIME = "application/pdf"
private const val PDF_EXT = ".pdf"

/**
 * Presents the Share picker and dispatches to the selected format
 * (plain-text / CSV / PDF). Snapshots the cart before showing the picker so
 * every option renders the same numbers even if the user keeps typing.
 */
class CartShareCoordinator(
    private val activity: AppCompatActivity,
    private val viewModel: CartViewModel,
    private val flushPendingCommits: () -> Unit,
    private val snackbar: (String) -> Unit,
) {
    fun show() {
        flushPendingCommits()
        val snapshot = viewModel.snapshotForShare()
        if (snapshot == null) {
            snackbar(activity.getString(R.string.cart_share_empty))
            return
        }
        activity.showCartChoiceExplainerDialog(
            titleRes = R.string.menu_share,
            choices =
                listOf(
                    CartChoice(
                        R.string.cart_share_option_text,
                        R.string.cart_share_option_text_desc,
                    ) { shareAsText(snapshot) },
                    CartChoice(
                        R.string.cart_share_option_csv,
                        R.string.cart_share_option_csv_desc,
                    ) { shareAsCsv(snapshot) },
                    CartChoice(
                        R.string.cart_share_option_pdf,
                        R.string.cart_share_option_pdf_desc,
                    ) { shareAsPdf(snapshot) },
                ),
        )
    }

    private fun shareAsText(snapshot: CartSnapshot) {
        val text = buildShareText(snapshot)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        activity.startActivity(Intent.createChooser(intent, null))
    }

    private fun shareAsCsv(snapshot: CartSnapshot) {
        val title = shareTitle(snapshot)
        val chooser =
            buildCartShareChooser(
                context = activity,
                filename = shareFilename(title, CSV_EXT),
                mimeType = CSV_MIME,
                bytes = snapshot.toCsv(title = title).toByteArray(Charsets.UTF_8),
            )
        activity.startActivity(chooser)
    }

    private fun shareAsPdf(snapshot: CartSnapshot) {
        val title = shareTitle(snapshot)
        val chooser =
            buildCartShareChooser(
                context = activity,
                filename = shareFilename(title, PDF_EXT),
                mimeType = PDF_MIME,
                bytes = snapshot.toPdfBytes(title = title),
            )
        activity.startActivity(chooser)
    }

    // Cart name if the user has one (from Save-as), otherwise a phone-local
    // timestamp so the artefact still has an identifying handle.
    private fun shareTitle(snapshot: CartSnapshot): String = snapshot.cart.name.ifBlank { filenameTimestampNow() }

    private fun shareFilename(
        title: String,
        extension: String,
    ): String = title.sanitizeForFilename() + extension

    private fun buildShareText(snapshot: CartSnapshot): String =
        buildString {
            val baseIso = snapshot.baseCurrency.iso4217Alpha()
            val destIso = snapshot.destinationCurrency.iso4217Alpha()
            val name = snapshot.cart.name.ifBlank { activity.getString(R.string.cart_share_default_title) }
            appendLine(activity.getString(R.string.cart_share_header, name, baseIso))
            snapshot.evaluatedItems.forEach { (item, value) ->
                val label = item.name.ifBlank { item.expression }
                appendLine("• $label: ${value.toCartDisplayString()}")
            }
            appendLine("—")
            appendLine(activity.getString(R.string.cart_share_subtotal, snapshot.subtotal.toCartDisplayString(), baseIso))
            if (snapshot.isConverting) {
                appendLine(
                    activity.getString(R.string.cart_share_converted, snapshot.convertedSubtotal.toCartDisplayString(), destIso),
                )
            }
            val combinedStack = snapshot.sideStacks.combined
            if (!combinedStack.isNeutralFeeStack()) {
                appendLine(activity.getString(R.string.cart_share_fees, combinedStack.toCartFeePercentDisplay()))
            }
            append(activity.getString(R.string.cart_share_total, snapshot.total.toCartDisplayString(), destIso))
        }
}
