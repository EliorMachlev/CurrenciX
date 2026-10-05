package com.eliormachlev.currencix.view.main

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.eliormachlev.currencix.util.registerActivityRule
import com.eliormachlev.currencix.view.compose.AppTheme
import com.eliormachlev.currencix.view.main.compose.ConverterBody
import com.eliormachlev.currencix.view.main.compose.DrawerControl
import com.eliormachlev.currencix.view.main.compose.MainKeypad
import com.eliormachlev.currencix.view.main.compose.MainKeypadCallbacks
import com.eliormachlev.currencix.view.main.compose.MainScreen
import com.eliormachlev.currencix.view.main.compose.keypadHeights
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The keypad gives up row height so the display (hero card and the recent
// pairs under it) shows in full; only content taller than even a compact
// keypad leaves has to scroll.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h700dp")
class MainScreenLayoutTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(registerActivityRule(ComponentActivity::class.java)).around(compose)

    private val heights = keypadHeights(isExpandedKeypad = true)
    private var contentHeight by mutableStateOf(LAST_LINE_HEIGHT)

    @Before
    fun setContent() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AppTheme {
                MainScreen(
                    drawer = DrawerControl(rememberDrawerState(DrawerValue.Closed), onItem = {}),
                    body = ConverterBody(isRefreshing = false, onRefresh = {}, foldingFeature = null, keypadHeights = heights),
                    topBar = {},
                    displayContent = {
                        Spacer(Modifier.fillMaxWidth().height(contentHeight - LAST_LINE_HEIGHT))
                        Box(Modifier.fillMaxWidth().height(LAST_LINE_HEIGHT).testTag(LAST_LINE))
                    },
                    keypadContent = {
                        MainKeypad(
                            isExpandedKeypad = true,
                            nextParen = '(',
                            callbacks = MainKeypadCallbacks({}, {}, {}, {}, {}, {}, {}),
                            modifier = Modifier.testTag(KEYPAD),
                        )
                    },
                )
            }
        }
        settle()
    }

    @Test
    fun `short content leaves the keypad full size`() {
        show(contentHeight = 100.dp)
        compose.onNodeWithTag(KEYPAD).assertHeightIsEqualTo(heights.full)
        assertTrue("the keypad sits at the bottom", keypadBottom() == screenHeight())
    }

    @Test
    fun `content a full keypad would cover shrinks the keypad instead`() {
        val squeeze = 20.dp
        show(contentHeight = screenHeight() - heights.full + squeeze)
        compose.onNodeWithTag(KEYPAD).assertHeightIsEqualTo(heights.full - squeeze)
        assertTrue("the display's last line is above the keypad", lastLineBottom() <= keypadTop())
    }

    @Test
    fun `the keypad never goes below its compact size`() {
        show(contentHeight = screenHeight())
        compose.onNodeWithTag(KEYPAD).assertHeightIsEqualTo(heights.compact)
        assertTrue("the rest of the display scrolls under the keypad", lastLineBottom() > keypadTop())
    }

    private fun show(contentHeight: Dp) {
        this.contentHeight = contentHeight
        settle()
    }

    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
    }

    private fun screenHeight(): Dp = compose.onRoot().getUnclippedBoundsInRoot().height

    private fun lastLineBottom(): Dp = compose.onNodeWithTag(LAST_LINE).getUnclippedBoundsInRoot().bottom

    private fun keypadTop(): Dp = compose.onNodeWithTag(KEYPAD).getUnclippedBoundsInRoot().top

    private fun keypadBottom(): Dp = compose.onNodeWithTag(KEYPAD).getUnclippedBoundsInRoot().bottom

    private companion object {
        const val KEYPAD = "keypad"
        const val LAST_LINE = "last-line"
        const val SETTLE_MILLIS = 1_000L
        val LAST_LINE_HEIGHT = 10.dp
    }
}
