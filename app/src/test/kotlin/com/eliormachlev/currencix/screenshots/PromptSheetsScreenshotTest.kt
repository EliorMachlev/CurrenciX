package com.eliormachlev.currencix.screenshots

import android.app.Application
import androidx.compose.ui.res.stringResource
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.view.cart.compose.CartNameInputSheet
import com.eliormachlev.currencix.view.compose.dialogs.LedgerPasswordSheet
import com.eliormachlev.currencix.view.preference.compose.TextEntrySheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The prompts that take typed input, as sheets: a cart's name, a password
// (with the wrong-password error under it), and a settings text entry with
// its explanation.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5, application = Application::class)
class PromptSheetsScreenshotTest {
    @get:Rule val shots = ScreenshotRule()

    @Test fun cartName() =
        shots.captureMatrix("prompt_cart_name") {
            CartNameInputSheet(titleRes = R.string.cart_menu_save_as, initial = SAMPLE_CART_NAME, onOk = {}, onDismiss = {})
        }

    @Test fun passwordRetry() =
        shots.captureMatrix("prompt_password_retry") {
            LedgerPasswordSheet(
                titleRes = R.string.backup_password_prompt_title,
                confirmLabelRes = android.R.string.ok,
                errorText = stringResource(R.string.backup_password_wrong),
                onConfirm = {},
                onDismiss = {},
            )
        }

    @Test fun textEntry() =
        shots.captureMatrix("prompt_text_entry") {
            TextEntrySheet(
                title = stringResource(R.string.api_open_exchangerates_api_key_title),
                initialText = "",
                message = stringResource(R.string.api_open_exchangerates_api_key_message),
                onDismiss = {},
                onConfirm = {},
            )
        }

    @Test fun cartNameLargeFont() =
        shots.captureLargeFont("prompt_cart_name") {
            CartNameInputSheet(titleRes = R.string.cart_menu_save_as, initial = SAMPLE_CART_NAME, onOk = {}, onDismiss = {})
        }
}

private const val SAMPLE_CART_NAME = "Trip to Lisbon"
