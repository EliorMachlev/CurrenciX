package com.eliormachlev.currencix.view.compose

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.eliormachlev.currencix.util.assertOnReadingStartSide
import com.eliormachlev.currencix.util.registerActivityRule
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.compose.dialogs.LedgerConfirmSheet
import com.eliormachlev.currencix.view.compose.dialogs.LedgerPasswordDialog
import com.eliormachlev.currencix.view.compose.dialogs.ProseAlertDialog
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The prose popups (sheets, ledger and Material-style dialogs) follow the
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

    private fun ledgerDialog() = show { LedgerPasswordDialog(title = TITLE, confirmLabel = "OK", onConfirm = {}, onDismiss = {}) }

    private fun materialDialog() =
        show {
            AppTheme {
                ProseAlertDialog(
                    onDismissRequest = {},
                    confirmButton = { Text("OK") },
                    title = { Text(TITLE) },
                    text = { Text(BODY) },
                )
            }
        }

    // Settles twice: the popup's window attaches first, then it enters. The
    // sheet's title is shown in small caps (upper case).
    private fun show(popup: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent(popup)
        repeat(2) {
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        }
        compose.onNodeWithText(TITLE, ignoreCase = true).assertOnReadingStartSide(compose.onNode(isDialog()))
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
    fun `in Hebrew a dialog's title sits on the right`() = ledgerDialog()

    @Test
    @Config(qualifiers = "en")
    fun `in English a dialog's title sits on the left`() = ledgerDialog()

    @Test
    @Config(qualifiers = "iw")
    fun `in Hebrew a Material-style dialog's title sits on the right`() = materialDialog()

    @Test
    @Config(qualifiers = "en")
    fun `in English a Material-style dialog's title sits on the left`() = materialDialog()
}

private const val TITLE = "Title"
private const val BODY = "Body"
private const val SETTLE_MILLIS = 1_000L
