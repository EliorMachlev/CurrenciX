package com.eliormachlev.currencix.view.main.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.view.compose.CurrencyChip
import com.eliormachlev.currencix.view.compose.CurrencyChipGap
import com.eliormachlev.currencix.view.compose.Ltr
import com.eliormachlev.currencix.view.compose.PairChipContent
import com.eliormachlev.currencix.view.compose.ReadingDirection
import com.eliormachlev.currencix.view.compose.RemoveFromHistorySheet
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * One-tap switches to the pairs used most recently — "🇺🇸 USD → 🇮🇱 ILS"
 * chips under the hero card. [pairs] excludes the pair on screen; nothing
 * is drawn when it's empty (and there's no [onPaste]). A long-press asks
 * whether to forget the pair ([onRemove]); with [onAlert], it opens a menu
 * first: a rate alert for the pair, or removing it. With [onPaste], a "Paste
 * amount" chip leads the row. A pair in [alerted] has a rate alert waiting:
 * its chip carries a small bell, which rings once when the alert is new.
 */
@Composable
fun RecentPairsRow(
    pairs: ImmutableList<CurrencyPair>,
    onPick: (CurrencyPair) -> Unit,
    onRemove: (CurrencyPair) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onPaste: (() -> Unit)? = null,
    onAlert: ((CurrencyPair) -> Unit)? = null,
    alerted: ImmutableSet<CurrencyPair> = persistentSetOf(),
    // Shorter chips, while the converter's status pill takes room above them.
    compact: Boolean = false,
) {
    var removing by remember { mutableStateOf<CurrencyPair?>(null) }
    removing?.let { pair ->
        RemoveFromHistorySheet(
            message = stringResource(R.string.recent_remove_pair, pair.from.iso4217Alpha(), pair.to.iso4217Alpha()),
            onConfirm = {
                onRemove(pair)
                removing = null
            },
            onDismiss = { removing = null },
        ) { PairChipContent(pair.from, pair.to) }
    }
    if (pairs.isEmpty() && onPaste == null) return
    val longPressLabel = stringResource(if (onAlert == null) R.string.recent_remove_title else R.string.recent_pair_options)
    var menuFor by remember { mutableStateOf<CurrencyPair?>(null) }
    // The newest pair goes in at the front. Left alone, the row holds its
    // place on the chip that was first, leaving the new one off-screen.
    val listState = rememberLazyListState()
    // Bells that have rung (or were there from the start) don't ring again;
    // a pair whose alert goes may ring when it's set anew.
    val quiet = remember { alerted.toMutableSet() }
    LaunchedEffect(alerted) { quiet.retainAll(alerted) }
    LaunchedEffect(pairs.firstOrNull(), onPaste != null) { listState.scrollToItem(0) }
    // Pairs read from → to, left to right, in every language, as on the
    // converter, wherever the row sits: in a sheet that follows Hebrew's
    // direction (Quick conversions) it would otherwise mirror.
    Ltr {
        LazyRow(
            modifier = modifier,
            state = listState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(CurrencyChipGap),
        ) {
            onPaste?.let { paste ->
                item(key = PASTE_KEY) { PasteChip(onClick = paste, compact = compact, modifier = Modifier.animateItem()) }
            }
            items(pairs, key = { "${it.from}:${it.to}" }) { pair ->
                Box(Modifier.animateItem()) {
                    val hasAlert = pair in alerted
                    val switchTo = stringResource(R.string.recent_pair_switch, pair.from.iso4217Alpha(), pair.to.iso4217Alpha())
                    val alertSet = stringResource(R.string.recent_pair_alert_set)
                    CurrencyChip(
                        description = if (hasAlert) "$switchTo. $alertSet" else switchTo,
                        onClick = { onPick(pair) },
                        onLongClick = { if (onAlert == null) removing = pair else menuFor = pair },
                        onLongClickLabel = longPressLabel,
                        compact = compact,
                    ) {
                        PairChipContent(pair.from, pair.to)
                        if (hasAlert) AlertBell(ring = pair !in quiet, onRung = { quiet += pair })
                    }
                    if (onAlert != null) {
                        // Words: the menu follows the language again.
                        ReadingDirection {
                            PairMenu(
                                expanded = menuFor == pair,
                                onAlert = { onAlert(pair) },
                                onRemove = { removing = pair },
                                onDismiss = { menuFor = null },
                            )
                        }
                    }
                }
            }
        }
    }
}

// Distinct from the pairs' "USD:EUR" keys.
private const val PASTE_KEY = "action:paste"

// "📋 Paste amount": the copied price, typed into the converter.
@Composable
private fun PasteChip(
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.paste_amount)
    CurrencyChip(description = label, onClick = onClick, modifier = modifier, compact = compact) {
        // Words, not a pair: the icon leads in the language's direction.
        ReadingDirection {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PASTE_ICON_GAP)) {
                Icon(
                    painter = painterResource(R.drawable.ic_content_paste),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(PASTE_ICON_SIZE),
                )
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private val PASTE_ICON_SIZE = 18.dp
private val PASTE_ICON_GAP = 6.dp

// A recent pair's long-press: a rate alert for it, or forget it (which still asks).
@Composable
private fun PairMenu(
    expanded: Boolean,
    onAlert: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        listOf(
            Triple(R.string.alert_menu, R.drawable.ic_alert, onAlert),
            Triple(R.string.recent_remove_title, R.drawable.ic_delete, onRemove),
        ).forEach { (label, icon, action) ->
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
                onClick = {
                    onDismiss()
                    action()
                },
            )
        }
    }
}

// The small bell on a pair with a rate alert waiting; with [ring], it swings
// once as it appears ([onRung] after).
@Composable
private fun AlertBell(
    ring: Boolean,
    onRung: () -> Unit,
) {
    val swing = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!ring) return@LaunchedEffect
        swing.animateTo(
            0f,
            keyframes {
                durationMillis = BELL_RING_MILLIS
                BELL_SWINGS.forEachIndexed { index, degrees -> degrees at (index + 1) * BELL_RING_MILLIS / (BELL_SWINGS.size + 1) }
            },
        )
        onRung()
    }
    Icon(
        painter = painterResource(R.drawable.ic_alert_active),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier =
            Modifier
                .padding(start = BELL_GAP)
                .size(BELL_SIZE)
                .graphicsLayer {
                    // Swings from the top, where a bell hangs.
                    transformOrigin = BELL_HANG
                    rotationZ = swing.value
                },
    )
}

private const val BELL_RING_MILLIS = 700

// The middle of the bell's top edge.
private val BELL_HANG = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0f)

// Each swing smaller than the last, settling back at rest.
private val BELL_SWINGS = listOf(18f, -14f, 10f, -6f, 3f)
private val BELL_SIZE = 14.dp
private val BELL_GAP = 4.dp
