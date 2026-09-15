package com.eliormachlev.currencix.view.preference.compose

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.BuildConfig
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.AppTheme
import com.eliormachlev.currencix.model.KeyboardType
import com.eliormachlev.currencix.model.Language
import com.eliormachlev.currencix.util.DECIMAL_PLACES_MAX
import com.eliormachlev.currencix.util.DECIMAL_PLACES_MIN
import com.eliormachlev.currencix.util.releaseNotesUrl
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel
import java.util.Calendar
import com.eliormachlev.currencix.view.compose.AppTheme as AppComposeTheme

// Buckets the preferences into the same six sections as the old prefs.xml
// (Settings / Data provider / Look & Feel / Graph / About / Version). Order
// is preserved so users landing on Settings see the same shape they always
// did — this migration is UI-plumbing, not IA.
private enum class SettingsSection {
    GENERAL,
    API,
    APPEARANCE,
    GRAPH,
    ABOUT,
    VERSION,
}

// One centrally-tracked "which picker dialog is currently open" — nulling it
// closes whatever's open. Keeps dialog state out of every row and lets us
// dismiss on rotation / back without individual saveable flags.
private sealed interface OpenDialog {
    data object Keyboard : OpenDialog

    data object DecimalPlaces : OpenDialog

    data object Theme : OpenDialog

    data object DateFormat : OpenDialog

    data object Language : OpenDialog

    data object Provider : OpenDialog

    data object ApiKey : OpenDialog
}

/**
 * Full preferences screen — Compose replacement for `prefs.xml` +
 * `PreferenceFragment`. Assembles six [PreferenceSection] cards from the
 * observed [PreferenceViewModel] state, opens picker dialogs via [OpenDialog]
 * state, and dispatches non-preference actions (opening fees, backup,
 * credits, changelog, rate) through the [callbacks] bag so the hosting
 * Fragment/Activity can wire fragment-pushes and intents.
 */
@Composable
fun PreferenceScreen(
    viewModel: PreferenceViewModel,
    callbacks: PreferenceScreenCallbacks,
) {
    var openDialog by remember { mutableStateOf<OpenDialog?>(null) }
    val dismiss: () -> Unit = { openDialog = null }

    val provider by viewModel.apiProvider.collectAsStateWithLifecycle()
    val apiKey by viewModel.openExchangeratesApiKey.collectAsStateWithLifecycle()
    val decimalPlaces by viewModel.decimalPlaces.collectAsStateWithLifecycle()
    val keyboardType by viewModel.keyboardType.collectAsStateWithLifecycle()
    val hapticEnabled by viewModel.isHapticFeedbackEnabled.collectAsStateWithLifecycle()
    val previewEnabled by viewModel.isPreviewConversionEnabled.collectAsStateWithLifecycle()
    val dateFormat by viewModel.dateFormat.collectAsStateWithLifecycle()
    val autoRefreshEnabled by viewModel.isAutoRefreshEnabled.collectAsStateWithLifecycle()
    val theme = remember { viewModel.getTheme() }
    val language = remember(provider) { Language.byIso(viewModel.getLanguage()) ?: Language.SYSTEM }

    AppComposeTheme {
        PreferenceSectionsList(
            viewModel = viewModel,
            callbacks = callbacks,
            provider = provider,
            apiKey = apiKey,
            decimalPlaces = decimalPlaces,
            keyboardType = keyboardType,
            hapticEnabled = hapticEnabled,
            previewEnabled = previewEnabled,
            dateFormat = dateFormat,
            theme = theme,
            language = language,
            autoRefreshEnabled = autoRefreshEnabled,
            onOpenDialog = { openDialog = it },
        )
    }

    PreferenceDialogsHost(
        openDialog = openDialog,
        dismiss = dismiss,
        viewModel = viewModel,
        callbacks = callbacks,
        provider = provider,
        apiKey = apiKey,
        decimalPlaces = decimalPlaces,
        keyboardType = keyboardType,
        dateFormat = dateFormat,
        theme = theme,
        language = language,
    )
}

/**
 * The six-section LazyColumn body of [PreferenceScreen]. Extracted so the
 * top-level composable stays under the LongMethod threshold and the dialog
 * host below can be read in isolation from the list layout.
 */
@Composable
@Suppress("LongParameterList")
private fun PreferenceSectionsList(
    viewModel: PreferenceViewModel,
    callbacks: PreferenceScreenCallbacks,
    provider: ApiProvider?,
    apiKey: String?,
    decimalPlaces: Int,
    keyboardType: KeyboardType,
    hapticEnabled: Boolean,
    previewEnabled: Boolean,
    dateFormat: String,
    theme: AppTheme,
    language: Language,
    autoRefreshEnabled: Boolean,
    onOpenDialog: (OpenDialog) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = dimensionResource(id = R.dimen.margin2x),
                vertical = dimensionResource(id = R.dimen.margin1x),
            ),
    ) {
        item(key = SettingsSection.GENERAL) {
            SectionEnter(index = SettingsSection.GENERAL.ordinal) {
                GeneralSection(
                    keyboardType = keyboardType,
                    decimalPlaces = decimalPlaces,
                    callbacks = callbacks,
                    openKeyboardPicker = { onOpenDialog(OpenDialog.Keyboard) },
                    openDecimalPlacesPicker = { onOpenDialog(OpenDialog.DecimalPlaces) },
                )
            }
        }
        item(key = SettingsSection.API) {
            SectionEnter(index = SettingsSection.API.ordinal) {
                ApiSection(
                    provider = provider,
                    apiKey = apiKey,
                    autoRefreshEnabled = autoRefreshEnabled,
                    onAutoRefreshChange = viewModel::setAutoRefreshEnabled,
                    openProviderPicker = { onOpenDialog(OpenDialog.Provider) },
                    openApiKeyEditor = { onOpenDialog(OpenDialog.ApiKey) },
                )
            }
        }
        item(key = SettingsSection.APPEARANCE) {
            SectionEnter(index = SettingsSection.APPEARANCE.ordinal) {
                AppearanceSection(
                    theme = theme,
                    language = language,
                    dateFormat = dateFormat,
                    hapticEnabled = hapticEnabled,
                    previewEnabled = previewEnabled,
                    onHapticChange = viewModel::setHapticFeedbackEnabled,
                    onPreviewChange = viewModel::setPreviewConversionEnabled,
                    openThemePicker = { onOpenDialog(OpenDialog.Theme) },
                    openLanguagePicker = { onOpenDialog(OpenDialog.Language) },
                    openDateFormatPicker = { onOpenDialog(OpenDialog.DateFormat) },
                )
            }
        }
        item(key = SettingsSection.GRAPH) {
            SectionEnter(index = SettingsSection.GRAPH.ordinal) { GraphSection(callbacks = callbacks) }
        }
        item(key = SettingsSection.ABOUT) {
            SectionEnter(index = SettingsSection.ABOUT.ordinal) { AboutSection(callbacks = callbacks) }
        }
        item(key = SettingsSection.VERSION) {
            SectionEnter(index = SettingsSection.VERSION.ordinal) { VersionSection() }
        }
    }
}

/**
 * Dispatches whichever picker/editor is currently open to its dialog composable.
 * Extracted from [PreferenceScreen] so the screen body stays short and the
 * dialog-selection `when` sits next to its own state.
 */
@Composable
@Suppress("LongParameterList")
private fun PreferenceDialogsHost(
    openDialog: OpenDialog?,
    dismiss: () -> Unit,
    viewModel: PreferenceViewModel,
    callbacks: PreferenceScreenCallbacks,
    provider: ApiProvider?,
    apiKey: String?,
    decimalPlaces: Int,
    keyboardType: KeyboardType,
    dateFormat: String,
    theme: AppTheme,
    language: Language,
) {
    when (openDialog) {
        OpenDialog.Keyboard -> KeyboardPickerDialog(keyboardType = keyboardType, dismiss = dismiss, viewModel = viewModel)
        OpenDialog.DecimalPlaces ->
            SingleChoicePickerDialog(
                title = stringResource(id = R.string.decimal_places_title),
                options = (DECIMAL_PLACES_MIN..DECIMAL_PLACES_MAX).toList(),
                selected = decimalPlaces,
                label = { it.toString() },
                onDismiss = dismiss,
                onPicked = viewModel::setDecimalPlaces,
            )
        OpenDialog.Theme -> ThemePickerDialog(theme = theme, dismiss = dismiss, viewModel = viewModel, callbacks = callbacks)
        OpenDialog.DateFormat -> DateFormatPickerDialog(dateFormat = dateFormat, dismiss = dismiss, viewModel = viewModel)
        OpenDialog.Language ->
            LanguagePickerDialog(
                selected = language,
                onDismiss = dismiss,
                onPicked = { viewModel.setLanguage(it.iso) },
            )
        OpenDialog.Provider ->
            ProviderPickerDialog(
                selected = provider,
                onDismiss = dismiss,
                onPicked = viewModel::setApiProvider,
            )
        OpenDialog.ApiKey ->
            TextEntryDialog(
                title = stringResource(id = R.string.api_open_exchangerates_api_key_title),
                initialText = apiKey.orEmpty(),
                message = stringResource(id = R.string.api_open_exchangerates_api_key_message),
                onDismiss = dismiss,
                onConfirm = { newKey ->
                    viewModel.setOpenExchangeratesApiKey(newKey.trim())
                    dismiss()
                },
            )
        null -> Unit
    }
}

@Composable
private fun KeyboardPickerDialog(
    keyboardType: KeyboardType,
    dismiss: () -> Unit,
    viewModel: PreferenceViewModel,
) {
    val entries = KeyboardType.entries
    val labels = entries.map { stringResource(id = keyboardLabelRes(it)) }
    val descriptions = entries.map { stringResource(id = keyboardDescriptionRes(it)) }
    SingleChoiceExplainerPickerDialog(
        title = stringResource(id = R.string.keyboard_title),
        options = entries,
        selected = keyboardType,
        label = { labels[entries.indexOf(it)] },
        description = { descriptions[entries.indexOf(it)] },
        onDismiss = dismiss,
        onPicked = viewModel::setKeyboardType,
    )
}

@Composable
private fun ThemePickerDialog(
    theme: AppTheme,
    dismiss: () -> Unit,
    viewModel: PreferenceViewModel,
    callbacks: PreferenceScreenCallbacks,
) {
    val themeEntries = AppTheme.entries.toList()
    val themeLabels = themeEntries.map { stringResource(id = themeLabelRes(it)) }
    SingleChoicePickerDialog(
        title = stringResource(id = R.string.theme_title),
        options = themeEntries,
        selected = theme,
        label = { themeLabels[themeEntries.indexOf(it)] },
        onDismiss = dismiss,
        onPicked = { picked ->
            if (viewModel.setTheme(picked)) callbacks.onThemeRequiresRestart()
        },
    )
}

@Composable
private fun DateFormatPickerDialog(
    dateFormat: String,
    dismiss: () -> Unit,
    viewModel: PreferenceViewModel,
) {
    val patterns = stringArrayResource(id = R.array.date_format_values).toList()
    val names = stringArrayResource(id = R.array.date_format_names).toList()
    SingleChoicePickerDialog(
        title = stringResource(id = R.string.date_format_title),
        options = patterns,
        selected = dateFormat,
        label = { pattern -> names.getOrNull(patterns.indexOf(pattern)) ?: pattern },
        onDismiss = dismiss,
        onPicked = viewModel::setDateFormat,
    )
}

@Composable
private fun GeneralSection(
    keyboardType: KeyboardType,
    decimalPlaces: Int,
    callbacks: PreferenceScreenCallbacks,
    openKeyboardPicker: () -> Unit,
    openDecimalPlacesPicker: () -> Unit,
) {
    PreferenceSection(text = stringResource(id = R.string.category_settings)) {
        PreferenceRow(
            title = stringResource(id = R.string.fee_title),
            summary = stringResource(id = R.string.fee_summary),
            iconRes = R.drawable.ic_fee,
            onClick = callbacks.onOpenFees,
        )
        PreferenceRow(
            title = stringResource(id = R.string.keyboard_title),
            summary = stringResource(id = keyboardLabelRes(keyboardType)),
            iconRes = R.drawable.ic_keyboard_extended,
            onClick = openKeyboardPicker,
        )
        PreferenceRow(
            title = stringResource(id = R.string.decimal_places_title),
            summary = decimalPlaces.toString(),
            iconRes = R.drawable.ic_numbers,
            onClick = openDecimalPlacesPicker,
        )
        PreferenceRow(
            title = stringResource(id = R.string.backup_title),
            summary = stringResource(id = R.string.backup_summary),
            iconRes = R.drawable.ic_backup,
            onClick = callbacks.onOpenBackup,
        )
    }
}

@Composable
private fun ApiSection(
    provider: ApiProvider?,
    apiKey: String?,
    autoRefreshEnabled: Boolean,
    onAutoRefreshChange: (Boolean) -> Unit,
    openProviderPicker: () -> Unit,
    openApiKeyEditor: () -> Unit,
) {
    val context = LocalContext.current
    PreferenceSection(text = stringResource(id = R.string.category_api)) {
        PreferenceRow(
            title = stringResource(id = R.string.api_title),
            summary = provider?.getName(context)?.toString(),
            iconRes = R.drawable.ic_data_provider,
            onClick = openProviderPicker,
        )
        if (provider == ApiProvider.OPEN_EXCHANGERATES) {
            PreferenceRow(
                title = stringResource(id = R.string.api_open_exchangerates_api_key_title),
                summary =
                    if (apiKey.isNullOrBlank()) {
                        stringResource(id = R.string.api_open_exchangerates_api_key_missing)
                    } else {
                        apiKey
                    },
                iconRes = R.drawable.ic_key,
                onClick = openApiKeyEditor,
            )
        }
        provider?.let {
            PreferenceRow(
                title = stringResource(id = R.string.api_about_title, it.getName(context)),
                summary = it.getDescriptionLong(context).toString(),
                iconRes = R.drawable.ic_info,
            )
            PreferenceRow(
                title = stringResource(id = R.string.api_refreshPeriod_title),
                summary = it.getDescriptionUpdateInterval(context).toString(),
                iconRes = R.drawable.ic_schedule,
            )
        }
        // Auto-refresh (#151). Placed inside the API section since its
        // cadence is derived from the currently-selected provider — the
        // adjacent "refresh period" row above spells out what "recommended"
        // means for the picked provider.
        SwitchRow(
            title = stringResource(id = R.string.auto_refresh_title),
            summary = stringResource(id = R.string.auto_refresh_summary),
            iconRes = R.drawable.ic_schedule,
            checked = autoRefreshEnabled,
            onCheckedChange = onAutoRefreshChange,
        )
    }
}

@Composable
private fun AppearanceSection(
    theme: AppTheme,
    language: Language,
    dateFormat: String,
    hapticEnabled: Boolean,
    previewEnabled: Boolean,
    onHapticChange: (Boolean) -> Unit,
    onPreviewChange: (Boolean) -> Unit,
    openThemePicker: () -> Unit,
    openLanguagePicker: () -> Unit,
    openDateFormatPicker: () -> Unit,
) {
    val context = LocalContext.current
    PreferenceSection(text = stringResource(id = R.string.category_appearance)) {
        PreferenceRow(
            title = stringResource(id = R.string.theme_title),
            summary = stringResource(id = themeLabelRes(theme)),
            iconRes = R.drawable.ic_theme,
            onClick = openThemePicker,
        )
        PreferenceRow(
            title = stringResource(id = R.string.language_title),
            summary = language.localizedName(context),
            iconRes = R.drawable.ic_language,
            onClick = openLanguagePicker,
        )
        PreferenceRow(
            title = stringResource(id = R.string.date_format_title),
            summary = dateFormat,
            iconRes = R.drawable.ic_event,
            onClick = openDateFormatPicker,
        )
        SwitchRow(
            title = stringResource(id = R.string.haptic_feedback_title),
            summary = stringResource(id = R.string.haptic_feedback_summary),
            iconRes = R.drawable.ic_vibration,
            checked = hapticEnabled,
            onCheckedChange = onHapticChange,
        )
        SwitchRow(
            title = stringResource(id = R.string.previewConversion_title),
            summary = stringResource(id = R.string.previewConversion_summary),
            iconRes = R.drawable.ic_conversion_preview,
            checked = previewEnabled,
            onCheckedChange = onPreviewChange,
        )
    }
}

@Composable
private fun GraphSection(callbacks: PreferenceScreenCallbacks) {
    PreferenceSection(text = stringResource(id = R.string.category_graph_options)) {
        PreferenceRow(
            title = stringResource(id = R.string.graph_options_title),
            summary = stringResource(id = R.string.graph_options_summary),
            iconRes = R.drawable.ic_tune,
            onClick = callbacks.onOpenGraphOptions,
        )
    }
}

@Composable
private fun AboutSection(callbacks: PreferenceScreenCallbacks) {
    val disclaimerHtml = stringResource(id = R.string.disclaimer_summary)
    val disclaimerAnnotated =
        remember(disclaimerHtml) {
            AnnotatedString(HtmlCompat.fromHtml(disclaimerHtml, HtmlCompat.FROM_HTML_MODE_COMPACT).toString())
        }
    PreferenceSection(text = stringResource(id = R.string.category_about)) {
        PreferenceRow(
            title = stringResource(id = R.string.disclaimer_title),
            summary = disclaimerAnnotated.text,
            iconRes = R.drawable.ic_gavel,
        )
        PreferenceRow(
            title = stringResource(id = R.string.credits_title),
            summary = stringResource(id = R.string.credits_summary),
            iconRes = R.drawable.ic_code,
            onClick = callbacks.onOpenCredits,
        )
        @Suppress("KotlinConstantConditions")
        if (BuildConfig.FLAVOR == FLAVOR_PLAY) {
            PreferenceRow(
                title = stringResource(id = R.string.rate_title),
                summary = stringResource(id = R.string.rate_summary),
                iconRes = R.drawable.ic_rate,
                onClick = callbacks.onRateApp,
            )
        }
    }
}

@Composable
private fun VersionSection() {
    val context = LocalContext.current
    PreferenceSection(text = stringResource(id = R.string.category_versioninfo)) {
        PreferenceRow(
            title = stringResource(id = R.string.title_changelog),
            iconRes = R.drawable.ic_changelog,
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseNotesUrl()))) },
        )
        PreferenceRow(
            title = BuildConfig.VERSION_NAME,
            summary = stringResource(id = R.string.version_summary, Calendar.getInstance().get(Calendar.YEAR).toString()),
            iconRes = R.drawable.ic_tag,
        )
    }
    Text(
        text = "",
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(bottom = dimensionResource(id = R.dimen.margin2x)),
    )
}

/**
 * Wire-out for non-preference actions triggered from the screen: opening
 * sub-fragments (fees, backup), showing existing dialogs (credits, graph
 * options), external intents (rate on Play), and full-activity restarts
 * (theme swap that changes pure-black but not night mode).
 */
data class PreferenceScreenCallbacks(
    val onOpenFees: () -> Unit,
    val onOpenBackup: () -> Unit,
    val onOpenGraphOptions: () -> Unit,
    val onOpenCredits: () -> Unit,
    val onRateApp: () -> Unit,
    val onThemeRequiresRestart: () -> Unit,
)

// Build flavor served through Play; other flavors hide the "rate on Play"
// entry (donation flavor gets its own entry elsewhere).
private const val FLAVOR_PLAY = "play"

private fun keyboardLabelRes(type: KeyboardType): Int =
    when (type) {
        KeyboardType.BASIC -> R.string.keyboard_option_default
        KeyboardType.EXPANDED -> R.string.keyboard_option_expanded
        KeyboardType.SYSTEM_NUMPAD -> R.string.keyboard_option_system
        KeyboardType.SYSTEM_FULL -> R.string.keyboard_option_system_full
    }

private fun keyboardDescriptionRes(type: KeyboardType): Int =
    when (type) {
        KeyboardType.BASIC -> R.string.keyboard_summary_default
        KeyboardType.EXPANDED -> R.string.keyboard_summary_expanded
        KeyboardType.SYSTEM_NUMPAD -> R.string.keyboard_summary_system
        KeyboardType.SYSTEM_FULL -> R.string.keyboard_summary_system_full
    }

private fun themeLabelRes(theme: AppTheme): Int =
    when (theme) {
        AppTheme.LIGHT -> R.string.theme_option_light
        AppTheme.DARK -> R.string.theme_option_dark
        AppTheme.OLED -> R.string.theme_option_oled
        AppTheme.SYSTEM -> R.string.system_default
        AppTheme.SYSTEM_OLED -> R.string.theme_option_system_oled
    }
