package com.eliormachlev.currencix.view.preference.compose

import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.BuildConfig
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.AppTheme
import com.eliormachlev.currencix.model.Language
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.DECIMAL_PLACES_MAX
import com.eliormachlev.currencix.util.DECIMAL_PLACES_MIN
import com.eliormachlev.currencix.util.inReadingOrder
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
    data object DecimalPlaces : OpenDialog

    data object Theme : OpenDialog

    data object DateFormat : OpenDialog

    data object Language : OpenDialog

    data object Provider : OpenDialog

    data object FallbackProvider : OpenDialog

    data object ApiKey : OpenDialog

    data object GraphOptions : OpenDialog

    data object Credits : OpenDialog
}

/**
 * Full preferences screen — Compose replacement for the old `prefs.xml`.
 * Assembles six [PreferenceSection] cards from the observed
 * [PreferenceViewModel] state, opens picker dialogs via [OpenDialog] state,
 * and dispatches non-preference actions (opening fees, backup, credits,
 * changelog, rate) through the [callbacks] bag so the route
 * (SettingsRoute) can wire navigation and intents.
 */
@Composable
fun PreferenceScreen(
    viewModel: PreferenceViewModel,
    callbacks: PreferenceScreenCallbacks,
) {
    var openDialog by remember { mutableStateOf<OpenDialog?>(null) }
    val values = observePreferenceValues(viewModel)

    AppComposeTheme {
        PreferenceSectionsList(
            viewModel = viewModel,
            callbacks = callbacks,
            values = values,
            onOpenDialog = { openDialog = it },
        )
    }

    PreferenceDialogsHost(
        openDialog = openDialog,
        dismiss = { openDialog = null },
        viewModel = viewModel,
        callbacks = callbacks,
        values = values,
    )
}

// The settings as the screen shows them, grouped the way its sections are.
@Immutable
private data class PreferenceValues(
    val api: ApiValues,
    val appearance: AppearanceValues,
    val decimalPlaces: Int,
    val expandedKeypadEnabled: Boolean,
)

@Immutable
private data class ApiValues(
    val provider: ApiProvider?,
    val fallback: ApiProvider,
    // Only Open Exchange Rates takes one.
    val apiKey: String?,
    val autoRefreshEnabled: Boolean,
)

@Immutable
private data class AppearanceValues(
    val theme: AppTheme,
    val language: Language,
    val dateFormat: String,
    val hapticEnabled: Boolean,
    val dynamicColorEnabled: Boolean,
    val previewEnabled: Boolean,
)

@Composable
private fun observePreferenceValues(viewModel: PreferenceViewModel): PreferenceValues {
    val provider by viewModel.apiProvider.collectAsStateWithLifecycle()
    val fallback by viewModel.fallbackProvider.collectAsStateWithLifecycle()
    val apiKey by viewModel.openExchangeratesApiKey.collectAsStateWithLifecycle()
    val autoRefreshEnabled by viewModel.isAutoRefreshEnabled.collectAsStateWithLifecycle()
    val decimalPlaces by viewModel.decimalPlaces.collectAsStateWithLifecycle()
    val expandedKeypadEnabled by viewModel.isExpandedKeypadEnabled.collectAsStateWithLifecycle()
    return PreferenceValues(
        api = ApiValues(provider, fallback, apiKey, autoRefreshEnabled),
        appearance = observeAppearanceValues(viewModel, provider),
        decimalPlaces = decimalPlaces,
        expandedKeypadEnabled = expandedKeypadEnabled,
    )
}

@Composable
private fun observeAppearanceValues(
    viewModel: PreferenceViewModel,
    provider: ApiProvider?,
): AppearanceValues {
    val dateFormat by viewModel.dateFormat.collectAsStateWithLifecycle()
    val hapticEnabled by viewModel.isHapticFeedbackEnabled.collectAsStateWithLifecycle()
    val dynamicColorEnabled by viewModel.isDynamicColorEnabled.collectAsStateWithLifecycle()
    val previewEnabled by viewModel.isPreviewConversionEnabled.collectAsStateWithLifecycle()
    return AppearanceValues(
        theme = remember { viewModel.getTheme() },
        language = remember(provider) { Language.byIso(viewModel.getLanguage()) ?: Language.SYSTEM },
        dateFormat = dateFormat,
        hapticEnabled = hapticEnabled,
        dynamicColorEnabled = dynamicColorEnabled,
        previewEnabled = previewEnabled,
    )
}

/**
 * The six-section LazyColumn body of [PreferenceScreen], apart from the
 * dialog host below.
 */
@Composable
private fun PreferenceSectionsList(
    viewModel: PreferenceViewModel,
    callbacks: PreferenceScreenCallbacks,
    values: PreferenceValues,
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
                    expandedKeypadEnabled = values.expandedKeypadEnabled,
                    decimalPlaces = values.decimalPlaces,
                    callbacks = callbacks,
                    onExpandedKeypadChange = viewModel::setExpandedKeypadEnabled,
                    openDecimalPlacesPicker = { onOpenDialog(OpenDialog.DecimalPlaces) },
                )
            }
        }
        item(key = SettingsSection.API) {
            SectionEnter(index = SettingsSection.API.ordinal) {
                ApiSection(values = values.api, onAutoRefreshChange = viewModel::setAutoRefreshEnabled, onOpenDialog = onOpenDialog)
            }
        }
        item(key = SettingsSection.APPEARANCE) {
            SectionEnter(index = SettingsSection.APPEARANCE.ordinal) {
                AppearanceSection(values = values.appearance, viewModel = viewModel, onOpenDialog = onOpenDialog)
            }
        }
        item(key = SettingsSection.GRAPH) {
            SectionEnter(SettingsSection.GRAPH.ordinal) { GraphSection { onOpenDialog(OpenDialog.GraphOptions) } }
        }
        item(key = SettingsSection.ABOUT) {
            SectionEnter(SettingsSection.ABOUT.ordinal) { AboutSection(callbacks) { onOpenDialog(OpenDialog.Credits) } }
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
private fun PreferenceDialogsHost(
    openDialog: OpenDialog?,
    dismiss: () -> Unit,
    viewModel: PreferenceViewModel,
    callbacks: PreferenceScreenCallbacks,
    values: PreferenceValues,
) {
    val appearance = values.appearance
    when (openDialog) {
        OpenDialog.DecimalPlaces ->
            SingleChoicePickerDialog(
                title = stringResource(id = R.string.decimal_places_title),
                choices = Choices((DECIMAL_PLACES_MIN..DECIMAL_PLACES_MAX).toList(), values.decimalPlaces) { it.toString() },
                onDismiss = dismiss,
                onPicked = viewModel::setDecimalPlaces,
            )
        OpenDialog.Theme -> ThemePickerDialog(theme = appearance.theme, dismiss = dismiss, viewModel = viewModel, callbacks = callbacks)
        OpenDialog.DateFormat -> DateFormatPickerDialog(dateFormat = appearance.dateFormat, dismiss = dismiss, viewModel = viewModel)
        OpenDialog.Language ->
            LanguagePickerDialog(
                selected = appearance.language,
                onDismiss = dismiss,
                onPicked = { viewModel.setLanguage(it.iso) },
            )
        OpenDialog.Provider ->
            ProviderPickerDialog(
                selected = values.api.provider,
                onDismiss = dismiss,
                onPicked = viewModel::setApiProvider,
            )
        OpenDialog.FallbackProvider -> FallbackPickerDialog(viewModel, dismiss)
        OpenDialog.GraphOptions ->
            GraphOptionsSheet(
                db = Database(LocalContext.current),
                onDismiss = dismiss,
            )
        OpenDialog.Credits -> CreditsSheet(onDismiss = dismiss)
        OpenDialog.ApiKey ->
            TextEntryDialog(
                title = stringResource(id = R.string.api_open_exchangerates_api_key_title),
                initialText = values.api.apiKey.orEmpty(),
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
        choices = Choices(themeEntries, theme) { themeLabels[themeEntries.indexOf(it)] },
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
        choices = Choices(patterns, dateFormat) { pattern -> names.getOrNull(patterns.indexOf(pattern)) ?: pattern },
        onDismiss = dismiss,
        onPicked = viewModel::setDateFormat,
    )
}

@Composable
private fun GeneralSection(
    expandedKeypadEnabled: Boolean,
    decimalPlaces: Int,
    callbacks: PreferenceScreenCallbacks,
    onExpandedKeypadChange: (Boolean) -> Unit,
    openDecimalPlacesPicker: () -> Unit,
) {
    PreferenceSection(text = stringResource(id = R.string.category_settings)) {
        PreferenceRow(
            title = stringResource(id = R.string.fee_title),
            summary = stringResource(id = R.string.fee_summary),
            iconRes = R.drawable.ic_fee,
            onClick = callbacks.onOpenFees,
        )
        SwitchRow(
            title = stringResource(id = R.string.keyboard_title),
            summary = stringResource(id = R.string.keyboard_summary_expanded),
            iconRes = R.drawable.ic_keyboard_extended,
            checked = expandedKeypadEnabled,
            onCheckedChange = onExpandedKeypadChange,
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
    values: ApiValues,
    onAutoRefreshChange: (Boolean) -> Unit,
    onOpenDialog: (OpenDialog) -> Unit,
) {
    val context = LocalContext.current
    val provider = values.provider
    PreferenceSection(text = stringResource(id = R.string.category_api)) {
        PreferenceRow(
            title = stringResource(id = R.string.api_title),
            summary = provider?.getName(context)?.toString(),
            iconRes = R.drawable.ic_data_provider,
            onClick = { onOpenDialog(OpenDialog.Provider) },
        )
        FallbackProviderRow(main = provider, fallback = values.fallback, onClick = { onOpenDialog(OpenDialog.FallbackProvider) })
        if (provider == ApiProvider.OPEN_EXCHANGERATES) {
            PreferenceRow(
                title = stringResource(id = R.string.api_open_exchangerates_api_key_title),
                summary = values.apiKey?.ifBlank { null } ?: stringResource(id = R.string.api_open_exchangerates_api_key_missing),
                iconRes = R.drawable.ic_key,
                onClick = { onOpenDialog(OpenDialog.ApiKey) },
            )
        }
        provider?.let {
            PreferenceRow(
                title = stringResource(id = R.string.api_about_title, it.getName(context)).inReadingOrder(context),
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
            checked = values.autoRefreshEnabled,
            onCheckedChange = onAutoRefreshChange,
        )
    }
}

// "Fallback provider — Frankfurter.app · used when Bank of Israel can't be reached"
@Composable
private fun FallbackProviderRow(
    main: ApiProvider?,
    fallback: ApiProvider,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    PreferenceRow(
        title = stringResource(id = R.string.fallback_provider_title),
        summary =
            main?.let {
                stringResource(R.string.fallback_provider_summary, fallback.getName(context), it.getName(context)).inReadingOrder(context)
            } ?: fallback.getName(context).toString(),
        iconRes = R.drawable.ic_sync_problem,
        onClick = onClick,
    )
}

// The fallback picker: the main provider is greyed out, since it can't stand in for itself.
@Composable
private fun FallbackPickerDialog(
    viewModel: PreferenceViewModel,
    onDismiss: () -> Unit,
) {
    val main by viewModel.apiProvider.collectAsStateWithLifecycle()
    val fallback by viewModel.fallbackProvider.collectAsStateWithLifecycle()
    ProviderPickerDialog(
        selected = fallback,
        onDismiss = onDismiss,
        onPicked = viewModel::setFallbackProvider,
        title = stringResource(id = R.string.fallback_provider_title),
        unavailable = main,
    )
}

@Composable
private fun AppearanceSection(
    values: AppearanceValues,
    viewModel: PreferenceViewModel,
    onOpenDialog: (OpenDialog) -> Unit,
) {
    val context = LocalContext.current
    PreferenceSection(text = stringResource(id = R.string.category_appearance)) {
        PreferenceRow(
            title = stringResource(id = R.string.theme_title),
            summary = stringResource(id = themeLabelRes(values.theme)),
            iconRes = R.drawable.ic_theme,
            onClick = { onOpenDialog(OpenDialog.Theme) },
        )
        SwitchRow(
            title = stringResource(id = R.string.dynamic_color_title),
            summary = stringResource(id = R.string.dynamic_color_summary),
            iconRes = R.drawable.ic_palette,
            checked = values.dynamicColorEnabled,
            onCheckedChange = viewModel::setDynamicColorEnabled,
        )
        PreferenceRow(
            title = stringResource(id = R.string.language_title),
            summary = values.language.localizedName(context),
            iconRes = R.drawable.ic_language,
            onClick = { onOpenDialog(OpenDialog.Language) },
        )
        PreferenceRow(
            title = stringResource(id = R.string.date_format_title),
            summary = values.dateFormat,
            iconRes = R.drawable.ic_event,
            onClick = { onOpenDialog(OpenDialog.DateFormat) },
        )
        SwitchRow(
            title = stringResource(id = R.string.haptic_feedback_title),
            summary = stringResource(id = R.string.haptic_feedback_summary),
            iconRes = R.drawable.ic_vibration,
            checked = values.hapticEnabled,
            onCheckedChange = viewModel::setHapticFeedbackEnabled,
        )
        SwitchRow(
            title = stringResource(id = R.string.previewConversion_title),
            summary = stringResource(id = R.string.previewConversion_summary),
            iconRes = R.drawable.ic_conversion_preview,
            checked = values.previewEnabled,
            onCheckedChange = viewModel::setPreviewConversionEnabled,
        )
    }
}

@Composable
private fun GraphSection(openGraphOptions: () -> Unit) {
    PreferenceSection(text = stringResource(id = R.string.category_graph_options)) {
        PreferenceRow(
            title = stringResource(id = R.string.graph_options_title),
            summary = stringResource(id = R.string.graph_options_summary),
            iconRes = R.drawable.ic_tune,
            onClick = openGraphOptions,
        )
    }
}

@Composable
private fun AboutSection(
    callbacks: PreferenceScreenCallbacks,
    openCreditsSheet: () -> Unit,
) {
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
            onClick = openCreditsSheet,
        )
        // Only a build distributed through a store can be rated there.
        callbacks.onRateApp?.let { rate ->
            PreferenceRow(
                title = stringResource(id = R.string.rate_title),
                summary = stringResource(id = R.string.rate_summary),
                iconRes = R.drawable.ic_rate,
                onClick = rate,
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
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, releaseNotesUrl().toUri())) },
        )
        PreferenceRow(
            title = BuildConfig.VERSION_NAME,
            summary = stringResource(id = R.string.version_summary, Calendar.getInstance().get(Calendar.YEAR).toString()),
            iconRes = R.drawable.ic_tag,
        )
        // Rows only a debug build has (see debugPreferenceRows, per build type).
        debugPreferenceRows?.invoke()
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
    val onRateApp: (() -> Unit)?,
    val onThemeRequiresRestart: () -> Unit,
)

private fun themeLabelRes(theme: AppTheme): Int =
    when (theme) {
        AppTheme.LIGHT -> R.string.theme_option_light
        AppTheme.DARK -> R.string.theme_option_dark
        AppTheme.OLED -> R.string.theme_option_oled
        AppTheme.SYSTEM -> R.string.system_default
        AppTheme.SYSTEM_OLED -> R.string.theme_option_system_oled
    }
