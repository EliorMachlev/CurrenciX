package com.eliormachlev.currencix.view.main.compose

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.view.compose.UiTestTags
import kotlinx.coroutines.launch

enum class DrawerAction(
    /** Whether the entry opens another screen (rather than a sheet, or an action on the converter). */
    val opensScreen: Boolean = false,
) {
    Timeline(opensScreen = true),
    Cart(opensScreen = true),
    QuickConversions,
    DatePicker,
    Refresh,
    Share,
    ChangeApi,
    Fees(opensScreen = true),
    Settings(opensScreen = true),
}

private val DrawerOuterPadding = 12.dp
private val DrawerGroupGap = 16.dp
private const val DRAWER_DISABLED_ALPHA = 0.38f

// Status shown inside the RateFooter:
//  - OFFLINE: device has no network
//  - UNREACHABLE: online but the rate provider's endpoint failed (5xx,
//    timeout, DNS, etc.) — user should know rates are stale even though
//    the phone is technically online
//  - FALLBACK: the main provider failed; rates came from the fallback one
//  - HISTORICAL: user pinned a past date via the date picker
// Ranking when several apply: Offline > Unreachable > Fallback > Historical,
// since each condition subsumes the "the rates aren't fresh" signal of the next.
enum class BannerKind { Offline, Unreachable, Fallback, Historical }

data class BannerContent(
    val kind: BannerKind,
    val text: String,
)

private data class DrawerEntry(
    val action: DrawerAction,
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val titleRes: Int,
)

private val PrimaryDrawerEntries =
    listOf(
        DrawerEntry(DrawerAction.Timeline, R.drawable.ic_timeline, R.string.menu_timeline),
        DrawerEntry(DrawerAction.Cart, R.drawable.ic_cart, R.string.cart_title),
        DrawerEntry(DrawerAction.QuickConversions, R.drawable.ic_table, R.string.menu_quick_conversions),
        DrawerEntry(DrawerAction.DatePicker, R.drawable.ic_history, R.string.menu_historical_rates),
        DrawerEntry(DrawerAction.Refresh, R.drawable.ic_refresh, R.string.menu_refresh),
        DrawerEntry(DrawerAction.Share, R.drawable.ic_share, R.string.menu_share),
    )

private val SecondaryDrawerEntries =
    listOf(
        DrawerEntry(DrawerAction.ChangeApi, R.drawable.ic_data_provider, R.string.api_title),
        DrawerEntry(DrawerAction.Fees, R.drawable.ic_fee, R.string.fee_manager_title),
        DrawerEntry(DrawerAction.Settings, R.drawable.ic_settings, R.string.menu_settings),
    )

/** The navigation drawer: whether it's open, and what picking an entry does. */
class DrawerControl(
    val state: DrawerState,
    val onItem: (DrawerAction) -> Unit,
)

/**
 * How the space under the top bar behaves: pull-to-refresh, and how the
 * display and the keypad share it.
 */
class ConverterBody(
    val isRefreshing: Boolean,
    val onRefresh: () -> Unit,
    val foldingFeature: FoldingFeature?,
    val keypadHeights: KeypadHeights,
)

// The converter screen: [topBar] over the pull-to-refresh hero display and
// the keypad (side by side in landscape / on a vertical fold), with a
// ModalNavigationDrawer under the top bar. Content slots are hoisted so
// ConverterRoute keeps direct control over the hero display + keypad
// composables (which own their own ViewModel wiring). Offline / historical
// status is rendered inside the hero's RateFooter instead of stealing a
// full-width strip above the card.
@Composable
fun MainScreen(
    drawer: DrawerControl,
    body: ConverterBody,
    topBar: @Composable () -> Unit,
    displayContent: @Composable () -> Unit,
    keypadContent: @Composable () -> Unit,
) {
    // Back closes the drawer instead of leaving the app. A plain back
    // handler rather than the sheet's own (ModalDrawerSheet(drawerState)),
    // which previews the gesture by shrinking the sheet under the finger.
    val scope = rememberCoroutineScope()
    BackHandler(enabled = drawer.state.targetValue == DrawerValue.Open) { scope.launch { drawer.state.close() } }
    // The drawer slides in under the top bar, not over it: the hamburger
    // morphs into an arrow as it opens (DrawerArrowIcon), and that arrow is
    // the way to close it again, so it has to stay in sight and in reach.
    Scaffold(topBar = topBar, containerColor = Color.Transparent) { padding ->
        ModalNavigationDrawer(
            drawerState = drawer.state,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            drawerContent = {
                ModalDrawerSheet {
                    // Nothing to refresh while a refresh is already running.
                    DrawerContent(onItemClick = drawer.onItem, isRefreshEnabled = !body.isRefreshing)
                }
            },
        ) {
            MainContent(
                modifier = Modifier,
                body = body,
                displayContent = displayContent,
                keypadContent = keypadContent,
            )
        }
    }
}

@Composable
private fun MainContent(
    modifier: Modifier,
    body: ConverterBody,
    displayContent: @Composable () -> Unit,
    keypadContent: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val available = maxHeight
        if (shouldUseHorizontal(maxWidth >= maxHeight, body.foldingFeature)) {
            Row(Modifier.fillMaxSize()) {
                DisplayArea(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    minContentHeight = available,
                    body = body,
                    displayContent = displayContent,
                )
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) { keypadContent() }
            }
        } else {
            // The display is as tall as its content, within what the keypad
            // can give: at least what a full-size keypad leaves (so short
            // content still fills the space above it), at most what a
            // compact one does. The keypad takes the rest, so its rows give
            // up height before the display has to scroll.
            val leastDisplay = (available - body.keypadHeights.full).coerceAtLeast(0.dp)
            val mostDisplay = (available - body.keypadHeights.compact).coerceAtLeast(0.dp)
            Column(Modifier.fillMaxSize()) {
                DisplayArea(
                    modifier = Modifier.fillMaxWidth().heightIn(max = mostDisplay),
                    minContentHeight = leastDisplay,
                    body = body,
                    displayContent = displayContent,
                )
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) { keypadContent() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisplayArea(
    modifier: Modifier,
    minContentHeight: Dp,
    body: ConverterBody,
    displayContent: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = body.isRefreshing,
        onRefresh = body.onRefresh,
        state = state,
        modifier = modifier,
    ) {
        // PullToRefreshBox routes its drag detection through a
        // NestedScrollConnection, so it only fires when the child dispatches
        // vertical scroll. MainDisplay is otherwise a static composition —
        // wrap it in a verticalScroll, at least [minContentHeight] tall so
        // short content still takes the pull over the whole area.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = minContentHeight),
        ) {
            displayContent()
        }
    }
}

// Taller than wide → Column; wider → Row. Measured on the space the
// converter gets, not the screen: beside the timeline on a tablet it's a
// narrow pane even in landscape. A folded-open device with a VERTICAL hinge
// (screen split left/right) always forces the Row layout so hero and keypad
// end up on separate halves — matching the previous XML foldable code.
private fun shouldUseHorizontal(
    isLandscapeSpace: Boolean,
    feature: FoldingFeature?,
): Boolean =
    when {
        feature == null || feature.state == FoldingFeature.State.FLAT -> isLandscapeSpace
        feature.orientation == FoldingFeature.Orientation.VERTICAL -> true
        else -> false
    }

@Composable
private fun DrawerContent(
    onItemClick: (DrawerAction) -> Unit,
    isRefreshEnabled: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(vertical = DrawerOuterPadding),
    ) {
        PrimaryDrawerEntries.forEach { entry ->
            DrawerRow(
                entry = entry,
                enabled = entry.action != DrawerAction.Refresh || isRefreshEnabled,
                onClick = { onItemClick(entry.action) },
            )
        }
        Spacer(Modifier.height(DrawerGroupGap))
        SecondaryDrawerEntries.forEach { entry ->
            DrawerRow(
                entry = entry,
                enabled = true,
                onClick = { onItemClick(entry.action) },
            )
        }
    }
}

// M3 nav drawer pills — no hairline between rows, rounded selectable
// container, ripple + tonal focus wired via NavigationDrawerItem. `selected`
// stays false (this drawer is action-oriented, no active destination) so
// every row uses the same neutral surface.
@Composable
private fun DrawerRow(
    entry: DrawerEntry,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(entry.titleRes)
    val alpha = if (enabled) 1f else DRAWER_DISABLED_ALPHA
    NavigationDrawerItem(
        selected = false,
        onClick = { if (enabled) onClick() },
        modifier =
            Modifier
                .padding(NavigationDrawerItemDefaults.ItemPadding)
                .testTag(UiTestTags.drawerEntry(entry.action.name)),
        icon = {
            Icon(
                painter = painterResource(id = entry.iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            )
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
        },
    )
}
