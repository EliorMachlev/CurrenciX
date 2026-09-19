package com.eliormachlev.currencix.screenshots

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.view.preference.compose.PreferenceRow
import com.eliormachlev.currencix.view.preference.compose.PreferenceSection
import com.eliormachlev.currencix.view.preference.compose.SwitchRow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// PreferenceScreen is VM-driven; rendering it directly would need a fake
// PreferenceViewModel + Database + AppCompatDelegate glue. Since screenshot
// tests exist to lock the *visual* composition, we reconstruct the six
// sections using the same PreferenceSection / PreferenceRow / SwitchRow
// primitives the real screen composes — matching CartScreenshotTest's
// pattern of bypassing the VM wrapper.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5)
class PreferenceScreenScreenshotTest {
    @Test fun preferenceScreen() = captureMatrix("preference_screen") { PreferenceScreenPreview() }
}

@Composable
private fun PreferenceScreenPreview() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                horizontal = dimensionResource(id = R.dimen.margin2x),
                vertical = dimensionResource(id = R.dimen.margin1x),
            ),
    ) {
        item { GeneralSectionPreview() }
        item { ApiSectionPreview() }
        item { AppearanceSectionPreview() }
        item { GraphSectionPreview() }
        item { AboutSectionPreview() }
        item { VersionSectionPreview() }
    }
}

@Composable
private fun GeneralSectionPreview() {
    PreferenceSection(text = stringResource(id = R.string.category_settings)) {
        PreferenceRow(
            title = stringResource(id = R.string.fee_title),
            summary = stringResource(id = R.string.fee_summary),
            iconRes = R.drawable.ic_fee,
        )
        PreferenceRow(
            title = stringResource(id = R.string.keyboard_title),
            summary = stringResource(id = R.string.keyboard_option_default),
            iconRes = R.drawable.ic_keyboard_extended,
        )
        PreferenceRow(
            title = stringResource(id = R.string.decimal_places_title),
            summary = "2",
            iconRes = R.drawable.ic_numbers,
        )
        PreferenceRow(
            title = stringResource(id = R.string.backup_title),
            summary = stringResource(id = R.string.backup_summary),
            iconRes = R.drawable.ic_backup,
        )
    }
}

@Composable
private fun ApiSectionPreview() {
    PreferenceSection(text = stringResource(id = R.string.category_api)) {
        PreferenceRow(
            title = stringResource(id = R.string.api_title),
            summary = SAMPLE_PROVIDER,
            iconRes = R.drawable.ic_data_provider,
        )
        PreferenceRow(
            title = stringResource(id = R.string.api_about_title, SAMPLE_PROVIDER),
            summary = SAMPLE_PROVIDER_DESCRIPTION,
            iconRes = R.drawable.ic_info,
        )
        PreferenceRow(
            title = stringResource(id = R.string.api_refreshPeriod_title),
            summary = SAMPLE_PROVIDER_UPDATE_INTERVAL,
            iconRes = R.drawable.ic_schedule,
        )
    }
}

@Composable
private fun AppearanceSectionPreview() {
    PreferenceSection(text = stringResource(id = R.string.category_appearance)) {
        PreferenceRow(
            title = stringResource(id = R.string.theme_title),
            summary = stringResource(id = R.string.system_default),
            iconRes = R.drawable.ic_theme,
        )
        PreferenceRow(
            title = stringResource(id = R.string.language_title),
            summary = stringResource(id = R.string.system_default),
            iconRes = R.drawable.ic_language,
        )
        PreferenceRow(
            title = stringResource(id = R.string.date_format_title),
            summary = SAMPLE_DATE_FORMAT,
            iconRes = R.drawable.ic_event,
        )
        SwitchRow(
            title = stringResource(id = R.string.haptic_feedback_title),
            summary = stringResource(id = R.string.haptic_feedback_summary),
            iconRes = R.drawable.ic_vibration,
            checked = true,
            onCheckedChange = {},
        )
        SwitchRow(
            title = stringResource(id = R.string.previewConversion_title),
            summary = stringResource(id = R.string.previewConversion_summary),
            iconRes = R.drawable.ic_conversion_preview,
            checked = false,
            onCheckedChange = {},
        )
    }
}

@Composable
private fun GraphSectionPreview() {
    PreferenceSection(text = stringResource(id = R.string.category_graph_options)) {
        PreferenceRow(
            title = stringResource(id = R.string.graph_options_title),
            summary = stringResource(id = R.string.graph_options_summary),
            iconRes = R.drawable.ic_tune,
        )
    }
}

@Composable
private fun AboutSectionPreview() {
    PreferenceSection(text = stringResource(id = R.string.category_about)) {
        PreferenceRow(
            title = stringResource(id = R.string.disclaimer_title),
            summary = SAMPLE_DISCLAIMER,
            iconRes = R.drawable.ic_gavel,
        )
        PreferenceRow(
            title = stringResource(id = R.string.credits_title),
            summary = stringResource(id = R.string.credits_summary),
            iconRes = R.drawable.ic_code,
        )
    }
}

@Composable
private fun VersionSectionPreview() {
    PreferenceSection(text = stringResource(id = R.string.category_versioninfo)) {
        PreferenceRow(
            title = stringResource(id = R.string.title_changelog),
            iconRes = R.drawable.ic_changelog,
        )
        PreferenceRow(
            title = SAMPLE_VERSION,
            summary = stringResource(id = R.string.version_summary, SAMPLE_YEAR),
            iconRes = R.drawable.ic_tag,
        )
    }
}

private const val SAMPLE_PROVIDER = "European Central Bank"
private const val SAMPLE_PROVIDER_DESCRIPTION =
    "Reference rates published each business day around 16:00 CET."
private const val SAMPLE_PROVIDER_UPDATE_INTERVAL = "Every 4 hours"
private const val SAMPLE_DATE_FORMAT = "dd/MM/yy HH:mm"
private const val SAMPLE_DISCLAIMER =
    "Rates are informational only and may lag the market. Do not trade on them."
private const val SAMPLE_VERSION = "1.0.0"
private const val SAMPLE_YEAR = "2026"
