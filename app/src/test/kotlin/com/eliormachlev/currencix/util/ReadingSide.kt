package com.eliormachlev.currencix.util

import android.app.Application
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue

// Assertions for right-to-left tests: where an element sits across its
// container, in terms of the app language's reading direction. The language
// is the test's Robolectric qualifiers (@Config(qualifiers = "iw") for
// Hebrew), so one test body can run in Hebrew and in English and check the
// same thing: "the title starts where the language reads from".

/** The horizontal middle of a rectangle. */
internal val DpRect.centerX: Dp get() = (left + right) / 2

/** Whether the test runs in a right-to-left language. */
internal fun testLanguageIsRtl(): Boolean = isRtlLanguage(ApplicationProvider.getApplicationContext<Application>())

/**
 * Asserts this node sits on the side its language starts reading from:
 * the right half of [container] in a right-to-left language, the left half
 * otherwise.
 */
internal fun SemanticsNodeInteraction.assertOnReadingStartSide(container: SemanticsNodeInteraction) =
    assertSide(getBoundsInRoot(), container.getBoundsInRoot(), onRight = testLanguageIsRtl())

/** Asserts this node sits on the side its language ends reading on: the opposite of [assertOnReadingStartSide]. */
internal fun SemanticsNodeInteraction.assertOnReadingEndSide(container: SemanticsNodeInteraction) =
    assertSide(getBoundsInRoot(), container.getBoundsInRoot(), onRight = !testLanguageIsRtl())

/** Asserts this node sits on the right half of [container], whatever the language. */
internal fun SemanticsNodeInteraction.assertOnRightOf(container: SemanticsNodeInteraction) =
    assertSide(getBoundsInRoot(), container.getBoundsInRoot(), onRight = true)

/** Asserts this node sits on the left half of [container], whatever the language. */
internal fun SemanticsNodeInteraction.assertOnLeftOf(container: SemanticsNodeInteraction) =
    assertSide(getBoundsInRoot(), container.getBoundsInRoot(), onRight = false)

private fun assertSide(
    node: DpRect,
    container: DpRect,
    onRight: Boolean,
) {
    val side = if (onRight) "right" else "left"
    val onThatSide = if (onRight) node.centerX > container.centerX else node.centerX < container.centerX
    assertTrue("expected on the $side: $node in $container", onThatSide)
}
