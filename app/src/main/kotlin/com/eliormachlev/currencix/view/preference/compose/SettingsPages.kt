package com.eliormachlev.currencix.view.preference.compose

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.repository.persistence.Pref
import com.eliormachlev.currencix.util.fastestMode
import com.eliormachlev.currencix.util.releaseNotesUrl
import com.eliormachlev.currencix.view.compose.FULL_SUMMARY
import com.eliormachlev.currencix.view.compose.UiTestTags
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.view.navigation.Screen
import com.eliormachlev.currencix.view.navigation.SettingsTopic
import java.util.Calendar
import kotlin.math.roundToInt

// One section of a settings screen, given what its rows read and do.
private typealias PageSection = @Composable (SettingsPageContext) -> Unit

/**
 * One settings screen: the categories (null [topic]) or a category's rows,
 * section by section, each landing a beat after the one above.
 */
@Composable
internal fun SettingsPage(
    topic: SettingsTopic?,
    page: SettingsPageContext,
) {
    // The credits list scrolls itself, with headers of its own.
    if (topic == SettingsTopic.CREDITS) {
        val context = LocalContext.current
        CreditsList(remember(context) { creditsSections(context) })
        return
    }
    val sections = sectionsOf(topic)
    SettingsList {
        sections.forEachIndexed { index, section ->
            item(key = index) { SectionEnter(index = index) { section(page) } }
        }
    }
}

private fun sectionsOf(topic: SettingsTopic?): List<PageSection> =
    when (topic) {
        null -> listOf { CategoriesSection(it) }
        SettingsTopic.CONVERTER -> listOf({ FeesSection(it) }, { InputSection(it) })
        SettingsTopic.RATES -> listOf({ ProviderSection(it) }, { UpdatesSection(it) })
        SettingsTopic.PROVIDERS -> listOf { ProvidersSection(it) }
        SettingsTopic.APPEARANCE -> listOf({ DisplaySection(it) }, { TouchSection(it) })
        SettingsTopic.CHART -> listOf { ChartSection() }
        SettingsTopic.ABOUT -> listOf({ VersionSection() }, { ProjectSection(it) }, { LegalSection() })
        SettingsTopic.CREDITS -> emptyList()
    }

/** The top bar's title on [this] topic's screen. */
@get:StringRes
internal val SettingsTopic.titleRes: Int
    get() =
        when (this) {
            SettingsTopic.CONVERTER -> R.string.settings_converter_title
            SettingsTopic.RATES -> R.string.category_api
            SettingsTopic.PROVIDERS -> R.string.settings_providers_title
            SettingsTopic.APPEARANCE -> R.string.category_appearance
            SettingsTopic.CHART -> R.string.menu_timeline
            SettingsTopic.ABOUT -> R.string.category_about
            SettingsTopic.CREDITS -> R.string.title_credits
        }

// What the row that opens a topic says is inside it.
@get:StringRes
private val SettingsTopic.summaryRes: Int
    get() =
        when (this) {
            SettingsTopic.CONVERTER -> R.string.settings_converter_summary
            SettingsTopic.RATES -> R.string.settings_rates_summary
            SettingsTopic.PROVIDERS -> R.string.settings_providers_summary
            SettingsTopic.APPEARANCE -> R.string.settings_appearance_summary
            SettingsTopic.CHART -> R.string.graph_options_summary
            SettingsTopic.ABOUT -> R.string.settings_about_summary
            SettingsTopic.CREDITS -> R.string.credits_summary
        }

@get:DrawableRes
private val SettingsTopic.iconRes: Int
    get() =
        when (this) {
            SettingsTopic.CONVERTER -> R.drawable.ic_calculate
            SettingsTopic.RATES -> R.drawable.ic_data_provider
            SettingsTopic.PROVIDERS -> R.drawable.ic_info
            SettingsTopic.APPEARANCE -> R.drawable.ic_theme
            SettingsTopic.CHART -> R.drawable.ic_timeline
            SettingsTopic.ABOUT -> R.drawable.ic_info
            SettingsTopic.CREDITS -> R.drawable.ic_code
        }

// The row that opens [topic]'s screen.
@Composable
private fun TopicRow(
    topic: SettingsTopic,
    page: SettingsPageContext,
) {
    NavigationRow(
        title = stringResource(topic.titleRes),
        summary = stringResource(topic.summaryRes),
        iconRes = topic.iconRes,
        onClick = { page.callbacks.onOpen(Screen.SettingsPage(topic)) },
        modifier = Modifier.testTag(UiTestTags.settingsTopic(topic.name)),
    )
}

// Settings itself: one row per category, backup among them (a screen of its own already).
@Composable
private fun CategoriesSection(page: SettingsPageContext) {
    Column {
        TopicRow(SettingsTopic.CONVERTER, page)
        TopicRow(SettingsTopic.RATES, page)
        TopicRow(SettingsTopic.APPEARANCE, page)
        TopicRow(SettingsTopic.CHART, page)
        NavigationRow(
            title = stringResource(R.string.backup_title),
            summary = stringResource(R.string.backup_summary),
            iconRes = R.drawable.ic_backup,
            onClick = { page.callbacks.onOpen(Screen.Backup) },
        )
        TopicRow(SettingsTopic.ABOUT, page)
    }
}

@Composable
private fun FeesSection(page: SettingsPageContext) {
    PreferenceSection(text = stringResource(R.string.fee_manager_title)) {
        NavigationRow(
            title = stringResource(R.string.fee_title),
            summary = stringResource(R.string.fee_summary),
            iconRes = R.drawable.ic_fee,
            onClick = { page.callbacks.onOpen(Screen.Fees) },
        )
    }
}

@Composable
private fun InputSection(page: SettingsPageContext) {
    val values = page.values
    PreferenceSection(text = stringResource(R.string.settings_input_section)) {
        SwitchRow(
            title = stringResource(R.string.keyboard_title),
            summary = stringResource(R.string.keyboard_summary_expanded),
            iconRes = R.drawable.ic_keyboard_extended,
            checked = values.expandedKeypadEnabled,
            onCheckedChange = page.viewModel::setExpandedKeypadEnabled,
        )
        PreferenceRow(
            title = stringResource(R.string.decimal_places_title),
            summary = values.decimalPlaces.toString(),
            iconRes = R.drawable.ic_numbers,
            onClick = { page.openDialog(OpenDialog.DecimalPlaces) },
        )
        SwitchRow(
            title = stringResource(R.string.previewConversion_title),
            summary = stringResource(R.string.previewConversion_summary),
            iconRes = R.drawable.ic_conversion_preview,
            checked = values.appearance.previewEnabled,
            onCheckedChange = page.viewModel::setPreviewConversionEnabled,
        )
    }
}

@Composable
private fun ProviderSection(page: SettingsPageContext) {
    val context = LocalContext.current
    val api = page.values.api
    val provider = api.provider
    PreferenceSection(text = stringResource(R.string.api_title)) {
        PreferenceRow(
            title = stringResource(R.string.api_title),
            summary = provider?.getName(context)?.toString(),
            iconRes = R.drawable.ic_data_provider,
            onClick = { page.openDialog(OpenDialog.Provider) },
        )
        FallbackProviderRow(main = provider, fallback = api.fallback, onClick = { page.openDialog(OpenDialog.FallbackProvider) })
        if (provider == ApiProvider.OPEN_EXCHANGERATES) {
            PreferenceRow(
                title = stringResource(R.string.api_open_exchangerates_api_key_title),
                summary = api.apiKey?.ifBlank { null } ?: stringResource(R.string.api_open_exchangerates_api_key_missing),
                iconRes = R.drawable.ic_key,
                onClick = { page.openDialog(OpenDialog.ApiKey) },
            )
        }
        TopicRow(SettingsTopic.PROVIDERS, page)
    }
}

// Auto-refresh (#151) and the alerts checked whenever rates arrive.
@Composable
private fun UpdatesSection(page: SettingsPageContext) {
    PreferenceSection(text = stringResource(R.string.settings_updates_section)) {
        SwitchRow(
            title = stringResource(R.string.auto_refresh_title),
            summary = stringResource(R.string.auto_refresh_summary),
            iconRes = R.drawable.ic_schedule,
            checked = page.values.api.autoRefreshEnabled,
            onCheckedChange = page.viewModel::setAutoRefreshEnabled,
        )
        PreferenceRow(
            title = stringResource(R.string.alerts_title),
            summary = stringResource(R.string.alerts_summary),
            iconRes = R.drawable.ic_alert,
            onClick = { page.openDialog(OpenDialog.RateAlerts) },
        )
    }
}

// The main provider, and the fallback that stands in when it can't be
// reached: what each is, and how often it publishes.
@Composable
private fun ProvidersSection(page: SettingsPageContext) {
    val api = page.values.api
    val provider = api.provider
    PreferenceSection(text = stringResource(R.string.api_title)) {
        provider?.let { AboutProviderRow(it) }
        if (api.fallback != provider) AboutProviderRow(api.fallback)
    }
}

@Composable
private fun DisplaySection(page: SettingsPageContext) {
    val context = LocalContext.current
    val values = page.values.appearance
    PreferenceSection(text = stringResource(R.string.settings_display_section)) {
        PreferenceRow(
            title = stringResource(R.string.theme_title),
            summary = stringResource(themeLabelRes(values.theme)),
            iconRes = R.drawable.ic_theme,
            onClick = { page.openDialog(OpenDialog.Theme) },
        )
        SwitchRow(
            title = stringResource(R.string.dynamic_color_title),
            summary = stringResource(R.string.dynamic_color_summary),
            iconRes = R.drawable.ic_palette,
            checked = values.dynamicColorEnabled,
            onCheckedChange = page.viewModel::setDynamicColorEnabled,
        )
        PreferenceRow(
            title = stringResource(R.string.language_title),
            summary = values.language.localizedName(context),
            iconRes = R.drawable.ic_language,
            onClick = { page.openDialog(OpenDialog.Language) },
        )
        PreferenceRow(
            title = stringResource(R.string.date_format_title),
            summary = values.dateFormat,
            iconRes = R.drawable.ic_event,
            onClick = { page.openDialog(OpenDialog.DateFormat) },
        )
        // Only a screen faster than 60 Hz has a choice to make.
        val view = LocalView.current
        val fastest =
            remember(view.display) {
                view.display
                    ?.fastestMode()
                    ?.hz
                    ?.roundToInt()
            }
        fastest?.let { hz ->
            SwitchRow(
                title = stringResource(R.string.high_refresh_rate_title),
                summary = stringResource(R.string.high_refresh_rate_summary, hz),
                iconRes = R.drawable.ic_speed,
                checked = values.highRefreshRateEnabled,
                onCheckedChange = page.viewModel::setHighRefreshRateEnabled,
            )
        }
    }
}

@Composable
private fun TouchSection(page: SettingsPageContext) {
    val values = page.values.appearance
    PreferenceSection(text = stringResource(R.string.settings_touch_section)) {
        SwitchRow(
            title = stringResource(R.string.haptic_feedback_title),
            summary = stringResource(R.string.haptic_feedback_summary),
            iconRes = R.drawable.ic_vibration,
            checked = values.hapticEnabled,
            onCheckedChange = page.viewModel::setHapticFeedbackEnabled,
        )
        SwitchRow(
            title = stringResource(R.string.click_sounds_title),
            summary = stringResource(R.string.click_sounds_summary),
            iconRes = R.drawable.ic_volume_up,
            checked = values.clickSoundsEnabled,
            onCheckedChange = page.viewModel::setClickSoundsEnabled,
        )
    }
}

// The chart's switches, the same ones its own options sheet shows.
@Composable
private fun ChartSection() {
    val context = LocalContext.current
    val options = remember(context) { graphOptions(Database(context).chart) }
    PreferenceSection(text = stringResource(R.string.category_graph_options)) {
        options.forEach { (labelRes, pref) -> ChartOptionRow(labelRes, pref) }
    }
}

@Composable
private fun ChartOptionRow(
    @StringRes labelRes: Int,
    pref: Pref<Boolean>,
) {
    val checked by pref.flow.collectAsStateWithLifecycle(initialValue = remember(pref) { pref.get() })
    SwitchRow(title = stringResource(labelRes), checked = checked, onCheckedChange = pref::set)
}

@Composable
private fun VersionSection() {
    val context = LocalContext.current
    // The installed APK's own name: BuildConfig is one for every CPU type's APK.
    val versionName = remember(context) { installedVersionName(context) }
    PreferenceSection(text = stringResource(R.string.category_versioninfo)) {
        PreferenceRow(
            title = versionName,
            summary = versionSummary(context, Calendar.getInstance().get(Calendar.YEAR)),
            iconRes = R.drawable.ic_tag,
            summaryMaxLines = FULL_SUMMARY,
        )
        PreferenceRow(
            title = stringResource(R.string.title_changelog),
            iconRes = R.drawable.ic_changelog,
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, releaseNotesUrl().toUri())) },
        )
        // Rows only a debug build has (see debugPreferenceRows, per build type).
        debugPreferenceRows?.invoke()
    }
}

@Composable
private fun ProjectSection(page: SettingsPageContext) {
    PreferenceSection(text = stringResource(R.string.credits_section_project)) {
        NavigationRow(
            title = stringResource(R.string.credits_title),
            summary = stringResource(R.string.credits_summary),
            iconRes = R.drawable.ic_code,
            onClick = { page.callbacks.onOpen(Screen.SettingsPage(SettingsTopic.CREDITS)) },
        )
        // Only a build distributed through a store can be rated there.
        page.callbacks.onRateApp?.let { rate ->
            PreferenceRow(
                title = stringResource(R.string.rate_title),
                summary = stringResource(R.string.rate_summary),
                iconRes = R.drawable.ic_rate,
                onClick = rate,
            )
        }
    }
}

@Composable
private fun LegalSection() {
    val disclaimerHtml = stringResource(R.string.disclaimer_summary)
    val disclaimer =
        remember(disclaimerHtml) {
            AnnotatedString(HtmlCompat.fromHtml(disclaimerHtml, HtmlCompat.FROM_HTML_MODE_COMPACT).toString())
        }
    PreferenceSection(text = stringResource(R.string.credits_section_legal)) {
        PreferenceRow(
            title = stringResource(R.string.disclaimer_title),
            summary = disclaimer.text,
            iconRes = R.drawable.ic_gavel,
            summaryMaxLines = FULL_SUMMARY,
        )
    }
    // Room under the last row, clear of the navigation bar's edge.
    Spacer(Modifier.height(Spacing.Large))
}
