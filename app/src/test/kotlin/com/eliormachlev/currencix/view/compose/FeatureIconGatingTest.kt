package com.eliormachlev.currencix.view.compose

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.eliormachlev.currencix.util.registerActivityRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Feature icons are colored only with Material You on; otherwise they keep
// their plain color.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FeatureIconGatingTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(registerActivityRule(ComponentActivity::class.java)).around(compose)

    @Test
    fun `with Material You the icon takes the primary`() = assertEquals(PRIMARY, iconColor(dynamicColor = true))

    @Test
    fun `without it the icon keeps its plain color`() = assertEquals(PLAIN, iconColor(dynamicColor = false))

    private fun iconColor(dynamicColor: Boolean): Color {
        var color = Color.Unspecified
        compose.setContent {
            MaterialTheme(colorScheme = SCHEME) {
                CompositionLocalProvider(LocalDynamicColor provides dynamicColor) {
                    color = featureIconColor(plain = PLAIN)
                }
            }
        }
        compose.waitForIdle()
        return color
    }

    private companion object {
        // A primary dark enough to read on the background as it is.
        val PRIMARY = Color(0xFF2B5A9E)
        val PLAIN = Color(0xFF45464F)
        val SCHEME = lightColorScheme(primary = PRIMARY, background = Color.White, onBackground = Color.Black)
    }
}
