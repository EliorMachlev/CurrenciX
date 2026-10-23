package com.eliormachlev.currencix.util

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.robolectric.Shadows.shadowOf

/**
 * Registers [activity] with Robolectric's package manager before the rule it
 * wraps launches it — test activities (and `ComponentActivity` itself, for
 * `createComposeRule`) aren't in the app's manifest. [before] runs just
 * ahead of the test, for per-test resets.
 *
 * Chain it outside the compose rule: `RuleChain.outerRule(this).around(compose)`.
 */
fun registerActivityRule(
    activity: Class<out Activity>,
    before: () -> Unit = {},
): TestRule =
    TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val app = ApplicationProvider.getApplicationContext<Application>()
                shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, activity))
                before()
                base.evaluate()
            }
        }
    }
