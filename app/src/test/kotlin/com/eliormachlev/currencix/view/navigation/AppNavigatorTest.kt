package com.eliormachlev.currencix.view.navigation

import androidx.compose.runtime.saveable.SaverScope
import com.eliormachlev.currencix.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppNavigatorTest {
    private val timeline = Screen.Timeline(Currency.EUR, Currency.USD)
    private val cart = Screen.Cart(Currency.ILS, null)

    @Test
    fun `every screen survives an encode - decode round trip`() {
        val screens =
            listOf(
                Screen.Converter,
                timeline,
                cart,
                Screen.Cart(null, null),
                Screen.Settings,
                Screen.Fees,
                Screen.Backup,
            )
        screens.forEach { screen -> assertEquals(screen, decodeScreen(screen.encode())) }
    }

    @Test
    fun `a screen that can no longer be read is dropped, not guessed`() {
        assertNull(decodeScreen("unknown-route"))
        assertNull(decodeScreen("timeline:EUR"))
        assertNull(decodeScreen("timeline:EUR:XXX"))
    }

    @Test
    fun `navigate pushes and pop returns, never below the converter`() {
        val navigator = AppNavigator(listOf(Screen.Converter))
        navigator.navigate(Screen.Settings)
        navigator.navigate(Screen.Fees)
        assertEquals(listOf(Screen.Converter, Screen.Settings, Screen.Fees), navigator.backStack)

        navigator.pop()
        navigator.pop()
        navigator.pop()
        assertEquals(listOf(Screen.Converter), navigator.backStack)
        assertEquals(Screen.Converter, navigator.current)
    }

    @Test
    fun `navigating to a screen already on the stack pops back to it`() {
        val navigator = AppNavigator(listOf(Screen.Converter))
        navigator.navigate(Screen.Settings)
        navigator.navigate(Screen.Fees)
        navigator.navigate(Screen.Backup)

        navigator.navigate(Screen.Fees)

        assertEquals(listOf(Screen.Converter, Screen.Settings, Screen.Fees), navigator.backStack)
    }

    @Test
    fun `a restored stack is anchored on exactly one converter`() {
        val restored = AppNavigator(listOf(Screen.Settings, Screen.Converter, cart, cart))
        assertEquals(listOf(Screen.Converter, Screen.Settings, cart), restored.backStack)
    }

    @Test
    fun `the saver restores the same stack`() {
        val navigator = AppNavigator(listOf(Screen.Converter))
        navigator.navigate(timeline)
        navigator.navigate(cart)

        val scope = SaverScope { true }
        val saved = with(AppNavigator.Saver) { scope.save(navigator) }!!
        val restored = AppNavigator.Saver.restore(saved)!!

        assertEquals(navigator.backStack.toList(), restored.backStack.toList())
    }

    @Test
    fun `replaceTop swaps the top screen without adding history`() {
        val navigator = AppNavigator(listOf(Screen.Converter))
        navigator.navigate(timeline)

        navigator.replaceTop(cart)
        assertEquals(listOf(Screen.Converter, cart), navigator.backStack)

        navigator.pop()
        assertEquals(listOf(Screen.Converter), navigator.backStack)
    }

    @Test
    fun `replaceTop never replaces the converter and drops a lower copy of the new screen`() {
        val navigator = AppNavigator(listOf(Screen.Converter))
        navigator.replaceTop(timeline)
        assertEquals(listOf(Screen.Converter), navigator.backStack)

        navigator.navigate(timeline)
        navigator.navigate(cart)
        navigator.replaceTop(timeline)
        assertEquals(listOf(Screen.Converter, timeline), navigator.backStack)
    }

    @Test
    fun `only the timeline and the cart open beside the converter`() {
        assertEquals(PaneRole.List, Screen.Converter.paneRole)
        assertEquals(PaneRole.Detail, timeline.paneRole)
        assertEquals(PaneRole.Detail, cart.paneRole)
        listOf(Screen.Settings, Screen.Fees, Screen.Backup).forEach { assertNull(it.paneRole) }
    }
}
