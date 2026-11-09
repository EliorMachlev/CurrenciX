package com.eliormachlev.currencix.view.cart

import android.app.Activity
import android.app.Application
import com.eliormachlev.currencix.viewmodel.main.KeyAvailability
import com.eliormachlev.currencix.viewmodel.main.Operator
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The cart's floating keypad: which row it edits, the expression it shows
// as keys are pressed, and when that expression is handed back to the cart.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CartKeypadControllerTest {
    private val commits = mutableListOf<Pair<String, String>>()
    private var nextCount = 0

    private val controller =
        CartKeypadController(
            activity = Robolectric.buildActivity(Activity::class.java).create().get(),
            isExpandedKeypad = MutableStateFlow(false),
            onExpressionCommit = { id, expression -> commits += id to expression },
            onNext = { nextCount++ },
        )

    private val live get() = controller.liveExpression.value

    // The calculation row spaces its operators ("12 + 3"); the spacing isn't what's tested.
    private fun unspaced(text: String) = text.replace(" ", "")

    @Test
    fun `nothing is being edited to begin with`() {
        assertNull(controller.activeItemId.value)
        assertEquals("", live)
        assertFalse(controller.keypadVisible.value)
    }

    @Test
    fun `opening a row shows the keypad and its saved price`() {
        controller.openKeypadFor("row1", "25")

        assertEquals("row1", controller.activeItemId.value)
        assertTrue(controller.keypadVisible.value)
        assertEquals("25", live)
    }

    @Test
    fun `an empty row starts at zero`() {
        controller.openKeypadFor("row1", "  ")

        assertEquals("0", live)
    }

    @Test
    fun `a saved calculation is picked up where it was left`() {
        controller.openKeypadFor("row1", "12+3")

        assertEquals("12+3", unspaced(live))
    }

    @Test
    fun `a saved calculation with brackets and a decimal is rebuilt`() {
        controller.openKeypadFor("row1", "(1.5+2)")

        assertEquals("(1.5+2)", unspaced(live))
    }

    @Test
    fun `digits are typed and deleted`() {
        controller.openKeypadFor("row1", "25")

        controller.keypadCallbacks.onDigit("7")
        assertEquals("257", live)

        controller.keypadCallbacks.onDelete()
        assertEquals("25", live)
    }

    @Test
    fun `an operator turns the price into a calculation and equals works it out`() {
        controller.openKeypadFor("row1", "12")

        controller.keypadCallbacks.onOperator(Operator.PLUS)
        controller.keypadCallbacks.onDigit("3")
        assertEquals("12+3", unspaced(live))

        controller.keypadCallbacks.onEquals()
        assertEquals("15", live)
    }

    @Test
    fun `a decimal point is typed`() {
        controller.openKeypadFor("row1", "2")

        controller.keypadCallbacks.onDecimal()
        controller.keypadCallbacks.onDigit("5")

        assertEquals("2.5", live)
    }

    @Test
    fun `clear starts over`() {
        controller.openKeypadFor("row1", "25")

        controller.keypadCallbacks.onClear()

        assertEquals("0", live)
    }

    @Test
    fun `brackets are typed into a calculation`() {
        controller.openKeypadFor("row1", "50")
        controller.keypadCallbacks.onOperator(Operator.PLUS)
        controller.keypadCallbacks.onOpenParen()
        controller.keypadCallbacks.onDigit("2")
        controller.keypadCallbacks.onCloseParen()

        assertEquals("50+(2)", unspaced(live))
    }

    @Test
    fun `percent follows a number, and not a closing bracket`() {
        controller.openKeypadFor("row1", "50")
        assertTrue(controller.keypadKeys.value.percent)

        controller.keypadCallbacks.onPercent()
        assertEquals("50%", live)

        controller.keypadCallbacks.onOperator(Operator.PLUS)
        controller.keypadCallbacks.onOpenParen()
        controller.keypadCallbacks.onDigit("2")
        controller.keypadCallbacks.onCloseParen()
        assertFalse(controller.keypadKeys.value.percent)
    }

    @Test
    fun `key presses do nothing while no row is open`() {
        controller.keypadCallbacks.onDigit("7")
        controller.keypadCallbacks.onOperator(Operator.PLUS)
        controller.keypadCallbacks.onEquals()

        assertEquals("", live)
        assertTrue(commits.isEmpty())
    }

    @Test
    fun `flushing commits the buffered expression and keeps the row open`() {
        controller.openKeypadFor("row1", "25")
        controller.keypadCallbacks.onDigit("7")

        controller.flushActiveExpression()

        assertEquals(listOf("row1" to "257"), commits)
        assertEquals("row1", controller.activeItemId.value)
    }

    @Test
    fun `flushing with no row open commits nothing`() {
        controller.flushActiveExpression()

        assertTrue(commits.isEmpty())
    }

    @Test
    fun `closing commits the row, hides the keypad and clears the keys`() {
        controller.openKeypadFor("row1", "25")
        controller.keypadCallbacks.onDigit("7")

        controller.closeKeypad()

        assertEquals(listOf("row1" to "257"), commits)
        assertFalse(controller.keypadVisible.value)
        assertNull(controller.activeItemId.value)
        assertEquals("", live)
        assertEquals(KeyAvailability(), controller.keypadKeys.value)
    }

    @Test
    fun `closing when nothing is open commits nothing`() {
        controller.closeKeypad()

        assertTrue(commits.isEmpty())
    }

    @Test
    fun `opening another row commits the first one`() {
        controller.openKeypadFor("row1", "25")

        controller.openKeypadFor("row2", "9")

        assertEquals(listOf("row1" to "25"), commits)
        assertEquals("row2", controller.activeItemId.value)
        assertEquals("9", live)
    }

    @Test
    fun `dismissing the keyboards closes an open keypad`() {
        controller.openKeypadFor("row1", "25")

        controller.dismissKeyboards()

        assertFalse(controller.keypadVisible.value)
        assertNull(controller.activeItemId.value)
        assertEquals(listOf("row1" to "25"), commits)
    }

    @Test
    fun `dismissing the keyboards when nothing is open is harmless`() {
        controller.dismissKeyboards()

        assertTrue(commits.isEmpty())
        assertFalse(controller.keypadVisible.value)
    }

    @Test
    fun `the next key is passed through`() {
        controller.onNext()
        controller.onNext()

        assertEquals(2, nextCount)
    }
}
