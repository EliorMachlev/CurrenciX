package com.eliormachlev.currencix.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

// Few iterations: these run on a CI emulator, where absolute numbers are
// indicative only — the point is the with/without-profile comparison and
// catching large regressions, not precise timings.
private const val ITERATIONS = 5

/**
 * Frame timing for the app's hot interactions plus cold-start time, each
 * measured with and without the baseline profile. Uses the same [Journeys]
 * as [BaselineProfileGenerator], so the profile is measured on exactly the
 * paths it was generated from.
 *
 * FrameTimingMetric reports frameDurationCpuMs (P50…P99) and frameOverrunMs;
 * an overrun > 0 is a frame that missed its deadline — visible jank.
 *
 * Run in CI (.github/workflows/baseline-profile.yaml) or locally against a
 * device: `./gradlew :baselineprofile:connectedFdroidBenchmarkReleaseAndroidTest`.
 */
@RunWith(Parameterized::class)
class InteractionBenchmarks(
    private val compilationMode: CompilationMode,
) {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startup() =
        rule.measureRepeated(
            packageName = targetPackage(),
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = { pressHome() },
        ) { startActivityAndWait() }

    @Test
    fun keypadTyping() = measureFrames { typeOnKeypad(TYPED_AMOUNT + TYPED_AMOUNT) }

    @Test
    fun currencyPickerScroll() = measureFrames { scrollCurrencyPicker() }

    @Test
    fun screenTransitions() =
        measureFrames {
            visitFromDrawer(UiTags.DRAWER_TIMELINE)
            visitFromDrawer(UiTags.DRAWER_CART)
        }

    // The app is launched (and onboarding cleared) outside the measured
    // block, so only the interaction's frames are counted.
    private fun measureFrames(interaction: MacrobenchmarkScope.() -> Unit) =
        rule.measureRepeated(
            packageName = targetPackage(),
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = ITERATIONS,
            setupBlock = { launchToConverter() },
            measureBlock = interaction,
        )

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun compilationModes(): List<CompilationMode> =
            listOf(
                CompilationMode.None(),
                CompilationMode.Partial(BaselineProfileMode.UseIfAvailable),
            )
    }
}
