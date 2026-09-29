package com.eliormachlev.currencix.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The Material 3 top bars the pushed screens wear: the timeline's small bar
// (pair beside the back arrow) and the cart's medium one.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5, application = Application::class)
class TopBarsScreenshotTest {
    @get:Rule val shots = ScreenshotRule()

    @Test fun timelineBar() = shots.captureMatrix("top_bar_timeline") { TimelineBar() }

    @Test fun cartBarMedium() = shots.captureMatrix("top_bar_cart_medium") { CartBar() }
}

@Composable
private fun TimelineBar() {
    ScreenScaffold(
        title = { TimelineTitle(Currency.USD to Currency.ILS) },
        onBack = {},
        style = TopBarStyle.Small,
        actions = {
            TopBarAction(painterResource(R.drawable.ic_tune), "Graph options", onClick = {})
            TopBarAction(painterResource(R.drawable.ic_swap_horiz), "Swap", onClick = {})
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
                    OverflowAction("Share", R.drawable.ic_share) {},
                    OverflowAction("Clear", R.drawable.ic_delete, destructive = true, separated = true) {},
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
