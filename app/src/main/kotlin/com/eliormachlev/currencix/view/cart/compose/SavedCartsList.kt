package com.eliormachlev.currencix.view.cart.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.util.hapticClickable
import com.eliormachlev.currencix.util.rememberHapticOnClick

private const val ROW_MIN_HEIGHT_DP = 48

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
                ).padding(horizontal = dimensionResource(id = R.dimen.margin2x)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = cart.name.ifBlank { cart.id.take(8) },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = rememberHapticOnClick(onRename)) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(id = R.string.cart_rename),
            )
        }
        IconButton(onClick = rememberHapticOnClick(onDelete)) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(id = R.string.cart_delete_item),
            )
        }
    }
}
