package com.eliormachlev.currencix.view.main.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.view.compose.CurrencyChip
import com.eliormachlev.currencix.view.compose.CurrencyChipGap
import com.eliormachlev.currencix.view.compose.FlagCode
import kotlinx.collections.immutable.ImmutableList

private val ARROW_SIZE = 14.dp

/**
 * One-tap switches to the pairs used most recently — "🇺🇸 USD → 🇮🇱 ILS"
 * chips under the hero card. [pairs] excludes the pair on screen; nothing
 * is drawn when it's empty.
 */
@Composable
fun RecentPairsRow(
    pairs: ImmutableList<CurrencyPair>,
    onPick: (CurrencyPair) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    if (pairs.isEmpty()) return
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(CurrencyChipGap),
    ) {
        items(pairs, key = { "${it.from}:${it.to}" }) { pair ->
            CurrencyChip(
                description = stringResource(R.string.recent_pair_switch, pair.from.iso4217Alpha(), pair.to.iso4217Alpha()),
                onClick = { onPick(pair) },
                modifier = Modifier.animateItem(),
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
