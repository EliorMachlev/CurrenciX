package com.eliormachlev.currencix.view.compose

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.eliormachlev.currencix.util.registerActivityRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Counts how often the holder is built, and what it was built from.
class Held(
    val application: Application,
    val key: Int,
) {
    init {
        built++
    }

    companion object {
        var built = 0
    }
}

class RetainHostActivity : ComponentActivity() {
    var key by mutableIntStateOf(0)
    var held: Held? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { held = retainWithApplication(key) { Held(it, key) } }
    }
}

@RunWith(RobolectricTestRunner::class)
// A plain Application: the real one starts WorkManager and rate refreshes.
@Config(sdk = [34], application = Application::class)
class RetainWithApplicationTest {
    private val registerActivity =
        registerActivityRule(RetainHostActivity::class.java) { Held.built = 0 }

    private val compose = createAndroidComposeRule<RetainHostActivity>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(registerActivity).around(compose)

    @Test
    fun `the holder is built from the application and kept while the keys stay the same`() {
        compose.waitForIdle()
        val first = compose.activity.held
        assertSame(compose.activity.applicationContext, first?.application)

        compose.runOnIdle { compose.activity.key = 0 }
        compose.waitForIdle()
        assertEquals(1, Held.built)
        assertSame(first, compose.activity.held)
    }

    @Test
    fun `the holder is built again when a key changes`() {
        compose.waitForIdle()

        compose.runOnIdle { compose.activity.key = 1 }
        compose.waitForIdle()

        assertEquals(2, Held.built)
        assertEquals(1, compose.activity.held?.key)
    }
}
