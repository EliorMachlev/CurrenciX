package com.eliormachlev.currencix.view.compose

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.assertOnReadingStartSide
import com.eliormachlev.currencix.util.registerActivityRule
import com.eliormachlev.currencix.view.compose.onboarding.OnboardingAnchor
import com.eliormachlev.currencix.view.compose.onboarding.Spotlight
import com.eliormachlev.currencix.view.compose.onboarding.SpotlightStep
import com.eliormachlev.currencix.view.convert.ConvertTextSheet
import com.eliormachlev.currencix.view.convert.SelectionConversion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

// The app's other popups in Hebrew: the first-run tour card reads right to
// left, and the selected-text sheet does too while its rate line, which is
// math, stays left to right.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "iw")
class PopupReadingDirectionTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(registerActivityRule(ComponentActivity::class.java)).around(compose)

    private fun show(popup: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent(popup)
        repeat(2) {
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        }
    }

    @Test
    fun `the tour card's title sits on the right`() {
        show {
            Spotlight(
                steps = listOf(SpotlightStep(OnboardingAnchor.SwapFab, TITLE, BODY)),
                skipLabel = "Skip",
                nextLabel = "Next",
                finishLabel = "Done",
                onDismiss = {},
            )
        }
        compose.onNodeWithText(TITLE).assertOnReadingStartSide(compose.onNode(isPopup()))
    }

    @Test
    fun `the selected-text sheet keeps its rate line left to right`() {
        show {
            ConvertTextSheet(
                text = "€49.99",
                conversion =
                    SelectionConversion.Converted(
                        amount = BigDecimal("49.99"),
                        from = Currency.EUR,
                        to = Currency.ILS,
                        result = BigDecimal("184.21"),
                        rate = BigDecimal("3.6849"),
                        assumedFrom = false,
                    ),
                decimals = 2,
                onOpen = {},
                onDismiss = {},
            )
        }
        val sheet = compose.onNode(isDialog())
        val rate = compose.onNodeWithText(RATE_START, substring = true)
        // The line sits where Hebrew starts, on the right…
        rate.assertOnReadingStartSide(sheet)
        // …and is laid out left to right, so "1 EUR ≈ 3.6849 ILS" keeps its
        // "1" in front instead of carrying it to the far end.
        assertEquals(ResolvedTextDirection.Ltr, rate.textLayout().getParagraphDirection(0))
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }
}

private const val TITLE = "כותרת"
private const val BODY = "גוף"
private const val RATE_START = "1 EUR"
private const val SETTLE_MILLIS = 1_000L
