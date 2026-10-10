package com.eliormachlev.currencix.view.timeline.compose

import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.fromHtmlLegacy
import com.eliormachlev.currencix.util.rememberHapticOnClick

private val CHART_PADDING = TIMELINE_CONTENT_PADDING
private val PROVIDER_FONT_SIZE = 12.sp
private const val PROVIDER_ALPHA = 0.5f

/** What the chart card shows around (or instead of) the chart. */
@Immutable
data class ChartStatus(
    val isRefreshing: Boolean,
    // The provider failed, or doesn't cover the pair: shown in place of the chart.
    val error: String?,
    // The range holds no rates: a notice in place of the chart.
    val empty: Boolean,
    // "Data by …", in the card's corner.
    val provider: CharSequence?,
)

@Composable
internal fun TimelineChartCard(
    status: ChartStatus,
    onRetry: () -> Unit,
    onChangeProvider: () -> Unit,
    modifier: Modifier = Modifier,
    chart: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (status.isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }

        val content = Modifier.fillMaxSize().padding(CHART_PADDING)
        when {
            status.error != null -> TimelineErrorState(status.error, onRetry, onChangeProvider, content)

            // No rates in the chosen dates (a weekend, before the provider's
            // history): say so rather than leave the previous line up.
            status.empty -> TimelineNotice(R.drawable.ic_event, AnnotatedString(stringResource(R.string.timeline_no_rates)), content)

            else -> Box(content) { chart() }
        }

        val provider = status.provider
        if (provider != null) {
            AndroidView(
                factory = { ctx ->
                    TextView(ctx).apply {
                        textSize = PROVIDER_FONT_SIZE.value
                        alpha = PROVIDER_ALPHA
                        gravity = android.view.Gravity.END
                    }
                },
                update = { it.text = provider.fromHtmlLegacy() },
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = CHART_PADDING, vertical = 8.dp)
                        .wrapContentSize(),
            )
        }
    }
}

// No chart to show: the provider failed, or doesn't cover this pair. Says
// why, and offers the two ways out — try again, or pick another provider.
@Composable
private fun TimelineErrorState(
    message: String,
    onRetry: () -> Unit,
    onChangeProvider: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TimelineNotice(R.drawable.ic_sync_problem, remember(message) { AnnotatedString.fromHtml(message) }, modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(NOTICE_GAP)) {
            OutlinedButton(onClick = rememberHapticOnClick(onRetry)) { Text(stringResource(R.string.timeline_retry)) }
            FilledTonalButton(onClick = rememberHapticOnClick(onChangeProvider)) {
                Text(stringResource(R.string.change_provider))
            }
        }
    }
}

// An icon and a line of text in place of the chart, with optional actions under them.
@Composable
private fun TimelineNotice(
    @DrawableRes icon: Int,
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(NOTICE_ICON_SIZE),
        )
        Spacer(Modifier.height(NOTICE_GAP))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actions != null) {
            Spacer(Modifier.height(NOTICE_GAP))
            actions()
        }
    }
}

private val NOTICE_ICON_SIZE = 40.dp
private val NOTICE_GAP = 12.dp
