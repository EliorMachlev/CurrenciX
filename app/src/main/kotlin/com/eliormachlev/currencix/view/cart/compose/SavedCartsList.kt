package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.util.formatCartAmount
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.util.rememberHapticOnClick
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.viewmodel.cart.subtotalOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val ROW_MIN_HEIGHT_DP = 48

// An unnamed cart is listed by the start of its id.
private const val SHORT_ID_LENGTH = 8

// Between the parts of a saved cart's summary line.
private const val SUMMARY_SEPARATOR = " · "

private val SAVED_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
fun SavedCartsList(
    items: List<SavedCart>,
    onPick: (SavedCart) -> Unit,
    onRename: (SavedCart) -> Unit,
    onDelete: (SavedCart) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(items = items, key = { it.id }) { cart ->
            SavedCartRow(
                cart = cart,
                onPick = { onPick(cart) },
                onRename = { onRename(cart) },
                onDelete = { onDelete(cart) },
            )
        }
    }
}

@Composable
private fun SavedCartRow(
    cart: SavedCart,
    onPick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = ROW_MIN_HEIGHT_DP.dp)
                .hapticClickable(
                    onClickLabel = stringResource(id = R.string.a11y_action_load),
                    onClick = onPick,
                ).padding(horizontal = Spacing.Large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = Spacing.Small)) {
            Text(
                text = cart.name.ifBlank { cart.id.take(SHORT_ID_LENGTH) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = savedCartSummary(cart),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = rememberHapticOnClick(onRename)) {
            Icon(
                painter = painterResource(R.drawable.ic_edit),
                contentDescription = stringResource(id = R.string.cart_rename),
            )
        }
        IconButton(onClick = rememberHapticOnClick(onDelete)) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(id = R.string.cart_delete_item),
            )
        }
    }
}

// "4 items · 23.50 $ · 3 Oct 2026": what's in a saved cart, what it comes
// to in its own currency, and when it was saved.
@Composable
private fun savedCartSummary(cart: SavedCart): String {
    val context = LocalContext.current
    val count = pluralStringResource(R.plurals.cart_saved_items, cart.items.size, cart.items.size)
    val total = remember(cart) { context.formatCartAmount(subtotalOf(cart), Currency.fromString(cart.currency)) }
    val date =
        remember(cart.createdAt) {
            Instant
                .ofEpochMilli(cart.createdAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(SAVED_DATE_FORMAT)
        }
    return listOf(count, total, date).joinToString(SUMMARY_SEPARATOR)
}
