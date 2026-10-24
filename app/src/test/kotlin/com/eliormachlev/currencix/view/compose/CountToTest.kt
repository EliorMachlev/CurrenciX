package com.eliormachlev.currencix.view.compose

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.eliormachlev.currencix.util.registerActivityRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * The cart's total, as countTo shows it, ends on the real total whatever
 * order its inputs arrive in. It once stayed at 0.00 (the cart reopened,
 * or one item's price set) while the people's shares under it were right.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CountToTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(registerActivityRule(ComponentActivity::class.java)).around(compose)

    private var target by mutableStateOf<BigDecimal?>(null)
    private var key by mutableStateOf<Any?>(null)

    private fun show() =
        compose.setContent {
            Text("shown " + countTo(target, key)?.toPlainString())
        }

    @Test
    fun `the total and its currency arriving together end on the total`() {
        target = BigDecimal.ZERO
        show()
        compose.runOnIdle {
            target = BigDecimal("181.32")
            key = "EUR"
        }
        compose.onNodeWithText("shown 181.32").assertExists()
    }

    @Test
    fun `a total from zero in the same currency ends on the total`() {
        target = BigDecimal.ZERO
        key = "EUR"
        show()
        compose.runOnIdle { target = BigDecimal("0.89") }
        compose.onNodeWithText("shown 0.89").assertExists()
    }

    @Test
    fun `a changed total counts to the new one`() {
        target = BigDecimal("10")
        key = "EUR"
        show()
        compose.runOnIdle { target = BigDecimal("12") }
        compose.onNodeWithText("shown 12").assertExists()
    }
}
