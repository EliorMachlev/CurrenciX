package com.eliormachlev.currencix.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.view.compose.OverflowAction
import com.eliormachlev.currencix.view.compose.ScreenScaffold
import com.eliormachlev.currencix.view.compose.TopBarAction
import com.eliormachlev.currencix.view.compose.TopBarOverflowMenu
import com.eliormachlev.currencix.view.compose.TopBarStyle
import com.eliormachlev.currencix.view.timeline.TimelineTitle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The Material 3 top bars the pushed screens wear: medium (large title under
// the actions) and small (what it collapses into, and what short screens get).
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5)
class TopBarsScreenshotTest {
    @Test fun timelineBarMedium() = captureMatrix("top_bar_timeline_medium") { TimelineBar(TopBarStyle.Medium) }

    @Test fun timelineBarSmall() = captureMatrix("top_bar_timeline_small") { TimelineBar(TopBarStyle.Small) }

    @Test fun cartBarMedium() = captureMatrix("top_bar_cart_medium") { CartBar() }
}

@Composable
private fun TimelineBar(style: TopBarStyle) {
    ScreenScaffold(
        title = { TimelineTitle(Currency.EUR to Currency.USD) },
        onBack = {},
        style = style,
        actions = {
            TopBarAction(painterResource(R.drawable.ic_tune), "Graph options", onClick = {})
            TopBarAction(painterResource(R.drawable.ic_shuffle), "Swap", onClick = {})
        },
        modifier = Modifier.height(BAR_PREVIEW_HEIGHT),
    ) { padding -> BodyStub(Modifier.padding(padding)) }
}

@Composable
private fun CartBar() {
    ScreenScaffold(
        title = { Text("Shopping cart") },
        onBack = {},
        actions = {
            TopBarOverflowMenu(
                listOf(
                    OverflowAction("Share", Icons.Outlined.Share) {},
                    OverflowAction("Clear", Icons.Outlined.DeleteOutline, destructive = true, separated = true) {},
                ),
            )
        },
        modifier = Modifier.height(BAR_PREVIEW_HEIGHT),
    ) { padding -> BodyStub(Modifier.padding(padding)) }
}

// A strip of content under the bar, so the capture shows where the body starts.
@Composable
private fun BodyStub(modifier: Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(2) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {}
        }
    }
}

private val BAR_PREVIEW_HEIGHT = 260.dp
