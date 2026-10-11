package com.eliormachlev.currencix.view.cart.compose

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performTextInput
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.util.registerActivityRule
import com.eliormachlev.currencix.view.compose.MAX_NAME_LENGTH
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A cart row's name field: TalkBack always has its name as the field's hint,
// and a paste longer than a name may be is cut to fit.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CartItemNameFieldTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(registerActivityRule(ComponentActivity::class.java)).around(compose)

    private fun nameField(name: String = ""): SemanticsNodeInteraction {
        val item = CartItem(id = "1", name = name, expression = "", pinned = false)
        compose.setContent { CartItemRow(state = CartRowState(item, "USD"), actions = NO_ROW_ACTIONS) }
        return compose.onNode(hasSetTextAction())
    }

    private fun SemanticsNodeInteraction.typed(): String = fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun SemanticsNodeInteraction.hint(): String = fetchSemanticsNode().config[SemanticsProperties.HintText]

    @Test
    fun `an empty field is named by its hint`() {
        assertEquals(ITEM_NAME, nameField().hint())
    }

    @Test
    fun `a filled field keeps its hint`() {
        assertEquals(ITEM_NAME, nameField("Avocados").hint())
    }

    @Test
    fun `a long paste is cut to the longest name`() {
        val field = nameField()

        field.performTextInput("x".repeat(MAX_NAME_LENGTH + 10))

        assertEquals("x".repeat(MAX_NAME_LENGTH), field.typed())
    }

    private companion object {
        const val ITEM_NAME = "Item name"
    }
}
