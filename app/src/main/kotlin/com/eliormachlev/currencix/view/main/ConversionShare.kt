package com.eliormachlev.currencix.view.main

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.withFrameNanos
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.util.SHARE_IMAGES_SUBDIR
import com.eliormachlev.currencix.util.buildShareChooser
import com.eliormachlev.currencix.util.feePercentDelta
import com.eliormachlev.currencix.util.filenameTimestampNow
import com.eliormachlev.currencix.util.isNeutralFeeStack
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.util.toPngBytes
import com.eliormachlev.currencix.view.main.compose.HeroCaptureController
import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import timber.log.Timber
import java.math.BigDecimal
import java.math.MathContext

// Fee-percent precision on the share fee-stamp line — matches the on-screen
// FeeChip so shared text reads the same as the visible pill.
private const val FEE_PERCENT_DECIMAL_PLACES = 2

// Rate footer line ("1 USD = 3.028 ILS") — matches the on-screen hero footer
// precision (see FOOTER_RATE_DECIMAL_PLACES in MainDisplay.kt).
private const val SHARE_RATE_DECIMAL_PLACES = 4

// Fee-name joiner for the share fee stamp — matches the on-screen FeeChip.
private const val SHARE_FEE_NAME_SEPARATOR = ", "

// Hero-card snapshot chooser payload. MIME + extension pair kept together so
// the file name and Intent's `type` never drift out of sync.
private const val SHARE_IMAGE_MIME = "image/png"
private const val SHARE_IMAGE_EXT = ".png"
private const val SHARE_TEXT_MIME = "text/plain"

/**
 * The converter's Share action: a snapshot of the hero card plus a text
 * version of the conversion, as one chooser.
 */
internal class ConversionShare(
    private val context: Context,
    private val viewModel: MainViewModel,
    val heroCapture: HeroCaptureController,
    private val status: ConverterStatus,
) {
    /**
     * Must run on a coroutine with a frame clock (a Compose scope): the
     * drawer has just started closing when this fires, so it waits one frame
     * to keep the closing drawer out of the snapshot. Falls back to a
     * text-only share if the hero card can't be captured (not composed yet),
     * so the tap is never a no-op.
     */
    suspend fun share() {
        val text = buildText() ?: return
        withFrameNanos { }
        val bitmap = heroCapture.capture()
        if (bitmap == null) Timber.w("share: hero capture returned null, falling back to text share")
        val chooser =
            if (bitmap != null) {
                buildShareChooser(
                    context = context,
                    subdir = SHARE_IMAGES_SUBDIR,
                    filename = "currencix-${filenameTimestampNow()}$SHARE_IMAGE_EXT",
                    mimeType = SHARE_IMAGE_MIME,
                    bytes = bitmap.toPngBytes(),
                    extraText = text,
                )
            } else {
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = SHARE_TEXT_MIME
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    null,
                )
            }
        context.startActivity(chooser)
    }

    // The chooser's EXTRA_TEXT:
    //   $50 USD = ₪151.4 ILS
    //   +1% MAX = ₪152.91 ILS       (only when a fee stack is active)
    //
    //   -- Based on Bank of Israel, 18/09/26, $1 USD = ₪3.028 ILS
    // Built from the on-screen values so it honors the typed amount and the
    // active fee stack (matching what the user sees).
    internal fun buildText(): String? {
        val base = viewModel.getBaseCurrency().value ?: return null
        val dest = viewModel.getDestinationCurrency().value ?: return null
        val rates = viewModel.getExchangeRates().value ?: return null
        val rateList = rates.rates ?: return null
        if (rateList.none { it.currency == base } || rateList.none { it.currency == dest }) return null
        val places = viewModel.getDecimalPlaces().value
        val amount = viewModel.getCurrentBaseValueAsNumber().value ?: BigDecimal.ZERO
        val result = viewModel.getResultAsNumber().value ?: BigDecimal.ZERO
        val main =
            conversionLine(
                base = base,
                dest = dest,
                baseAmount = amount.toHumanReadableNumber(context, trim = true, decimalPlaces = places),
                destAmount = result.toHumanReadableNumber(context, trim = true, decimalPlaces = places),
            )
        val feeLine = feeLine(dest, places)
        val footer = footer(base, dest, rates) ?: return null
        return buildString {
            append(main)
            if (feeLine != null) {
                append('\n')
                append(feeLine)
            }
            append("\n\n-- ")
            append(footer)
        }
    }

    // "<baseSymbol><baseAmount> <baseIso> = <destSymbol><destAmount> <destIso>"
    // — one template (and one localized string) for the main line, the fee
    // line and the rate stamp in the footer.
    private fun conversionLine(
        base: Currency,
        dest: Currency,
        baseAmount: String,
        destAmount: String,
    ): String =
        context.getString(
            R.string.share_conversion_line,
            base.symbolOrIso(),
            baseAmount,
            base.iso4217Alpha(),
            dest.symbolOrIso(),
            destAmount,
            dest.iso4217Alpha(),
        )

    // Fee stamp beneath the main conversion, mirroring the on-screen FeeChip:
    // "<+pct> <NAMES> = <destSymbol><trueCost> <destIso>". Skipped when no fee
    // stack is active or the true cost isn't computed yet.
    private fun feeLine(
        dest: Currency,
        places: Int,
    ): String? {
        val stack = viewModel.getFeeStack().value ?: return null
        if (stack.isNeutralFeeStack()) return null
        val trueCost = viewModel.getResultWithFeesAsNumber().value ?: return null
        val percent =
            stack
                .feePercentDelta(FEE_PERCENT_DECIMAL_PLACES)
                .toHumanReadableNumber(context, showPositiveSign = true, suffix = "%", trim = true)
        val names =
            viewModel
                .getActiveFees()
                .value
                .orEmpty()
                .mapNotNull { it.name.trim().takeIf(String::isNotEmpty) }
                .joinToString(SHARE_FEE_NAME_SEPARATOR)
                .uppercase()
        val stamp = if (names.isEmpty()) percent else "$percent $names"
        return context.getString(
            R.string.share_fee_line,
            stamp,
            dest.symbolOrIso(),
            trueCost.toHumanReadableNumber(context, trim = true, decimalPlaces = places),
            dest.iso4217Alpha(),
        )
    }

    // "Based on <provider>, <date>, <$1 base = <sym><rate> <destIso>>". The
    // rate tail is appended here rather than added as a placeholder on
    // `share_footer`, so its 30+ translations don't need an extra argument
    // (lint's StringFormatMatches would reject the count mismatch).
    private fun footer(
        base: Currency,
        dest: Currency,
        rates: ExchangeRates,
    ): String? {
        val providerName = rates.provider?.getName(context) ?: return null
        val dateString = status.formatTimestamp(rates.date, rates.time) ?: return null
        val rateList = rates.rates ?: return null
        val baseValue = rateList.firstOrNull { it.currency == base }?.value ?: return null
        val destValue = rateList.firstOrNull { it.currency == dest }?.value ?: return null
        val perOne = destValue.divide(baseValue, MathContext.DECIMAL128)
        val rateLine =
            conversionLine(
                base = base,
                dest = dest,
                baseAmount = "1",
                destAmount = perOne.toHumanReadableNumber(context, trim = true, decimalPlaces = SHARE_RATE_DECIMAL_PLACES),
            )
        return "${context.getString(R.string.share_footer, providerName, dateString)}, $rateLine"
    }
}
