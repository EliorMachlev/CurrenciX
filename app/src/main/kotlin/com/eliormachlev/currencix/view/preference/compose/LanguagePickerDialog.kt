package com.eliormachlev.currencix.view.preference.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Language
import com.eliormachlev.currencix.util.normalizeForSearch

private val SEARCH_TO_LIST_GAP = 12.dp

/**
 * Compose replacement for the old `LanguagePickerPreference` dialog. Radio
 * list of every [Language], filterable by localized OR native name (matches
 * against `normalizeForSearch`). Tapping a row commits [onPicked] with the
 * ISO string and dismisses.
 */
@Composable
fun LanguagePickerDialog(
    selected: Language?,
    onDismiss: () -> Unit,
    onPicked: (Language) -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val normalized = remember(query) { query.trim().normalizeForSearch() }
    val filtered =
        remember(normalized) {
            if (normalized.isEmpty()) {
                Language.entries
            } else {
                Language.entries.filter { it.matches(context, normalized) }
            }
        }
    ChoiceDialogFrame(
        title = stringResource(id = R.string.language_title),
        onDismiss = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(SEARCH_TO_LIST_GAP))
            LazyColumn(Modifier.fillMaxWidth()) {
                items(items = filtered, key = { it.iso }) { language ->
                    ChoiceRow(
                        checked = language == selected,
                        onClick = {
                            onPicked(language)
                            onDismiss()
                        },
                    ) {
                        when (language) {
                            Language.SYSTEM ->
                                Text(
                                    text = language.localizedName(context),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            else -> {
                                Text(
                                    text = language.nativeName(context),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = language.localizedName(context),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// [normalizedQuery] must already be [normalizeForSearch]-ed by the caller —
// callee-side re-normalization would fire per-row on every keystroke.
private fun Language.matches(
    context: android.content.Context,
    normalizedQuery: String,
): Boolean {
    if (localizedName(context).normalizeForSearch().contains(normalizedQuery)) return true
    if (this == Language.SYSTEM) return false
    return nativeName(context).normalizeForSearch().contains(normalizedQuery)
}
