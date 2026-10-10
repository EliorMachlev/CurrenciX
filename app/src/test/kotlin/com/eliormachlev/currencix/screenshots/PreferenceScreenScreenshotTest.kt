package com.eliormachlev.currencix.screenshots

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.view.compose.FULL_SUMMARY
import com.eliormachlev.currencix.view.compose.ReadingDirection
import com.eliormachlev.currencix.view.preference.compose.AboutProviderRow
import com.eliormachlev.currencix.view.preference.compose.PreferenceRow
import com.eliormachlev.currencix.view.preference.compose.PreferenceSection
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
// tests exist to lock the *visual* composition, we reconstruct the six
// sections using the same PreferenceSection / PreferenceRow / SwitchRow
// primitives the real screen composes — matching CartScreenshotTest's
// pattern of bypassing the VM wrapper.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5, application = Application::class)
class PreferenceScreenScreenshotTest {
    @get:Rule val shots = ScreenshotRule()

    @Test fun preferenceScreen() = shots.captureMatrix("preference_screen") { PreferenceScreenPreview() }

    @Test fun preferenceScreenLargeFont() = shots.captureLargeFont("preference_screen") { PreferenceScreenPreview() }
}

// Settings is laid out the way the language reads (ProseTheme in the app).
@Composable
private fun PreferenceScreenPreview() = ReadingDirection { PreferenceSections() }

@Composable
private fun PreferenceSections() {
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
        SwitchRow(
            title = stringResource(id = R.string.keyboard_title),
            summary = stringResource(id = R.string.keyboard_summary_expanded),
            iconRes = R.drawable.ic_keyboard_extended,
            checked = false,
            onCheckedChange = {},
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
            summary = SAMPLE_PROVIDER.getName(LocalContext.current).toString(),
            iconRes = R.drawable.ic_data_provider,
        )
        // The real row: its description and refresh cadence come from the
        // provider's own (translated) strings.
        AboutProviderRow(SAMPLE_PROVIDER)
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
            summary = stringResource(id = R.string.disclaimer_summary),
            summaryMaxLines = FULL_SUMMARY,
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
            summary = versionSummary(LocalContext.current, SAMPLE_YEAR.toInt()),
            iconRes = R.drawable.ic_tag,
        )
    }
}

private val SAMPLE_PROVIDER = ApiProvider.FRANKFURTER_APP
private const val SAMPLE_DATE_FORMAT = "dd/MM/yy HH:mm"
private const val SAMPLE_VERSION = "1.0.0"
private const val SAMPLE_YEAR = "2026"
