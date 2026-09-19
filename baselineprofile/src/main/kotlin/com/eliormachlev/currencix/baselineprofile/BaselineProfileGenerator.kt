package com.eliormachlev.currencix.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline-profile generator that captures the cold-start critical path of
 * CurrenciX: launcher tap → wait for first frame → gesture-scroll the hero
 * → open + close the navigation drawer.
 *
 * The captured trace is written under
 * `app/src/<variant>/generated/baselineProfiles/baseline-prof.txt` (and
 * `startup-prof.txt`) by the `androidx.baselineprofile` Gradle plugin, baked
 * into the release APK by `ProfileInstaller`, and delivered ahead-of-time by
 * ART so first-frame + first-interaction paths are pre-compiled.
 *
 * Keep the journey short — profiles should reflect the *critical* hot code
 * paths, not exhaustive UI coverage; noise dilutes the profile budget.
 * Gesture-based interactions (swipe / back) are used instead of by-tag lookups
 * so this generator stays green even as Compose test-tags evolve.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val targetPackage =
            InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: DEFAULT_TARGET_PACKAGE

        rule.collect(packageName = targetPackage) {
            pressHome()
            startActivityAndWait()

            // Let the first frame settle — capturing before content is on
            // screen produces a profile dominated by inflation rather than the
            // real interaction hot paths.
            device.waitForIdle(IDLE_TIMEOUT_MS)

            // Swipe up on the hero region — exercises the primary Compose
            // recomposition + scroll pipeline (LazyList measure + BigDecimal
            // rate formatting on visible rows).
            val width = device.displayWidth
            val height = device.displayHeight
            device.swipe(
                width / 2,
                (height * SWIPE_START_FRAC).toInt(),
                width / 2,
                (height * SWIPE_END_FRAC).toInt(),
                SWIPE_STEPS,
            )
            device.waitForIdle(IDLE_TIMEOUT_MS)

            // Drawer open (edge-swipe from left) + close — warms the drawer
            // scaffold + navigation graph, which sits behind the very first
            // user tap after cold start.
            device.swipe(
                0,
                height / 2,
                width * DRAWER_WIDTH_FRAC / 100,
                height / 2,
                SWIPE_STEPS,
            )
            device.waitForIdle(IDLE_TIMEOUT_MS)
            device.pressBack()
            device.waitForIdle(IDLE_TIMEOUT_MS)
        }
    }

    private companion object {
        // Fallback target when the instrumentation argument isn't wired —
        // matches the applicationId in app/build.gradle.kts. The Gradle plugin
        // injects the resolved id at runtime; this default keeps ad-hoc
        // `connectedCheck` runs from an IDE working out of the box.
        const val DEFAULT_TARGET_PACKAGE = "com.eliormachlev.currencix"

        // 5 s idle-wait is generous for a CI emulator's post-gesture settle;
        // long enough to be resilient, short enough to fail loudly if the
        // app never surfaces content.
        const val IDLE_TIMEOUT_MS = 5_000L

        // Swipe span — from 80 % down to 20 % down of the screen, i.e. a
        // clear upward flick over the hero card region.
        const val SWIPE_START_FRAC = 0.8
        const val SWIPE_END_FRAC = 0.2

        // Edge-swipe covers ~40 % of screen width — enough to fully open a
        // Material navigation drawer on any typical screen size.
        const val DRAWER_WIDTH_FRAC = 40

        // Gesture step count; more steps → slower, more realistic swipe.
        const val SWIPE_STEPS = 20
    }
}
