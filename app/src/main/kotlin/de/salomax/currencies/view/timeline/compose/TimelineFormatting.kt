package de.salomax.currencies.view.timeline.compose

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.window.layout.FoldingFeature
import de.salomax.currencies.util.hasAppendedCurrencySymbol
import de.salomax.currencies.util.toHumanReadableNumber
import java.math.BigDecimal

// Row/Column split rules used by both the foldable observer and the initial
// (pre-foldable-info) layout choice. Boolean because the two variants are
// binary and hoisting an enum solely for readability adds noise.
internal enum class TimelineLayout { ROW, COLUMN }

/**
 * Layout to use given the current [FoldingFeature]. Mirrors the pre-Compose
 * [android.widget.LinearLayout] orientation logic: on a portrait fold, only a
 * bend flips to horizontal; on a landscape fold, only flat keeps horizontal.
 */
internal fun orientationFor(feature: FoldingFeature): TimelineLayout {
    val isPortrait = feature.orientation == FoldingFeature.Orientation.VERTICAL
    val isBent =
        feature.state == FoldingFeature.State.HALF_OPENED ||
            (!isPortrait && feature.state == FoldingFeature.State.FLAT)
    return if (isPortrait) {
        if (isBent) TimelineLayout.ROW else TimelineLayout.COLUMN
    } else {
        if (isBent) TimelineLayout.COLUMN else TimelineLayout.ROW
    }
}

/** Fallback layout used until the folding-feature stream emits (or on non-foldables). */
internal fun defaultLayoutFor(orientation: Int): TimelineLayout =
    if (orientation == Configuration.ORIENTATION_LANDSCAPE) TimelineLayout.ROW else TimelineLayout.COLUMN

/**
 * Build "<symbol> <bold-number>" or "<bold-number> <symbol>" depending on
 * locale, matching the pre-Compose [android.text.SpannableStringBuilder]
 * output — only the number is bold.
 */
internal fun combineValueAndSymbol(
    context: Context,
    value: BigDecimal,
    symbol: String?,
    decimalPlaces: Int,
): AnnotatedString {
    val number = value.toHumanReadableNumber(context, decimalPlaces = decimalPlaces)
    val safeSymbol = symbol ?: ""
    return buildAnnotatedString {
        if (hasAppendedCurrencySymbol(context)) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(number) }
            append(" $safeSymbol")
        } else {
            append("$safeSymbol ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(number) }
        }
    }
}

/** Format the rate-difference percent as "+ 12 %" / "- 3 %" or blank when null. */
internal fun formatRateDiff(
    context: Context,
    value: BigDecimal?,
    decimals: Int,
): String = value?.toHumanReadableNumber(context, decimals, true, "%") ?: ""
