package com.eliormachlev.currencix.view.main.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.view.compose.CurrencyChip
import com.eliormachlev.currencix.view.compose.CurrencyChipGap
import com.eliormachlev.currencix.view.compose.FlagCode
import com.eliormachlev.currencix.view.compose.RemoveFromHistoryDialog
import kotlinx.collections.immutable.ImmutableList

private val ARROW_SIZE = 14.dp

/**
 * One-tap switches to the pairs used most recently — "🇺🇸 USD → 🇮🇱 ILS"
 * chips under the hero card. [pairs] excludes the pair on screen; nothing
 * is drawn when it's empty. A long-press asks whether to forget the pair
 * ([onRemove]).
 */
@Composable
fun RecentPairsRow(
    pairs: ImmutableList<CurrencyPair>,
    onPick: (CurrencyPair) -> Unit,
    onRemove: (CurrencyPair) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var removing by remember { mutableStateOf<CurrencyPair?>(null) }
    removing?.let { pair ->
        RemoveFromHistoryDialog(
            message = stringResource(R.string.recent_remove_pair, pair.from.iso4217Alpha(), pair.to.iso4217Alpha()),
            onConfirm = {
                onRemove(pair)
                removing = null
            },
            onDismiss = { removing = null },
        )
    }
    if (pairs.isEmpty()) return
    val removeLabel = stringResource(R.string.recent_remove_title)
    // The newest pair goes in at the front. Left alone, the row holds its
    // place on the chip that was first, leaving the new one off-screen.
    val listState = rememberLazyListState()
    LaunchedEffect(pairs.first()) { listState.scrollToItem(0) }
    LazyRow(
        modifier = modifier,
        state = listState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(CurrencyChipGap),
    ) {
        items(pairs, key = { "${it.from}:${it.to}" }) { pair ->
            CurrencyChip(
                description = stringResource(R.string.recent_pair_switch, pair.from.iso4217Alpha(), pair.to.iso4217Alpha()),
                onClick = { onPick(pair) },
                modifier = Modifier.animateItem(),
                onLongClick = { removing = pair },
                onLongClickLabel = removeLabel,
            ) {
                FlagCode(pair.from)
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(ARROW_SIZE),
                )
                FlagCode(pair.to)
            }
        }
    }
}
