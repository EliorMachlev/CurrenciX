package com.eliormachlev.currencix.view.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.navigation.LocalScreenBackground

// Material's compact window-height class ends here.
private val COMPACT_HEIGHT = 480.dp

/**
 * Top bar size. [Medium] carries a large title under the actions and
 * collapses into [Small] as the content scrolls up under it — the pushed
 * screens (timeline, cart, settings) use it; the converter stays [Small].
 * On a short screen (a phone in landscape) every bar is [Small].
 */
enum class TopBarStyle { Small, Medium }

/** One entry in a top bar's overflow menu. */
@Immutable
data class OverflowAction(
    val label: String,
    @param:DrawableRes val icon: Int,
    val destructive: Boolean = false,
    // Draws a divider above this entry, to set a destructive action apart.
    val separated: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Material 3 frame for every pushed screen: a top bar with a back arrow,
 * [title] and [actions], over [content]. The bar shares the screen's
 * background until content scrolls beneath it, then takes the raised
 * surface tone. [content] gets the padding that keeps it clear of the bar
 * and the system bars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList")
fun ScreenScaffold(
    title: @Composable () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    style: TopBarStyle = TopBarStyle.Medium,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // A large title would eat a landscape phone's short screen: below the
    // Material "compact height" line, every bar is the small one.
    val windowHeight =
        with(LocalDensity.current) {
            LocalWindowInfo.current.containerSize.height
                .toDp()
        }
    val compactHeight = windowHeight < COMPACT_HEIGHT
    val barStyle = if (compactHeight) TopBarStyle.Small else style
    val scrollBehavior =
        when (barStyle) {
            TopBarStyle.Medium -> TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            TopBarStyle.Small -> TopAppBarDefaults.pinnedScrollBehavior()
        }
    val navigationIcon: @Composable () -> Unit = { BackButton(onBack) }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // The screen frame already paints the window background.
        containerColor = Color.Transparent,
        topBar = {
            when (barStyle) {
                TopBarStyle.Medium ->
                    MediumTopAppBar(
                        title = title,
                        navigationIcon = navigationIcon,
                        actions = actions,
                        colors = screenTopBarColors(),
                        scrollBehavior = scrollBehavior,
                    )
                TopBarStyle.Small ->
                    TopAppBar(
                        title = title,
                        navigationIcon = navigationIcon,
                        actions = actions,
                        colors = screenTopBarColors(),
                        scrollBehavior = scrollBehavior,
                    )
            }
        },
        content = content,
    )
}

/** Top bar colors shared by every screen, the converter's included. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun screenTopBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(
        containerColor = LocalScreenBackground.current.takeOrElse(MaterialTheme.colorScheme.background),
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    )

private fun Color.takeOrElse(fallback: Color): Color = if (this == Color.Unspecified) fallback else this

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = rememberHapticOnClick(onBack)) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = stringResource(R.string.desc_navigate_up),
        )
    }
}

/** An action icon in a top bar. */
@Composable
fun TopBarAction(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(onClick = rememberHapticOnClick(onClick), modifier = modifier, enabled = enabled) {
        Icon(painter = icon, contentDescription = contentDescription)
    }
}

/**
 * The ⋮ button and its Material 3 menu. Each entry closes the menu and then
 * runs its action; a [OverflowAction.destructive] entry is drawn in the
 * error color.
 */
@Composable
fun TopBarOverflowMenu(
    items: List<OverflowAction>,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val open = rememberHapticOnClick { expanded = true }
    IconButton(onClick = open, modifier = modifier) {
        Icon(painter = painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.desc_more_options))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        items.forEach { item ->
            if (item.separated) HorizontalDivider()
            val tint = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            val onClick =
                remember(item) {
                    {
                        expanded = false
                        item.onClick()
                    }
                }
            DropdownMenuItem(
                text = {
                    Text(
                        text = item.label,
                        color = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                },
                leadingIcon = { Icon(painter = painterResource(item.icon), contentDescription = null, tint = tint) },
                onClick = rememberHapticOnClick(onClick),
            )
        }
    }
}
