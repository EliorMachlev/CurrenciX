package com.eliormachlev.currencix.view.cart.compose

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CartItem
import com.eliormachlev.currencix.util.registerActivityRule
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Pinning a row moves it to the top at once — not only after the cart is
// reopened (the list used to refresh the row in place without re-sorting).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CartPinTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(registerActivityRule(ComponentActivity::class.java)).around(compose)

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Application>().getString(id)

    @Test
    fun `pinning a row moves it to the top right away`() {
        val items =
            MutableLiveData<ImmutableList<CartItem>>(
                persistentListOf(
                    CartItem(id = "1", name = "Apples", expression = "1"),
                    CartItem(id = "2", name = "Bread", expression = "2"),
                ),
            )
        compose.setContent {
            CartItemsList(
                sources = cartSources(items),
                actions =
                    cartItemActions(onTogglePin = { id ->
                        items.value = items.value!!.map { if (it.id == id) it.copy(pinned = !it.pinned) else it }.toImmutableList()
                    }),
                reorder = NO_REORDER,
                onBackgroundTap = {},
            )
        }
        val pin = string(R.string.cart_pin_item)

        // Bread is the second row; pin it.
        compose.onAllNodesWithContentDescription(pin)[1].performClick()
        compose.waitForIdle()

        val bread = compose.onNodeWithText("Bread").getBoundsInRoot().top
        val apples = compose.onNodeWithText("Apples").getBoundsInRoot().top
        assertTrue("Bread should now be above Apples", bread < apples)
        // …in its own section, headed apart from the rest.
        compose.onNodeWithText(string(R.string.cart_section_pinned).uppercase()).assertExists()
        compose.onNodeWithText(string(R.string.cart_section_others).uppercase()).assertExists()
    }
}
