package com.eliormachlev.currencix.view.main.compose

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.view.compose.rememberActionBarTopPadding

enum class DrawerAction {
    Timeline,
    Cart,
    QuickConversions,
    DatePicker,
    Refresh,
    Share,
    ChangeApi,
    Fees,
    Settings,
}

private val StatusBannerMargin = 8.dp
private val StatusBannerPadding = 12.dp
private val StatusBannerIconSpacing = 12.dp
private val DrawerItemPadding = 12.dp
private val DrawerContentPadding = NavigationDrawerItemDefaults.ItemPadding

// Two-state banner surfaced above the hero: OFFLINE (device has no network)
// or HISTORICAL (user pinned a past date via the date picker). Offline wins
// when both are true, since stale/cached is the more actionable signal.
enum class BannerKind { Offline, Historical }

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

// Compose replacement for the old activity_main.xml tree — hosts the offline
// banner, pull-to-refresh, and side-by-side display/keypad, all wrapped by a
// ModalNavigationDrawer. Content slots are hoisted so MainActivity keeps
// direct control over the hero display + keypad composables (which own their
// own ViewModel wiring).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    drawerState: DrawerState,
    banner: BannerContent?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    isRefreshDrawerEnabled: Boolean,
    onDrawerItem: (DrawerAction) -> Unit,
    foldingFeature: FoldingFeature?,
    displayContent: @Composable () -> Unit,
    keypadContent: @Composable () -> Unit,
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    onItemClick = onDrawerItem,
                    isRefreshEnabled = isRefreshDrawerEnabled,
                )
            }
        },
    ) {
        MainContent(
            banner = banner,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            foldingFeature = foldingFeature,
            displayContent = displayContent,
            keypadContent = keypadContent,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(
    banner: BannerContent?,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    foldingFeature: FoldingFeature?,
    displayContent: @Composable () -> Unit,
    keypadContent: @Composable () -> Unit,
) {
    val rootModifier = Modifier.fillMaxSize().padding(top = rememberActionBarTopPadding())
    if (shouldUseHorizontal(foldingFeature)) {
        Row(modifier = rootModifier) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                StatusBannerSlot(banner)
                DisplayArea(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    displayContent = displayContent,
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) { keypadContent() }
        }
    } else {
        Column(modifier = rootModifier) {
            StatusBannerSlot(banner)
            DisplayArea(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                displayContent = displayContent,
            )
            Box(modifier = Modifier.fillMaxWidth()) { keypadContent() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisplayArea(
    modifier: Modifier,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    displayContent: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
    ) {
        displayContent()
    }
}

// Portrait → Column; landscape → Row. A folded-open device with a VERTICAL
// hinge (screen split left/right) always forces the Row layout so hero and
// keypad end up on separate halves — matching the previous XML foldable code.
@Composable
private fun shouldUseHorizontal(feature: FoldingFeature?): Boolean {
    val cfg = LocalConfiguration.current
    val naturalHorizontal = cfg.screenWidthDp >= cfg.screenHeightDp
    if (feature == null) return naturalHorizontal
    return when {
        feature.state == FoldingFeature.State.FLAT -> naturalHorizontal
        feature.orientation == FoldingFeature.Orientation.VERTICAL -> true
        else -> false
    }
}

@Composable
private fun StatusBannerSlot(banner: BannerContent?) {
    // Remember the last non-null banner so the outgoing card keeps its label
    // and colors through the fade-out even after state flips to null.
    var lastShown by remember { mutableStateOf<BannerContent?>(null) }
    if (banner != null) lastShown = banner
    AnimatedVisibility(visible = banner != null) {
        lastShown?.let { StatusBanner(banner = it) }
    }
}

@Composable
private fun StatusBanner(banner: BannerContent) {
    val containerColor =
        when (banner.kind) {
            BannerKind.Offline -> MaterialTheme.colorScheme.errorContainer
            BannerKind.Historical -> MaterialTheme.colorScheme.secondaryContainer
        }
    val contentColor =
        when (banner.kind) {
            BannerKind.Offline -> MaterialTheme.colorScheme.onErrorContainer
            BannerKind.Historical -> MaterialTheme.colorScheme.onSecondaryContainer
        }
    val iconRes =
        when (banner.kind) {
            BannerKind.Offline -> R.drawable.ic_cloud_off
            BannerKind.Historical -> R.drawable.ic_history
        }
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(StatusBannerMargin),
        colors =
            CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(StatusBannerPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StatusBannerIconSpacing),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
            )
            Text(
                text = banner.text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
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
                .padding(vertical = DrawerItemPadding),
    ) {
        PrimaryDrawerEntries.forEach { entry ->
            DrawerRow(
                entry = entry,
                enabled = entry.action != DrawerAction.Refresh || isRefreshEnabled,
                onClick = { onItemClick(entry.action) },
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = DrawerItemPadding / 2),
        )
        SecondaryDrawerEntries.forEach { entry ->
            DrawerRow(
                entry = entry,
                enabled = true,
                onClick = { onItemClick(entry.action) },
            )
        }
    }
}

@Composable
private fun DrawerRow(
    entry: DrawerEntry,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(entry.titleRes)
    NavigationDrawerItem(
        icon = {
            Icon(
                painter = painterResource(entry.iconRes),
                contentDescription = null,
            )
        },
        label = { Text(label) },
        selected = false,
        onClick = { if (enabled) onClick() },
        modifier = Modifier.padding(DrawerContentPadding),
    )
}
