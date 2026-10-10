package com.eliormachlev.currencix.view.preference.compose

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performTextInput
import com.eliormachlev.currencix.util.registerActivityRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The fee percent field only ever holds a valid percent: either separator
// becomes the locale's (here "."), and input past the limits is cut from the
// tail rather than refused whole.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FeePercentFieldTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(registerActivityRule(ComponentActivity::class.java)).around(compose)

    private fun typed(input: String): String {
        val state = TextFieldState()
        compose.setContent { FeePercentField(state = state) }
        compose.onNode(hasSetTextAction()).performTextInput(input)
        compose.waitForIdle()
        return state.text.toString()
    }

    @Test
    fun `a comma becomes the locale's separator`() {
        assertEquals("12.5", typed("12,5"))
    }

    @Test
    fun `fraction digits past the limit are cut`() {
        assertEquals("1.234", typed("1.23456"))
    }

    @Test
    fun `a percent over 100 is cut back into range`() {
        assertEquals("15", typed("150"))
    }

    @Test
    fun `letters are refused`() {
        assertEquals("", typed("abc"))
    }
}
