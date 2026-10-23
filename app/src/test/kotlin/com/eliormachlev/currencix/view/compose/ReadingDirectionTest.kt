package com.eliormachlev.currencix.view.compose

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.util.assertOnReadingStartSide
import com.eliormachlev.currencix.util.registerActivityRule
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.compose.dialogs.LedgerConfirmSheet
import com.eliormachlev.currencix.view.compose.dialogs.LedgerPasswordSheet
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The app's prompts (plain, confirm and password sheets) follow the
// app language's direction: their title starts where the language reads
// from, on the right in Hebrew and on the left in English. The whole
// activity runs in the language (qualifiers), as on a phone, since a popup's
// window takes its resources from the activity.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReadingDirectionTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(registerActivityRule(ComponentActivity::class.java)).around(compose)

    private fun sheet() = show { LedgerBottomSheet(title = TITLE, onDismiss = {}) { Text(BODY) } }

    private fun confirmSheet() =
        show { LedgerConfirmSheet(title = TITLE, message = BODY, confirmLabel = "OK", onConfirm = {}, onDismiss = {}) }

    private fun passwordSheet() =
        showTitled(
            popup = {
                LedgerPasswordSheet(
                    titleRes = R.string.backup_password_prompt_title,
                    confirmLabelRes = android.R.string.ok,
                    onConfirm = {},
                    onDismiss = {},
                )
            },
            title = ApplicationProvider.getApplicationContext<Application>().getString(R.string.backup_password_prompt_title),
        )

    // Settles twice: the popup's window attaches first, then it enters. The
    // sheet's title is shown in small caps (upper case).
    private fun show(popup: @Composable () -> Unit) = showTitled(popup, TITLE)

    private fun showTitled(
        popup: @Composable () -> Unit,
        title: String,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent(popup)
        repeat(2) {
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        }
        compose.onNodeWithText(title, ignoreCase = true).assertOnReadingStartSide(compose.onNode(isDialog()))
    }

    @Test
    @Config(qualifiers = "iw")
    fun `in Hebrew a sheet's title sits on the right`() = sheet()

    @Test
    @Config(qualifiers = "en")
    fun `in English a sheet's title sits on the left`() = sheet()

    @Test
    @Config(qualifiers = "iw")
    fun `in Hebrew a confirm sheet's title sits on the right`() = confirmSheet()

    @Test
    @Config(qualifiers = "en")
    fun `in English a confirm sheet's title sits on the left`() = confirmSheet()

    @Test
    @Config(qualifiers = "iw")
    fun `in Hebrew a password sheet's title sits on the right`() = passwordSheet()

    @Test
    @Config(qualifiers = "en")
    fun `in English a password sheet's title sits on the left`() = passwordSheet()
}

private const val TITLE = "Title"
private const val BODY = "Body"
private const val SETTLE_MILLIS = 1_000L
