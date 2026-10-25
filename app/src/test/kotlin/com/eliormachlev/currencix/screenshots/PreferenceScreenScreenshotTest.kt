package com.eliormachlev.currencix.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.view.compose.FULL_SUMMARY
import com.eliormachlev.currencix.view.compose.ReadingDirection
import com.eliormachlev.currencix.view.preference.compose.AboutProviderRow
import com.eliormachlev.currencix.view.preference.compose.NavigationRow
import com.eliormachlev.currencix.view.preference.compose.PreferenceRow
import com.eliormachlev.currencix.view.preference.compose.PreferenceSection
import com.eliormachlev.currencix.view.preference.compose.SettingsList
import com.eliormachlev.currencix.view.preference.compose.SwitchRow
import com.eliormachlev.currencix.view.preference.compose.versionSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// PreferenceScreen is VM-driven; rendering it directly would need a fake
// PreferenceViewModel + Database + AppCompatDelegate glue. Since screenshot
// tests exist to lock the *visual* composition, we reconstruct Settings'
// category list and two of its screens from the same NavigationRow /
// PreferenceRow / SwitchRow primitives the real screens compose — matching
// CartScreenshotTest's pattern of bypassing the VM wrapper.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5, application = Application::class)
class PreferenceScreenScreenshotTest {
    @get:Rule val shots = ScreenshotRule()

    @Test fun preferenceScreen() = shots.captureMatrix("preference_screen") { Prose { CategoriesPreview() } }

    @Test fun preferenceScreenLargeFont() = shots.captureLargeFont("preference_screen") { Prose { CategoriesPreview() } }

    @Test fun ratesScreen() = shots.captureMatrix("preference_rates") { Prose { RatesPreview() } }

    @Test fun aboutScreen() = shots.captureMatrix("preference_about") { Prose { AboutPreview() } }

    @Test fun securityScreen() = shots.captureMatrix("preference_security") { Prose { SecurityPreview() } }
}

// Security & privacy: the app lock, blocking screenshots, private requests.
@Composable
private fun SecurityPreview() {
    SettingsList {
        item {
            PreferenceSection(text = stringResource(id = R.string.settings_access_section)) {
                SwitchRow(
                    title = stringResource(id = R.string.app_lock_title),
                    summary = stringResource(id = R.string.app_lock_summary),
                    iconRes = R.drawable.ic_fingerprint,
                    checked = true,
                    onCheckedChange = {},
                )
                SwitchRow(
                    title = stringResource(id = R.string.block_screenshots_title),
                    summary = stringResource(id = R.string.block_screenshots_summary),
                    iconRes = R.drawable.ic_no_screenshots,
                    checked = false,
                    onCheckedChange = {},
                )
            }
        }
        item {
            PreferenceSection(text = stringResource(id = R.string.settings_network_section)) {
                SwitchRow(
                    title = stringResource(id = R.string.private_requests_title),
                    summary = stringResource(id = R.string.private_requests_summary),
                    iconRes = R.drawable.ic_visibility_off,
                    checked = false,
                    onCheckedChange = {},
                )
            }
        }
    }
}

// Settings is laid out the way the language reads (ProseTheme in the app).
@Composable
private fun Prose(content: @Composable () -> Unit) = ReadingDirection(content)

@Composable
private fun CategoriesPreview() {
    SettingsList {
        item {
            Column {
                CategoryRow(R.string.settings_converter_title, R.string.settings_converter_summary, R.drawable.ic_calculate)
                CategoryRow(R.string.category_api, R.string.settings_rates_summary, R.drawable.ic_data_provider)
                CategoryRow(R.string.category_appearance, R.string.settings_appearance_summary, R.drawable.ic_theme)
                CategoryRow(R.string.menu_timeline, R.string.graph_options_summary, R.drawable.ic_timeline)
                CategoryRow(R.string.backup_title, R.string.backup_summary, R.drawable.ic_backup)
                CategoryRow(R.string.settings_security_title, R.string.settings_security_summary, R.drawable.ic_shield)
                CategoryRow(R.string.category_about, R.string.settings_about_summary, R.drawable.ic_info)
            }
        }
    }
}

@Composable
private fun CategoryRow(
    title: Int,
    summary: Int,
    icon: Int,
) = NavigationRow(title = stringResource(title), summary = stringResource(summary), iconRes = icon, onClick = {})

@Composable
private fun RatesPreview() {
    SettingsList {
        item {
            PreferenceSection(text = stringResource(id = R.string.api_title)) {
                PreferenceRow(
                    title = stringResource(id = R.string.api_title),
                    summary = SAMPLE_PROVIDER.getName(LocalContext.current).toString(),
                    iconRes = R.drawable.ic_data_provider,
                )
                SwitchRow(
                    title = stringResource(id = R.string.show_unreliable_providers_title),
                    summary = stringResource(id = R.string.show_unreliable_providers_summary),
                    iconRes = R.drawable.ic_visibility,
                    checked = false,
                    onCheckedChange = {},
                )
                CategoryRow(R.string.settings_providers_title, R.string.settings_providers_summary, R.drawable.ic_info)
            }
        }
        item {
            PreferenceSection(text = stringResource(id = R.string.settings_updates_section)) {
                SwitchRow(
                    title = stringResource(id = R.string.auto_refresh_title),
                    summary = stringResource(id = R.string.auto_refresh_summary),
                    iconRes = R.drawable.ic_schedule,
                    checked = true,
                    onCheckedChange = {},
                )
                // The sample provider publishes once a day, so the row says when
                // instead of offering an interval.
                PreferenceRow(
                    title = stringResource(id = R.string.refresh_interval_title),
                    summary = stringResource(id = R.string.refresh_interval_daily, SAMPLE_PROVIDER.getName(LocalContext.current)),
                    iconRes = R.drawable.ic_refresh,
                    enabled = false,
                )
            }
        }
        // The providers screen's rows: their description and refresh cadence
        // come from the provider's own (translated) strings; an unreliable
        // one says why under them.
        item {
            PreferenceSection(text = stringResource(id = R.string.settings_providers_title)) {
                AboutProviderRow(SAMPLE_PROVIDER)
                AboutProviderRow(ApiProvider.NORGES_BANK)
            }
        }
    }
}

@Composable
private fun AboutPreview() {
    SettingsList {
        item {
            PreferenceSection(text = stringResource(id = R.string.category_versioninfo)) {
                PreferenceRow(
                    title = SAMPLE_VERSION,
                    summary = versionSummary(LocalContext.current, SAMPLE_YEAR),
                    iconRes = R.drawable.ic_tag,
                )
                PreferenceRow(title = stringResource(id = R.string.title_changelog), iconRes = R.drawable.ic_changelog)
            }
        }
        item {
            PreferenceSection(text = stringResource(id = R.string.credits_section_project)) {
                CategoryRow(R.string.credits_title, R.string.credits_summary, R.drawable.ic_code)
            }
        }
        item {
            PreferenceSection(text = stringResource(id = R.string.credits_section_legal)) {
                PreferenceRow(
                    title = stringResource(id = R.string.disclaimer_title),
                    summary = stringResource(id = R.string.disclaimer_summary),
                    summaryMaxLines = FULL_SUMMARY,
                    iconRes = R.drawable.ic_gavel,
                )
            }
        }
    }
}

private val SAMPLE_PROVIDER = ApiProvider.FRANKFURTER_APP
private const val SAMPLE_VERSION = "1.0.0"
private const val SAMPLE_YEAR = 2026
