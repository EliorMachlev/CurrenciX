package com.eliormachlev.currencix.view.preference.compose

import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eliormachlev.currencix.BuildConfig
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.AppTheme
import com.eliormachlev.currencix.model.Language
import com.eliormachlev.currencix.model.RefreshInterval
import com.eliormachlev.currencix.util.DECIMAL_PLACES_MAX
import com.eliormachlev.currencix.util.DECIMAL_PLACES_MIN
import com.eliormachlev.currencix.util.inReadingOrder
import com.eliormachlev.currencix.view.alert.RateAlertsSheet
import com.eliormachlev.currencix.view.compose.FULL_SUMMARY
import com.eliormachlev.currencix.view.compose.ProseTheme
import com.eliormachlev.currencix.view.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.view.compose.rememberLinkedText
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.view.navigation.Screen
import com.eliormachlev.currencix.view.navigation.SettingsTopic
import com.eliormachlev.currencix.viewmodel.preference.PreferenceSettings

// One centrally-tracked "which picker dialog is currently open" — nulling it
// closes whatever's open. Keeps dialog state out of every row and lets us
// dismiss on rotation / back without individual saveable flags.
internal sealed interface OpenDialog {
    data object DecimalPlaces : OpenDialog

    data object Theme : OpenDialog

    data object DateFormat : OpenDialog

    data object Language : OpenDialog

    data object Provider : OpenDialog

    data object FallbackProvider : OpenDialog

    /** [provider]'s key: the main provider's or the fallback's. */
    data class ApiKey(
        val provider: ApiProvider,
    ) : OpenDialog

    data object RateAlerts : OpenDialog

    data object AutoRefreshInterval : OpenDialog
}

/**
 * Settings: the list of its categories when [topic] is null, else that
 * category's screen ([settingsPage]). Picker sheets open via [OpenDialog]
 * state; navigation, the store page and restarts go through [callbacks] so
 * the route (SettingsRoute) wires them.
 */
@Composable
fun PreferenceScreen(
    settings: PreferenceSettings,
    callbacks: PreferenceScreenCallbacks,
    topic: SettingsTopic?,
) {
    var openDialog by remember { mutableStateOf<OpenDialog?>(null) }
    val values = observePreferenceValues(settings)
    val page = SettingsPageContext(settings, callbacks, values) { openDialog = it }

    ProseTheme { SettingsPage(topic, page) }

    PreferenceDialogsHost(
        openDialog = openDialog,
        dismiss = { openDialog = null },
        settings = settings,
        callbacks = callbacks,
        values = values,
    )
}

/** What a settings screen's rows read and do. */
internal class SettingsPageContext(
    val settings: PreferenceSettings,
    val callbacks: PreferenceScreenCallbacks,
    val values: PreferenceValues,
    val openDialog: (OpenDialog) -> Unit,
)

// The settings as the screen shows them, grouped the way its sections are.
@Immutable
internal data class PreferenceValues(
    val api: ApiValues,
    val appearance: AppearanceValues,
    val decimalPlaces: Int,
    val expandedKeypadEnabled: Boolean,
)

@Immutable
internal data class ApiValues(
    val provider: ApiProvider?,
    val fallback: ApiProvider,
    // The key entered for each keyed provider.
    val apiKeys: Map<ApiProvider, String>,
    val autoRefreshEnabled: Boolean,
    val autoRefreshInterval: RefreshInterval,
)

@Immutable
internal data class AppearanceValues(
    val theme: AppTheme,
    val language: Language,
    val dateFormat: String,
    val hapticEnabled: Boolean,
    val clickSoundsEnabled: Boolean,
    val highRefreshRateEnabled: Boolean,
    val dynamicColorEnabled: Boolean,
    val previewEnabled: Boolean,
)

@Composable
private fun observePreferenceValues(settings: PreferenceSettings): PreferenceValues {
    val provider by settings.apiProvider.collectAsStateWithLifecycle()
    val fallback by settings.fallbackProvider.collectAsStateWithLifecycle()
    val apiKeys by settings.apiKeys.collectAsStateWithLifecycle()
    val autoRefreshEnabled by settings.isAutoRefreshEnabled.collectAsStateWithLifecycle()
    val autoRefreshInterval by settings.autoRefreshInterval.collectAsStateWithLifecycle()
    val decimalPlaces by settings.decimalPlaces.collectAsStateWithLifecycle()
    val expandedKeypadEnabled by settings.isExpandedKeypadEnabled.collectAsStateWithLifecycle()
    return PreferenceValues(
        api = ApiValues(provider, fallback, apiKeys, autoRefreshEnabled, autoRefreshInterval),
        appearance = observeAppearanceValues(settings, provider),
        decimalPlaces = decimalPlaces,
        expandedKeypadEnabled = expandedKeypadEnabled,
    )
}

@Composable
private fun observeAppearanceValues(
    settings: PreferenceSettings,
    provider: ApiProvider?,
): AppearanceValues {
    val dateFormat by settings.dateFormat.collectAsStateWithLifecycle()
    val hapticEnabled by settings.isHapticFeedbackEnabled.collectAsStateWithLifecycle()
    val clickSoundsEnabled by settings.isClickSoundsEnabled.collectAsStateWithLifecycle()
    val highRefreshRateEnabled by settings.isHighRefreshRateEnabled.collectAsStateWithLifecycle()
    val dynamicColorEnabled by settings.isDynamicColorEnabled.collectAsStateWithLifecycle()
    val previewEnabled by settings.isPreviewConversionEnabled.collectAsStateWithLifecycle()
    return AppearanceValues(
        theme = remember { settings.getTheme() },
        language = remember(provider) { settings.getLanguage() },
        dateFormat = dateFormat,
        hapticEnabled = hapticEnabled,
        clickSoundsEnabled = clickSoundsEnabled,
        highRefreshRateEnabled = highRefreshRateEnabled,
        dynamicColorEnabled = dynamicColorEnabled,
        previewEnabled = previewEnabled,
    )
}

/**
 * Dispatches whichever picker/editor is currently open to its sheet.
 * Extracted from [PreferenceScreen] so the screen body stays short and the
 * dialog-selection `when` sits next to its own state.
 */
@Composable
private fun PreferenceDialogsHost(
    openDialog: OpenDialog?,
    dismiss: () -> Unit,
    settings: PreferenceSettings,
    callbacks: PreferenceScreenCallbacks,
    values: PreferenceValues,
) {
    val appearance = values.appearance
    when (openDialog) {
        OpenDialog.DecimalPlaces -> {
            DecimalPlacesPickerSheet(decimalPlaces = values.decimalPlaces, dismiss = dismiss, settings = settings)
        }

        OpenDialog.Theme -> {
            ThemePickerSheet(theme = appearance.theme, dismiss = dismiss, settings = settings, callbacks = callbacks)
        }

        OpenDialog.DateFormat -> {
            DateFormatPickerSheet(dateFormat = appearance.dateFormat, dismiss = dismiss, settings = settings)
        }

        OpenDialog.Language -> {
            LanguagePickerSheet(
                selected = appearance.language,
                onDismiss = dismiss,
                onPicked = settings::setLanguage,
            )
        }

        OpenDialog.Provider -> {
            ProviderPickerWithKeyPrompt(
                selected = values.api.provider,
                settings = settings,
                onDismiss = dismiss,
                onPicked = settings::setApiProvider,
            )
        }

        OpenDialog.FallbackProvider -> {
            FallbackPickerSheet(settings, dismiss)
        }

        OpenDialog.AutoRefreshInterval -> {
            RefreshIntervalPickerSheet(selected = values.api.autoRefreshInterval, dismiss = dismiss, settings = settings)
        }

        OpenDialog.RateAlerts -> {
            RateAlertsSheet(onDismiss = dismiss)
        }

        is OpenDialog.ApiKey -> {
            ApiKeySheet(
                provider = openDialog.provider,
                apiKey = values.api.apiKeys[openDialog.provider],
                dismiss = dismiss,
                settings = settings,
            )
        }

        null -> {}
    }
}

@Composable
private fun DecimalPlacesPickerSheet(
    decimalPlaces: Int,
    dismiss: () -> Unit,
    settings: PreferenceSettings,
) {
    SingleChoicePickerSheet(
        title = stringResource(id = R.string.decimal_places_title),
        choices = Choices((DECIMAL_PLACES_MIN..DECIMAL_PLACES_MAX).toList(), decimalPlaces) { it.toString() },
        onDismiss = dismiss,
        onPicked = settings::setDecimalPlaces,
    )
}

// How often auto-refresh runs: 15 minutes to a day.
@Composable
private fun RefreshIntervalPickerSheet(
    selected: RefreshInterval,
    dismiss: () -> Unit,
    settings: PreferenceSettings,
) {
    val locale = LocalConfiguration.current.locales[0]
    SingleChoicePickerSheet(
        title = stringResource(id = R.string.refresh_interval_title),
        choices = Choices(RefreshInterval.entries, selected) { it.label(locale) },
        onDismiss = dismiss,
        onPicked = settings::setAutoRefreshInterval,
    )
}

@Composable
private fun ThemePickerSheet(
    theme: AppTheme,
    dismiss: () -> Unit,
    settings: PreferenceSettings,
    callbacks: PreferenceScreenCallbacks,
) {
    val themeEntries = AppTheme.entries.toList()
    val themeLabels = themeEntries.map { stringResource(id = themeLabelRes(it)) }
    SingleChoicePickerSheet(
        title = stringResource(id = R.string.theme_title),
        choices = Choices(themeEntries, theme) { themeLabels[themeEntries.indexOf(it)] },
        onDismiss = dismiss,
        onPicked = { picked ->
            if (settings.setTheme(picked)) callbacks.onThemeRequiresRestart()
        },
    )
}

@Composable
private fun DateFormatPickerSheet(
    dateFormat: String,
    dismiss: () -> Unit,
    settings: PreferenceSettings,
) {
    val patterns = stringArrayResource(id = R.array.date_format_values).toList()
    val names = stringArrayResource(id = R.array.date_format_names).toList()
    SingleChoicePickerSheet(
        title = stringResource(id = R.string.date_format_title),
        choices = Choices(patterns, dateFormat) { pattern -> names.getOrNull(patterns.indexOf(pattern)) ?: pattern },
        onDismiss = dismiss,
        onPicked = settings::setDateFormat,
    )
}

// One row per provider: "About Bank of Israel", its description in full,
// and under it how often it publishes, beside a small clock (and why it's
// unreliable, if it is). Both keep their resource's links and emphasis.
@Composable
internal fun AboutProviderRow(provider: ApiProvider) {
    val context = LocalContext.current
    val description = remember(provider, context) { provider.getDescriptionLong(context) }
    val cadence = remember(provider, context) { provider.getDescriptionUpdateInterval(context) }
    PreferenceRow(
        title = stringResource(id = R.string.api_about_title, provider.getName(context)).inReadingOrder(context),
        summary = description,
        iconRes = R.drawable.ic_info,
        summaryMaxLines = FULL_SUMMARY,
        footer = {
            CadenceLine(cadence)
            ReliabilityLabel(provider.reliability)
        },
    )
}

@Composable
private fun CadenceLine(text: CharSequence) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.padding(top = CADENCE_TOP_GAP),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(CADENCE_ICON_GAP),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_schedule),
            // Read as part of the row; the clock only marks the line.
            contentDescription = stringResource(R.string.api_refreshPeriod_title),
            tint = color,
            modifier = Modifier.padding(top = CADENCE_ICON_NUDGE).size(CADENCE_ICON_SIZE),
        )
        Text(text = rememberLinkedText(text), style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

private val CADENCE_TOP_GAP = Spacing.XSmall
private val CADENCE_ICON_GAP = Spacing.XSmall
private val CADENCE_ICON_SIZE = 16.dp
private val CADENCE_ICON_NUDGE = 2.dp

// "Fallback provider — Frankfurter.app · used when Bank of Israel can't be reached"
@Composable
internal fun FallbackProviderRow(
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
private fun FallbackPickerSheet(
    settings: PreferenceSettings,
    onDismiss: () -> Unit,
) {
    val main by settings.apiProvider.collectAsStateWithLifecycle()
    val fallback by settings.fallbackProvider.collectAsStateWithLifecycle()
    ProviderPickerWithKeyPrompt(
        selected = fallback,
        settings = settings,
        onDismiss = onDismiss,
        onPicked = settings::setFallbackProvider,
        title = stringResource(id = R.string.fallback_provider_title),
        unavailable = main,
    )
}

/**
 * What the screen hands to its route: opening another screen ([onOpen]: a
 * category, fees, backup), rating the app on its store (null where it has
 * none), and the full restart a pure-black theme change needs.
 */
data class PreferenceScreenCallbacks(
    val onOpen: (Screen) -> Unit,
    val onRateApp: (() -> Unit)?,
    val onThemeRequiresRestart: () -> Unit,
)

internal fun themeLabelRes(theme: AppTheme): Int =
    when (theme) {
        AppTheme.LIGHT -> R.string.theme_option_light
        AppTheme.DARK -> R.string.theme_option_dark
        AppTheme.OLED -> R.string.theme_option_oled
        AppTheme.SYSTEM -> R.string.system_default
        AppTheme.SYSTEM_OLED -> R.string.theme_option_system_oled
    }

// The version name of the APK installed, which names its CPU type; BuildConfig's when it can't be read.
internal fun installedVersionName(context: Context): String =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } ?: BuildConfig.VERSION_NAME
