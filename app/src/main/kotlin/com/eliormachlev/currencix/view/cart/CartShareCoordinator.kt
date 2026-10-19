package com.eliormachlev.currencix.view.cart

import android.content.Context
import android.content.Intent
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.CartExportLabels
import com.eliormachlev.currencix.util.buildCartShareChooser
import com.eliormachlev.currencix.util.buildTextShareChooser
import com.eliormachlev.currencix.util.filenameTimestampNow
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.sanitizeForFilename
import com.eliormachlev.currencix.util.toCartDisplayString
import com.eliormachlev.currencix.util.toCartFeePercentDisplay
import com.eliormachlev.currencix.util.toCsv
import com.eliormachlev.currencix.util.toPdfBytes
import com.eliormachlev.currencix.util.toPngBytes
import com.eliormachlev.currencix.view.cart.compose.CartChoiceOption
import com.eliormachlev.currencix.view.cart.compose.CartChoiceRequest
import com.eliormachlev.currencix.viewmodel.cart.CartSnapshot
import com.eliormachlev.currencix.viewmodel.cart.CartViewModel
import com.eliormachlev.currencix.viewmodel.cart.budgetLeft
import com.eliormachlev.currencix.viewmodel.cart.perPerson
import com.eliormachlev.currencix.viewmodel.cart.personSharesOf

private const val CSV_MIME = "text/csv"
private const val CSV_EXT = ".csv"
private const val PDF_MIME = "application/pdf"
private const val PDF_EXT = ".pdf"
private const val PNG_MIME = "image/png"
private const val PNG_EXT = ".png"

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
                            R.string.cart_share_option_image,
                            R.string.cart_share_option_image_desc,
                        ) { shareAsImage(snapshot) },
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
        context.startActivity(buildTextShareChooser(buildShareText(snapshot)))
    }

    private fun shareAsCsv(snapshot: CartSnapshot) =
        shareFile(snapshot, CSV_EXT, CSV_MIME) { title, labels -> toCsv(title = title, labels = labels).toByteArray(Charsets.UTF_8) }

    private fun shareAsPdf(snapshot: CartSnapshot) = shareFile(snapshot, PDF_EXT, PDF_MIME) { title, labels -> toPdfBytes(title, labels) }

    private fun shareAsImage(snapshot: CartSnapshot) = shareFile(snapshot, PNG_EXT, PNG_MIME) { title, labels -> toPngBytes(title, labels) }

    // The cart as a file ([render] makes its bytes), handed to the share sheet.
    private fun shareFile(
        snapshot: CartSnapshot,
        extension: String,
        mimeType: String,
        render: CartSnapshot.(title: String, labels: CartExportLabels) -> ByteArray,
    ) {
        val title = shareTitle(snapshot)
        val chooser =
            buildCartShareChooser(
                context = context,
                filename = shareFilename(title, extension),
                mimeType = mimeType,
                bytes = snapshot.render(title, CartExportLabels.from(context)),
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
            snapshot.cart.extras.tipPercent?.let { percent ->
                appendLine(context.getString(R.string.cart_share_tip, percent.toPlainString(), snapshot.tip.toCartDisplayString(), baseIso))
            }
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
            appendExtras(snapshot, destIso)
        }

    // "Per person (÷3): …" and "Left in budget: …" / "Over budget: …" after the total.
    private fun StringBuilder.appendExtras(
        snapshot: CartSnapshot,
        destIso: String,
    ) {
        val extras = snapshot.cart.extras
        // "Dana: 120.40 ILS", one line each, when it splits by person.
        personSharesOf(snapshot.cart, snapshot.total).forEach { (person, share) ->
            val name = person.name.ifBlank { context.getString(R.string.cart_person_unnamed) }
            append('\n').append(context.getString(R.string.cart_share_person, name, share.toCartDisplayString(), destIso))
        }
        if (!extras.splitsByPerson && extras.splitWays > 1) {
            val each = perPerson(snapshot.total, extras.splitWays).toCartDisplayString()
            append('\n').append(context.getString(R.string.cart_share_per_person, extras.splitWays, each, destIso))
        }
        extras.budget?.let { budget ->
            val left = budgetLeft(snapshot.total, budget)
            val res = if (left.signum() < 0) R.string.cart_share_over_budget else R.string.cart_share_budget_left
            append('\n').append(context.getString(res, left.abs().toCartDisplayString(), destIso))
        }
    }
}
