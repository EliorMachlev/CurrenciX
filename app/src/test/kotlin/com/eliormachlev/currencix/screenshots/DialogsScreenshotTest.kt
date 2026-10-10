package com.eliormachlev.currencix.screenshots

import android.app.Application
import androidx.compose.ui.res.stringResource
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.Language
import com.eliormachlev.currencix.view.preference.compose.LanguagePickerDialog
import com.eliormachlev.currencix.view.preference.compose.ProviderPickerDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Stateless picker dialogs rendered across the matrix. Each dialog wraps
// itself in AppTheme via ChoiceDialogFrame, but our MatrixCell also sets
// theme/locale up-stack — that's fine, the inner AppTheme is a no-op with
// the same palette.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5, application = Application::class)
class DialogsScreenshotTest {
    @get:Rule val shots = ScreenshotRule()

    @Test fun languagePicker() =
        shots.captureMatrix("dialog_language_picker") {
            LanguagePickerDialog(
                selected = Language.SYSTEM,
                onDismiss = {},
                onPicked = {},
            )
        }

    @Test fun providerPicker() =
        shots.captureMatrix("dialog_provider_picker") {
            ProviderPickerDialog(
                selected = ApiProvider.entries.first(),
                onDismiss = {},
                onPicked = {},
            )
        }

    @Test fun providerPickerLargeFont() =
        shots.captureLargeFont("dialog_provider_picker") {
            ProviderPickerDialog(
                selected = ApiProvider.entries.first(),
                onDismiss = {},
                onPicked = {},
            )
        }

    // The fallback picker: the main provider greyed out.
    @Test fun fallbackProviderPicker() =
        shots.captureMatrix("dialog_fallback_provider_picker") {
            ProviderPickerDialog(
                selected = ApiProvider.FRANKFURTER_APP,
                onDismiss = {},
                onPicked = {},
                title = stringResource(R.string.fallback_provider_title),
                unavailable = ApiProvider.BANK_OF_ISRAEL,
            )
        }
}
