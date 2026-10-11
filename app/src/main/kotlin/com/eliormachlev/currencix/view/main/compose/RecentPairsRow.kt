package com.eliormachlev.currencix.view.main.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.view.compose.CurrencyChip
import com.eliormachlev.currencix.view.compose.CurrencyChipGap
import com.eliormachlev.currencix.view.compose.PairChipContent
import com.eliormachlev.currencix.view.compose.ReadingDirection
import com.eliormachlev.currencix.view.compose.RemoveFromHistorySheet
import kotlinx.collections.immutable.ImmutableList

/**
 * One-tap switches to the pairs used most recently — "🇺🇸 USD → 🇮🇱 ILS"
 * chips under the hero card. [pairs] excludes the pair on screen; nothing
 * is drawn when it's empty (and there's no [onPaste]). A long-press asks
 * whether to forget the pair ([onRemove]). With [onPaste], a "Paste amount"
 * chip leads the row.
 */
@Composable
fun RecentPairsRow(
    pairs: ImmutableList<CurrencyPair>,
    onPick: (CurrencyPair) -> Unit,
    onRemove: (CurrencyPair) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onPaste: (() -> Unit)? = null,
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
    val removeLabel = stringResource(R.string.recent_remove_title)
    // The newest pair goes in at the front. Left alone, the row holds its
    // place on the chip that was first, leaving the new one off-screen.
    val listState = rememberLazyListState()
    LaunchedEffect(pairs.firstOrNull(), onPaste != null) { listState.scrollToItem(0) }
    LazyRow(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(CurrencyChipGap),
    ) {
        onPaste?.let { paste -> item(key = PASTE_KEY) { PasteChip(onClick = paste, modifier = Modifier.animateItem()) } }
        items(pairs, key = { "${it.from}:${it.to}" }) { pair ->
            CurrencyChip(
                description = stringResource(R.string.recent_pair_switch, pair.from.iso4217Alpha(), pair.to.iso4217Alpha()),
                onClick = { onPick(pair) },
                modifier = Modifier.animateItem(),
                onLongClick = { removing = pair },
                onLongClickLabel = removeLabel,
            ) {
                PairChipContent(pair.from, pair.to)
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
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.paste_amount)
    CurrencyChip(description = label, onClick = onClick, modifier = modifier) {
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
