package com.eliormachlev.currencix.view.cart

import android.content.Context
import android.content.Intent
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.buildCartShareChooser
import com.eliormachlev.currencix.util.filenameTimestampNow
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.sanitizeForFilename
import com.eliormachlev.currencix.util.toCartDisplayString
import com.eliormachlev.currencix.util.toCartFeePercentDisplay
import com.eliormachlev.currencix.util.toCsv
import com.eliormachlev.currencix.util.toPdfBytes
import com.eliormachlev.currencix.view.cart.compose.CartChoiceOption
import com.eliormachlev.currencix.view.cart.compose.CartChoiceRequest
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
    private val context: Context,
    private val viewModel: CartViewModel,
    private val flushPendingCommits: () -> Unit,
    private val snackbar: (String) -> Unit,
    private val showChoice: (CartChoiceRequest) -> Unit,
) {
    fun show() {
        flushPendingCommits()
        val snapshot = viewModel.snapshotForShare()
        if (snapshot == null) {
            snackbar(context.getString(R.string.cart_share_empty))
            return
        }
        showChoice(
            CartChoiceRequest(
                titleRes = R.string.menu_share,
                options =
                    listOf(
                        CartChoiceOption(
                            R.string.cart_share_option_text,
                            R.string.cart_share_option_text_desc,
                        ) { shareAsText(snapshot) },
                        CartChoiceOption(
                            R.string.cart_share_option_csv,
                            R.string.cart_share_option_csv_desc,
                        ) { shareAsCsv(snapshot) },
                        CartChoiceOption(
                            R.string.cart_share_option_pdf,
                            R.string.cart_share_option_pdf_desc,
                        ) { shareAsPdf(snapshot) },
                    ),
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
        context.startActivity(Intent.createChooser(intent, null))
    }

    private fun shareAsCsv(snapshot: CartSnapshot) {
        val title = shareTitle(snapshot)
        val chooser =
            buildCartShareChooser(
                context = context,
                filename = shareFilename(title, CSV_EXT),
                mimeType = CSV_MIME,
                bytes = snapshot.toCsv(title = title).toByteArray(Charsets.UTF_8),
            )
        context.startActivity(chooser)
    }

    private fun shareAsPdf(snapshot: CartSnapshot) {
        val title = shareTitle(snapshot)
        val chooser =
            buildCartShareChooser(
                context = context,
                filename = shareFilename(title, PDF_EXT),
                mimeType = PDF_MIME,
                bytes = snapshot.toPdfBytes(title = title),
            )
        context.startActivity(chooser)
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
            val name = snapshot.cart.name.ifBlank { context.getString(R.string.cart_share_default_title) }
            appendLine(context.getString(R.string.cart_share_header, name, baseIso))
            snapshot.evaluatedItems.forEach { (item, value) ->
                val label = item.name.ifBlank { item.expression }
                appendLine("• $label: ${value.toCartDisplayString()}")
            }
            appendLine("—")
            appendLine(context.getString(R.string.cart_share_subtotal, snapshot.subtotal.toCartDisplayString(), baseIso))
            if (snapshot.isConverting) {
                appendLine(
                    context.getString(R.string.cart_share_converted, snapshot.convertedSubtotal.toCartDisplayString(), destIso),
                )
            }
            val combinedStack = snapshot.feeStack
            if (!combinedStack.isNeutralFeeStack()) {
                appendLine(context.getString(R.string.cart_share_fees, combinedStack.toCartFeePercentDisplay()))
            }
            append(context.getString(R.string.cart_share_total, snapshot.total.toCartDisplayString(), destIso))
        }
}
