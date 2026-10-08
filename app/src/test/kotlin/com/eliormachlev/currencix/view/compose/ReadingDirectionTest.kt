package com.eliormachlev.currencix.view.compose

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import com.eliormachlev.currencix.util.registerActivityRule
import com.eliormachlev.currencix.view.compose.dialogs.LedgerBottomSheet
import com.eliormachlev.currencix.view.compose.dialogs.LedgerConfirmDialog
import com.eliormachlev.currencix.view.compose.dialogs.ProseAlertDialog
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The prose popups (sheets, ledger and Material-style dialogs) follow the
// app language's direction: in Hebrew their title sits on the right, in
// English on the left. The whole activity runs in the language (qualifiers),
// as on a phone, since a popup's window takes its resources from the
// activity.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReadingDirectionTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(registerActivityRule(ComponentActivity::class.java)).around(compose)

    private fun showSheet() =
        compose.setContent {
            LedgerBottomSheet(title = TITLE, onDismiss = {}) { Text(BODY) }
        }

    private fun showDialog() =
        compose.setContent {
            LedgerConfirmDialog(title = TITLE, message = BODY, confirmLabel = "OK", onConfirm = {}, onDismiss = {})
        }

    private fun showAlert() =
        compose.setContent {
            AppTheme {
                ProseAlertDialog(
                    onDismissRequest = {},
                    confirmButton = { Text("OK") },
                    title = { Text(TITLE) },
                    text = { Text(BODY) },
                )
            }
        }

    // Settles twice: the popup's window attaches first, then it enters.
    private fun settle() =
        repeat(2) {
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        }

    private fun titleAndWindow(): Pair<DpRect, DpRect> {
        compose.mainClock.autoAdvance = false
        settle()
        // The sheet's title is shown in small caps (upper case).
        val title = compose.onNodeWithText(TITLE, ignoreCase = true).getBoundsInRoot()
        val window = compose.onNode(isDialog()).getBoundsInRoot()
        return title to window
    }

    private fun DpRect.centerX() = (left + right) / 2

    @Test
    @Config(qualifiers = "iw")
    fun `in Hebrew a sheet's title sits on the right`() {
        showSheet()
        val (title, window) = titleAndWindow()
        assertTrue("title at $title in $window", title.centerX() > window.centerX())
    }

    @Test
    @Config(qualifiers = "en")
    fun `in English a sheet's title sits on the left`() {
        showSheet()
        val (title, window) = titleAndWindow()
        assertTrue("title at $title in $window", title.centerX() < window.centerX())
    }

    @Test
    @Config(qualifiers = "iw")
    fun `in Hebrew a dialog's title sits on the right`() {
        showDialog()
        val (title, window) = titleAndWindow()
        assertTrue("title at $title in $window", title.centerX() > window.centerX())
    }

    @Test
    @Config(qualifiers = "en")
    fun `in English a dialog's title sits on the left`() {
        showDialog()
        val (title, window) = titleAndWindow()
        assertTrue("title at $title in $window", title.centerX() < window.centerX())
    }

    @Test
    @Config(qualifiers = "iw")
    fun `in Hebrew a Material-style dialog's title sits on the right`() {
        showAlert()
        val (title, window) = titleAndWindow()
        assertTrue("title at $title in $window", title.centerX() > window.centerX())
    }

    @Test
    @Config(qualifiers = "en")
    fun `in English a Material-style dialog's title sits on the left`() {
        showAlert()
        val (title, window) = titleAndWindow()
        assertTrue("title at $title in $window", title.centerX() < window.centerX())
    }
}

private const val TITLE = "Title"
private const val BODY = "Body"
private const val SETTLE_MILLIS = 1_000L
