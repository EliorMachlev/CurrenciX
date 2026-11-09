package com.eliormachlev.currencix.view.alert

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.AlertDirection
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.RateAlert
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.registerActivityRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.math.BigDecimal

// The rate-alert sheets: setting one (what it says, which way it points,
// when it can't be confirmed), the list of waiting alerts with its delete
// button, and saving an alert into the store.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RateAlertSheetsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(registerActivityRule(ComponentActivity::class.java)).around(compose)

    private val pair = CurrencyPair(Currency.USD, Currency.ILS)
    private val current = BigDecimal("3.5")

    // The stores outlive a test, so each starts from none.
    @Before fun reset() = clearStores()

    @After fun cleanUp() = clearStores()

    private fun clearStores() {
        val db = Database(app)
        db.alerts.alerts.set(emptyList())
        db.providers.autoRefresh.set(false)
    }

    // Settles twice: the sheet's window attaches first, then it enters.
    private fun show(content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent(content)
        repeat(2) {
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        }
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
    }

    private fun text(
        id: Int,
        vararg args: Any,
    ) = app.getString(id, *args)

    private fun setSheet(
        onSet: (RateAlert) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) = show { RateAlertSheet(pair = pair, current = current, onSet = onSet, onDismiss = onDismiss) }

    private fun enter(value: String) {
        compose.onNode(hasSetTextAction()).performTextReplacement(value)
        settle()
    }

    @Test
    fun `the sheet starts from today's rate and says where it is`() {
        setSheet()

        compose.onNode(hasSetTextAction()).assertExists()
        compose.onNodeWithText("3.5", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText(text(R.string.alert_sheet_background)).assertExists()
    }

    @Test
    fun `a lower rate reads as or less`() {
        setSheet()

        enter("3.2")

        compose.onNodeWithText("or less", substring = true).assertExists()
    }

    @Test
    fun `a higher rate reads as or more and confirms as an alert above`() {
        var saved: RateAlert? = null
        setSheet(onSet = { saved = it })

        enter("4")
        compose.onNodeWithText("or more", substring = true).assertExists()
        compose.onNodeWithText(text(R.string.alert_sheet_confirm), ignoreCase = true).performClick()
        settle()

        val alert = saved
        assertEquals(pair, alert?.pair)
        assertEquals(0, BigDecimal("4").compareTo(alert?.threshold))
        assertEquals(AlertDirection.ABOVE, alert?.direction)
    }

    @Test
    fun `an entry that is not a positive number cannot be confirmed`() {
        var saved: RateAlert? = null
        setSheet(onSet = { saved = it })

        enter("0")

        compose.onNodeWithText(text(R.string.error_positive_number)).assertExists()
        compose.onNodeWithText(text(R.string.alert_sheet_confirm), ignoreCase = true).assertIsNotEnabled()
        assertNull(saved)
    }

    @Test
    fun `a valid entry leaves the confirm button on`() {
        setSheet()

        compose.onNodeWithText(text(R.string.alert_sheet_confirm), ignoreCase = true).assertIsEnabled()
    }

    @Test
    fun `cancel dismisses the sheet`() {
        var dismissed = false
        setSheet(onDismiss = { dismissed = true })

        compose.onNodeWithText(text(android.R.string.cancel), ignoreCase = true).performClick()
        settle()

        assertTrue(dismissed)
    }

    @Test
    fun `the list says so when there are no alerts`() {
        show { RateAlertsSheet(onDismiss = {}) }

        compose.onNodeWithText(text(R.string.alerts_empty)).assertExists()
    }

    @Test
    fun `the list shows a waiting alert and deleting it removes it from the store`() {
        Database(app).alerts.add(RateAlert(id = "a1", pair = pair, threshold = BigDecimal("3.2"), direction = AlertDirection.BELOW))
        show { RateAlertsSheet(onDismiss = {}) }

        compose.onNodeWithText("3.2", substring = true).assertExists()

        compose.onNodeWithContentDescription(text(R.string.alert_delete)).performClick()
        settle()

        assertTrue(
            Database(app)
                .alerts.alerts
                .get()
                .isEmpty(),
        )
        compose.onNodeWithText(text(R.string.alerts_empty)).assertExists()
    }

    @Test
    fun `setting an alert stores it and turns background refresh on`() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val db = Database(app)
        assertFalse(db.providers.autoRefresh.get())
        lateinit var set: (RateAlert) -> Unit
        show { set = rememberSetRateAlert() }
        val alert = RateAlert(id = "a2", pair = pair, threshold = BigDecimal("4"), direction = AlertDirection.ABOVE)

        compose.runOnUiThread { set(alert) }
        settle()

        assertEquals(listOf(alert), db.alerts.alerts.get())
        assertTrue(db.providers.autoRefresh.get())
    }

    private companion object {
        const val SETTLE_MILLIS = 1_000L
    }
}
