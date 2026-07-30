package de.salomax.currencies.view.timeline.compose

import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import de.salomax.currencies.util.fromHtmlLegacy

private val CHART_PADDING = 16.dp
private val PROVIDER_FONT_SIZE = 12.sp
private const val PROVIDER_ALPHA = 0.5f

@Composable
@Suppress("LongParameterList")
internal fun TimelineChartCard(
    isRefreshing: Boolean,
    error: String?,
    provider: CharSequence?,
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
            // AndroidView keeps the exact HTML rendering (bold spans etc.) that
            // fromHtmlLegacy produces — cheaper than porting the parser.
            AndroidView(
                factory = { ctx ->
                    TextView(ctx).apply {
                        typeface = android.graphics.Typeface.MONOSPACE
                        setTextColor(android.graphics.Color.parseColor("#FF6060"))
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }
                },
                update = { it.text = error.fromHtmlLegacy() },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(CHART_PADDING)
                        .align(Alignment.Center),
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
