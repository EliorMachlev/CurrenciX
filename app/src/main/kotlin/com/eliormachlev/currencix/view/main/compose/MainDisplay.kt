package com.eliormachlev.currencix.view.main.compose

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

// FAB is ~56dp — half-height offsets the swap button so its centre lands on
// the top/bottom half boundary, mirroring the ConstraintLayout guideline at
// 45% Y from the old main_display.xml.
private val FAB_HALF_HEIGHT: Dp = 28.dp

// Rounded-corner "paper card" that frames the conversion display. Matches the
// 16dp radius from the old bg_display drawable.
private val CARD_RADIUS: Dp = 16.dp

private val CARD_MARGIN: Dp = 8.dp
private val CARD_PADDING: Dp = 8.dp
private val FAB_START_MARGIN: Dp = 40.dp

// Matches `android:padding="@dimen/margin2x"` on textFrom/textTo — the
// currency-value glyphs sit this far inside their scroll-view wrapper.
private val CURRENCY_TEXT_PADDING: Dp = 16.dp

// Anchor the info footer's right edge to the same X as the currency values:
// `CARD_PADDING` matches the horizontal padding BottomHalf adds around its
// content, `CURRENCY_TEXT_PADDING` matches the textview's own end padding.
private val FOOTER_END_PADDING: Dp = CARD_PADDING + CURRENCY_TEXT_PADDING
private val FOOTER_BOTTOM_PADDING: Dp = 8.dp

// The XML guideline sat at 45%; the top half hosts the "from" side and the
// bottom half is slightly taller to fit the rate footer at the bottom.
private const val TOP_HALF_WEIGHT: Float = 0.9f
private const val BOTTOM_HALF_WEIGHT: Float = 1.1f
private const val GUIDELINE_FRACTION: Float = TOP_HALF_WEIGHT / (TOP_HALF_WEIGHT + BOTTOM_HALF_WEIGHT)

/**
 * Holds every legacy Android view that participates in the main display. All
 * widgets are inflated (and detached from a throw-away ConstraintLayout parent)
 * inside `MainActivity.onCreate` so [MainDisplay] can host them via
 * `AndroidView` without racing the ComposeView's first composition — the
 * activity holds strong references, so `findViewById` on the activity's window
 * decor still resolves them once composition attaches them to the tree.
 */
internal class MainDisplayViews(
    val snackbarHost: View,
    val refreshIndicator: View,
    val btnToggle: View,
    val spinnerFrom: View,
    val spinnerTo: View,
    val textCalculations: View,
    val scrollViewTextFrom: View,
    val scrollViewTextTo: View,
    val scrollViewOriginalFeeAmount: View,
    val scrollViewTrueCost: View,
    val scrollViewOriginalValue: View,
    val scrollViewConvertedFeeAmount: View,
    val iconHistorical: View,
    val textInfoConversion: View,
    val textInfoDate: View,
)

/**
 * Compose replacement for the old `main_display.xml`. Owns the layout skeleton
 * (paper card frame, top/bottom halves, info-footer positioning) while every
 * interactive or LiveData-driven widget is hosted as an `AndroidView`. A later
 * phase will migrate the display-only fields to pure Compose Text observing
 * MainViewModel state; keeping them as views for now leaves MainActivity's
 * observers untouched.
 */
@Composable
internal fun MainDisplay(
    views: MainDisplayViews,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .padding(CARD_MARGIN)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(CARD_RADIUS)),
    ) {
        val guidelineY = maxHeight * GUIDELINE_FRACTION

        Column(Modifier.fillMaxSize()) {
            TopHalf(views, Modifier.fillMaxWidth().weight(TOP_HALF_WEIGHT).padding(horizontal = CARD_PADDING))
            BottomHalf(views, Modifier.fillMaxWidth().weight(BOTTOM_HALF_WEIGHT).padding(horizontal = CARD_PADDING))
        }

        // Swap FAB straddles the top/bottom boundary at the start edge.
        AndroidView(
            factory = { views.btnToggle },
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = FAB_START_MARGIN)
                    .offset(y = guidelineY - FAB_HALF_HEIGHT),
        )

        // Refresh indicator sits at the very top of the card, snackbars above.
        AndroidView(
            factory = { views.refreshIndicator },
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
        )
        AndroidView(
            factory = { views.snackbarHost },
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
        )

        // Anchored to the card's absolute bottom-right so its text right edge
        // lines up with the currency-value text right edge (same X).
        InfoFooter(
            views,
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = FOOTER_END_PADDING, bottom = FOOTER_BOTTOM_PADDING),
        )
    }
}

@Composable
private fun TopHalf(
    views: MainDisplayViews,
    modifier: Modifier,
) {
    Box(modifier) {
        AndroidView(
            factory = { views.textCalculations },
            modifier = Modifier.align(Alignment.TopEnd),
        )
        Row(
            Modifier.fillMaxWidth().align(Alignment.BottomStart).wrapContentHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AndroidView(factory = { views.spinnerFrom })
            AndroidView(
                factory = { views.scrollViewTextFrom },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomHalf(
    views: MainDisplayViews,
    modifier: Modifier,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.Top,
    ) {
        AndroidView(
            factory = { views.scrollViewOriginalValue },
            modifier = Modifier.align(Alignment.End),
        )
        AndroidView(
            factory = { views.scrollViewConvertedFeeAmount },
            modifier = Modifier.align(Alignment.End),
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AndroidView(factory = { views.spinnerTo })
            AndroidView(
                factory = { views.scrollViewTextTo },
                modifier = Modifier.weight(1f),
            )
        }
        AndroidView(
            factory = { views.scrollViewOriginalFeeAmount },
            modifier = Modifier.align(Alignment.End),
        )
        AndroidView(
            factory = { views.scrollViewTrueCost },
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun InfoFooter(
    views: MainDisplayViews,
    modifier: Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        AndroidView(factory = { views.textInfoConversion })
        Row(verticalAlignment = Alignment.Bottom) {
            AndroidView(factory = { views.iconHistorical })
            AndroidView(factory = { views.textInfoDate })
        }
    }
}
