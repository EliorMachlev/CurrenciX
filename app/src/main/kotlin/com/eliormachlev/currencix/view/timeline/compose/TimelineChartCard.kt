package com.eliormachlev.currencix.view.timeline.compose

import android.widget.TextView
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

@Composable
@Suppress("LongParameterList")
internal fun TimelineChartCard(
    isRefreshing: Boolean,
    error: String?,
    provider: CharSequence?,
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
        if (isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }

        if (error != null) {
            TimelineErrorState(
                message = error,
                onRetry = onRetry,
                onChangeProvider = onChangeProvider,
                modifier = Modifier.fillMaxSize().padding(CHART_PADDING),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(CHART_PADDING)) {
                chart()
            }
        }

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
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_sync_problem),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ERROR_ICON_SIZE),
        )
        Spacer(Modifier.height(ERROR_GAP))
        Text(
            text = remember(message) { AnnotatedString.fromHtml(message) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(ERROR_GAP))
        Row(horizontalArrangement = Arrangement.spacedBy(ERROR_GAP)) {
            OutlinedButton(onClick = rememberHapticOnClick(onRetry)) { Text(stringResource(R.string.timeline_retry)) }
            FilledTonalButton(onClick = rememberHapticOnClick(onChangeProvider)) {
                Text(stringResource(R.string.timeline_change_provider))
            }
        }
    }
}

private val ERROR_ICON_SIZE = 40.dp
private val ERROR_GAP = 12.dp
