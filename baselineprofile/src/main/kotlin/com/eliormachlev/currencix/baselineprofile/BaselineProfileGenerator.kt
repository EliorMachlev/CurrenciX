package com.eliormachlev.currencix.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates CurrenciX's baseline + startup profiles.
 *
 * The `androidx.baselineprofile` Gradle plugin writes the result to
 * `app/src/<variant>/generated/baselineProfiles/` (`baseline-prof.txt`,
 * `startup-prof.txt`); ProfileInstaller hands it to ART at install time so
 * these paths run precompiled from the very first launch instead of
 * interpreted / JIT-compiled.
 *
 * Two collections, because the startup profile also drives DEX layout —
 * it should hold the launch path only, while the baseline profile covers
 * the interactions users hit right after (see [Journeys]).
 *
 * Run in CI on an emulator (.github/workflows/baseline-profile.yaml) or
 * locally against a device: `./gradlew :app:generateFdroidReleaseBaselineProfile`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    /** Launch → first frame of the converter. */
    @Test
    fun startup() =
        rule.collect(packageName = targetPackage(), includeInStartupProfile = true) {
            launchToConverter()
        }

    /** The interactions right after launch: typing, picking a currency, moving between screens. */
    @Test
    fun journeys() =
        rule.collect(packageName = targetPackage()) {
            launchToConverter()
            typeOnKeypad(TYPED_AMOUNT)
            scrollCurrencyPicker()
            visitFromDrawer(UiTags.DRAWER_TIMELINE)
            visitFromDrawer(UiTags.DRAWER_CART)
        }
}
