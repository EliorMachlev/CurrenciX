package com.eliormachlev.currencix.view.preference.compose

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel

/** [provider]'s key, trimmed on the way in; how to get one is its own text. */
@Composable
internal fun ApiKeySheet(
    provider: ApiProvider,
    apiKey: String?,
    dismiss: () -> Unit,
    viewModel: PreferenceViewModel,
) {
    val texts = provider.apiKeyTexts ?: return
    TextEntrySheet(
        title = stringResource(id = texts.title),
        initialText = apiKey.orEmpty(),
        message = stringResource(id = texts.message),
        onDismiss = dismiss,
        onConfirm = { newKey ->
            viewModel.setApiKey(provider, newKey.trim())
            dismiss()
        },
    )
}

/**
 * A [ProviderPickerSheet] that, when the provider picked takes an API key
 * and none is set yet, goes straight on to that provider's [ApiKeySheet]
 * instead of closing, so a keyed provider is never left without one.
 * [onPicked] still runs first, as with the plain picker.
 */
@Composable
fun ProviderPickerWithKeyPrompt(
    selected: ApiProvider?,
    viewModel: PreferenceViewModel,
    onDismiss: () -> Unit,
    onPicked: (ApiProvider) -> Unit,
    title: String = stringResource(id = R.string.api_title),
    unavailable: ApiProvider? = null,
) {
    val apiKeys by viewModel.apiKeys.collectAsStateWithLifecycle()
    var needsKey by remember { mutableStateOf<ApiProvider?>(null) }
    val keyFor = needsKey
    if (keyFor != null) {
        ApiKeySheet(provider = keyFor, apiKey = null, dismiss = onDismiss, viewModel = viewModel)
        return
    }
    ProviderPickerSheet(
        selected = selected,
        // The picker closes itself after a pick: stay open for the key prompt then.
        onDismiss = { if (needsKey == null) onDismiss() },
        onPicked = { picked ->
            onPicked(picked)
            if (picked.apiKeyTexts != null && apiKeys[picked].isNullOrBlank()) needsKey = picked
        },
        title = title,
        unavailable = unavailable,
    )
}

/** What the key row and its sheet say for a keyed provider. */
internal class ApiKeyTexts(
    @StringRes val title: Int,
    @StringRes val message: Int,
)

/** [ApiKeyTexts] for a provider that takes an API key; null for one that doesn't. */
internal val ApiProvider.apiKeyTexts: ApiKeyTexts?
    get() =
        when (this) {
            ApiProvider.OPEN_EXCHANGERATES -> {
                ApiKeyTexts(R.string.api_open_exchangerates_api_key_title, R.string.api_open_exchangerates_api_key_message)
            }

            ApiProvider.ALL_RATES_TODAY -> {
                ApiKeyTexts(R.string.api_all_rates_today_api_key_title, R.string.api_all_rates_today_api_key_message)
            }

            else -> {
                null
            }
        }
