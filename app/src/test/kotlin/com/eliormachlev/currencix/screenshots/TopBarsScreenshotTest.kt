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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.view.cart.CartBarActions
import com.eliormachlev.currencix.view.compose.CurrencyPairRow
import com.eliormachlev.currencix.view.compose.OverflowAction
import com.eliormachlev.currencix.view.compose.PairRowActions
import com.eliormachlev.currencix.view.compose.ReadingDirection
import com.eliormachlev.currencix.view.compose.ScreenScaffold
import com.eliormachlev.currencix.view.compose.TopBarOverflowMenu
import com.eliormachlev.currencix.view.compose.TopBarStyle
import com.eliormachlev.currencix.view.compose.theme.Spacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The Material 3 top bars the pushed screens wear: the timeline's small bar
// (its pair's pills under it) and the cart's medium one.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5, application = Application::class)
class TopBarsScreenshotTest {
    @get:Rule val shots = ScreenshotRule()

    @Test fun timelineBar() = shots.captureMatrix("top_bar_timeline") { TimelineBar() }

    @Test fun cartBarMedium() = shots.captureMatrix("top_bar_cart_medium") { CartBar() }
}

// Laid out the way the language reads, as TimelineRoute does.
@Composable
private fun TimelineBar() = ReadingDirection { TimelineBarContent() }

@Composable
private fun TimelineBarContent() {
    ScreenScaffold(
        title = { Text(stringResource(R.string.menu_timeline)) },
        onBack = {},
        style = TopBarStyle.Small,
        actions = {
            // Rate alert, share and graph options, as TimelineBarActions has them.
            TopBarOverflowMenu(
                listOf(
                    OverflowAction("Rate alert", R.drawable.ic_alert) {
                    },
                    OverflowAction("Share", R.drawable.ic_share) {},
                    OverflowAction("Graph options", R.drawable.ic_tune) {},
                ),
            )
        },
        modifier = Modifier.height(BAR_PREVIEW_HEIGHT),
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // The pair under the bar, as the converter shows it.
            CurrencyPairRow(
                from = Currency.USD,
                to = Currency.ILS,
                actions = PairRowActions({}, {}, {}, {}),
                modifier = Modifier.padding(horizontal = Spacing.Large, vertical = Spacing.Small),
            )
            BodyStub(Modifier)
        }
    }
}

// Laid out the way the language reads, as CartRoute does.
@Composable
private fun CartBar() = ReadingDirection { CartBarContent() }

@Composable
private fun CartBarContent() {
    ScreenScaffold(
        title = { Text(stringResource(R.string.cart_title)) },
        onBack = {},
        actions = {
            CartBarActions(
                onSave = {},
                menu =
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
