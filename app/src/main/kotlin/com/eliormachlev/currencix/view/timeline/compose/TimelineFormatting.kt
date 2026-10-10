package com.eliormachlev.currencix.view.timeline.compose

import android.content.Context
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.layout.FoldingFeature
import com.eliormachlev.currencix.util.hasAppendedCurrencySymbol
import com.eliormachlev.currencix.util.toHumanReadableNumber
import java.math.BigDecimal

// Shared timeline typography/spacing so a design tweak lands in one place instead
// of drifting across TimelineChartCard and TimelineSecondary.

internal val TIMELINE_CONTENT_PADDING = 16.dp
internal val TIMELINE_DATE_FONT_SIZE = 12.sp
internal val TIMELINE_RATE_VALUE_FONT_SIZE = 18.sp
internal const val TIMELINE_DATE_LETTER_SPACING_EM = 0.075f

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

/**
 * Fallback layout used until the folding-feature stream emits (or on
 * non-foldables): side by side when the screen's space is wider than tall.
 * The space, not the device orientation — beside the converter on a
 * landscape tablet, the timeline pane is portrait-shaped.
 */
internal fun defaultLayoutFor(isLandscapeSpace: Boolean): TimelineLayout =
    if (isLandscapeSpace) TimelineLayout.ROW else TimelineLayout.COLUMN

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
