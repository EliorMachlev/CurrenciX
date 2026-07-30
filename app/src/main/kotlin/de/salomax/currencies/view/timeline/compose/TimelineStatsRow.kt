package de.salomax.currencies.view.timeline.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Matches the drawable/dotted_line.xml: 1dp stroke, 1dp on / 4dp off,
// aligned to the baseline of the value/date text.
private val DOT_STROKE_WIDTH = 1.dp
private val DOT_DASH_ON = 1.dp
private val DOT_DASH_OFF = 4.dp
private val ROW_VERTICAL_PADDING = 2.dp
private val VALUE_START_PADDING = 16.dp
private val DOT_HORIZONTAL_PADDING = 8.dp
private val DATE_START_PADDING = 16.dp
private val LABEL_FONT_SIZE = 14.sp
private val VALUE_FONT_SIZE = 18.sp
private val DATE_FONT_SIZE = 12.sp
private const val DATE_LETTER_SPACING_EM = 0.075f

@Composable
@Suppress("LongParameterList")
internal fun TimelineStatsRow(
    label: String,
    labelWidth: Dp,
    value: AnnotatedString?,
    date: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(vertical = ROW_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            fontSize = LABEL_FONT_SIZE,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(labelWidth),
        )
        Text(
            text = value ?: AnnotatedString(""),
            fontSize = VALUE_FONT_SIZE,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(start = VALUE_START_PADDING),
        )
        if (date != null) {
            DottedSpacer(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = DOT_HORIZONTAL_PADDING),
            )
            Text(
                text = date,
                fontSize = DATE_FONT_SIZE,
                letterSpacing = DATE_LETTER_SPACING_EM.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = DATE_START_PADDING),
            )
        }
    }
}

@Composable
private fun DottedSpacer(modifier: Modifier = Modifier) {
    val color = LocalContentColor.current.copy(alpha = 0.6f)
    Canvas(modifier = modifier.wrapContentHeight().padding(bottom = 4.dp)) {
        val strokePx = DOT_STROKE_WIDTH.toPx()
        val onPx = DOT_DASH_ON.toPx()
        val offPx = DOT_DASH_OFF.toPx()
        val y = size.height / 2f
        drawLine(
            color = color,
            start =
                androidx.compose.ui.geometry
                    .Offset(0f, y),
            end =
                androidx.compose.ui.geometry
                    .Offset(size.width, y),
            strokeWidth = strokePx,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(onPx, offPx), 0f),
            cap = Stroke.DefaultCap,
        )
    }
}
