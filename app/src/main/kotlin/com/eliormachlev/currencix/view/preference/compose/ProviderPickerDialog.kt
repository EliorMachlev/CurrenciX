package com.eliormachlev.currencix.view.preference.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider

/**
 * Compose replacement for the old `ProviderPickerDialogAdapter` + free-fun
 * `showProviderPickerDialog`. Radio list of every [ApiProvider] with a name /
 * short description / optional hint (e.g. "requires API key"). Tapping a row
 * commits and dismisses.
 */
@Composable
fun ProviderPickerDialog(
    selected: ApiProvider?,
    onDismiss: () -> Unit,
    onPicked: (ApiProvider) -> Unit,
) {
    val context = LocalContext.current
    ChoiceDialogFrame(
        title = stringResource(id = R.string.api_title),
        onDismiss = onDismiss,
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            items(items = ApiProvider.entries, key = { it.id }) { provider ->
                ChoiceRow(
                    checked = provider == selected,
                    onClick = {
                        onPicked(provider)
                        onDismiss()
                    },
                ) {
                    Text(
                        text = provider.getName(context).toString(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = provider.getDescriptionShort(context).toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    provider.getHint(context)?.let { hint ->
                        Text(
                            text = hint.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
    }
}
