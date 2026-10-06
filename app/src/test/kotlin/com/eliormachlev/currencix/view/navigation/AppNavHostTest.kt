package com.eliormachlev.currencix.view.navigation

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.registerActivityRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Counts ViewModels created and cleared, to check per-screen scoping.
class ProbeViewModel : ViewModel() {
    init {
        created++
    }

    override fun onCleared() {
        cleared++
    }

    companion object {
        var created = 0
        var cleared = 0
    }
}

// Hosts AppNavHost the way MainActivity does, with one Text + one
// ViewModel per screen.
class NavHostTestActivity : ComponentActivity() {
    lateinit var navigator: AppNavigator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            navigator = rememberAppNavigator()
            AppNavHost(navigator) { screen ->
                viewModel<ProbeViewModel>()
                Text(screen.encode())
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one starts WorkManager and rate refreshes.
@Config(sdk = [34], application = Application::class)
class AppNavHostTest {
    // The test Activity isn't in any manifest: register it before the compose
    // rule launches it.
    private val registerActivity =
        registerActivityRule(NavHostTestActivity::class.java) {
            ProbeViewModel.created = 0
            ProbeViewModel.cleared = 0
        }

    private val compose = createAndroidComposeRule<NavHostTestActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(registerActivity).around(compose)

    private val navigator get() = compose.activity.navigator

    @Test
    fun `pushing and popping swaps the visible screen`() {
        compose.onNodeWithText("converter").assertExists()

        compose.runOnIdle { navigator.navigate(Screen.Timeline(Currency.EUR, Currency.USD)) }
        compose.onNodeWithText("timeline:EUR:USD").assertExists()
        compose.onNodeWithText("converter").assertDoesNotExist()

        compose.runOnIdle { navigator.pop() }
        compose.onNodeWithText("converter").assertExists()
        compose.onNodeWithText("timeline:EUR:USD").assertDoesNotExist()
    }

    @Test
    fun `each screen gets its own ViewModel, cleared when the screen is popped`() {
        compose.runOnIdle { navigator.navigate(Screen.Settings) }
        compose.waitForIdle()
        assertEquals(2, ProbeViewModel.created)
        assertEquals(0, ProbeViewModel.cleared)

        compose.runOnIdle { navigator.pop() }
        compose.waitForIdle()
        assertEquals(1, ProbeViewModel.cleared)
    }

    @Test
    fun `system back pops the top screen`() {
        compose.runOnIdle { navigator.navigate(Screen.Settings) }
        compose.onNodeWithText("settings").assertExists()

        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("converter").assertExists()
    }

    @Test
    fun `the back stack and screen ViewModels survive recreation`() {
        compose.runOnIdle {
            navigator.navigate(Screen.Settings)
            navigator.navigate(Screen.Fees)
        }
        compose.waitForIdle()
        val createdBefore = ProbeViewModel.created

        compose.activityRule.scenario.recreate()

        compose.onNodeWithText("fees").assertExists()
        compose.runOnIdle {
            assertEquals(listOf(Screen.Converter, Screen.Settings, Screen.Fees), navigator.backStack.toList())
            // A configuration change keeps each screen's ViewModel.
            assertEquals(createdBefore, ProbeViewModel.created)
            assertEquals(0, ProbeViewModel.cleared)
        }
    }
}
