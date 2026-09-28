package com.eliormachlev.currencix.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/*
 * The user journeys shared by the baseline-profile generator and the
 * frame-timing benchmarks, so both exercise exactly the same hot paths:
 * typing on the keypad, scrolling the currency picker, and moving between
 * screens. Elements are found by the app's UiTestTags (see UiTags), never by
 * visible text, so the journeys don't depend on locale or copy.
 */

private const val UI_TIMEOUT_MS = 5_000L

// First-run onboarding appears shortly after the first frame; on later runs
// it never does, so don't wait long for it.
private const val ONBOARDING_TIMEOUT_MS = 2_000L

// Material 3's modal drawer opens from a horizontal drag anywhere on its
// content. Drag across the keypad (no horizontal scrollers there), starting
// clear of the left edge — an edge swipe is the system back gesture under
// gesture navigation and would leave the app.
private const val DRAWER_DRAG_Y = 0.8f
private const val DRAWER_DRAG_FROM_X = 0.2f
private const val DRAWER_DRAG_TO_X = 0.85f

// A horizontal drag across the timeline chart scrubs its marker.
private const val CHART_SCRUB_Y = 0.4f
private const val CHART_SCRUB_FROM_X = 0.2f
private const val CHART_SCRUB_TO_X = 0.8f

private const val GESTURE_STEPS = 20
private const val FLING_MARGIN_DIVISOR = 10
private const val MAX_BACK_PRESSES = 3

/** Cold path to the converter: launch, wait for the keypad, clear onboarding if shown. */
internal fun MacrobenchmarkScope.launchToConverter() {
    pressHome()
    startActivityAndWait()
    awaitConverter()
    device.wait(Until.findObject(By.res(UiTags.ONBOARDING_SKIP)), ONBOARDING_TIMEOUT_MS)?.click()
    device.waitForIdle()
}

/** Types [keys] (digits) on the converter keypad, then deletes them again. */
internal fun MacrobenchmarkScope.typeOnKeypad(keys: String) {
    keys.forEach { device.findObject(By.res(UiTags.key(it))).click() }
    repeat(keys.length) { device.findObject(By.res(UiTags.KEY_DELETE)).click() }
    device.waitForIdle()
}

/** Opens the "to" currency picker, flings through the list, and closes it. */
internal fun MacrobenchmarkScope.scrollCurrencyPicker() {
    device.findObject(By.res(UiTags.PILL_TO)).click()
    val list =
        checkNotNull(device.wait(Until.findObject(By.res(UiTags.CURRENCY_LIST)), UI_TIMEOUT_MS)) {
            "currency picker never opened"
        }
    // Keep flings off the sheet's edges (drag handle, system gesture areas).
    list.setGestureMargin(device.displayWidth / FLING_MARGIN_DIVISOR)
    repeat(2) { list.fling(Direction.DOWN) }
    list.fling(Direction.UP)
    // The picker may have raised the keyboard for its search field: back
    // until the sheet is gone rather than assuming one press closes it.
    backUntilGone(UiTags.CURRENCY_LIST)
}

/** Opens a screen from the drawer, lets it settle, and navigates back. */
internal fun MacrobenchmarkScope.visitFromDrawer(entryTag: String) {
    openDrawer()
    checkNotNull(device.wait(Until.findObject(By.res(entryTag)), UI_TIMEOUT_MS)) {
        "drawer entry $entryTag never appeared"
    }.click()
    // The converter's keypad disappears once the new screen covers it.
    device.wait(Until.gone(By.res(UiTags.KEY_DELETE)), UI_TIMEOUT_MS)
    device.waitForIdle()
    if (entryTag == UiTags.DRAWER_TIMELINE) scrubChart()
    device.pressBack()
    awaitConverter()
}

private fun MacrobenchmarkScope.awaitConverter() {
    check(device.wait(Until.hasObject(By.res(UiTags.KEY_DELETE)), UI_TIMEOUT_MS)) {
        "converter keypad never appeared"
    }
}

private fun MacrobenchmarkScope.openDrawer() {
    val y = (device.displayHeight * DRAWER_DRAG_Y).toInt()
    device.swipe(
        (device.displayWidth * DRAWER_DRAG_FROM_X).toInt(),
        y,
        (device.displayWidth * DRAWER_DRAG_TO_X).toInt(),
        y,
        GESTURE_STEPS,
    )
}

private fun MacrobenchmarkScope.scrubChart() {
    val y = (device.displayHeight * CHART_SCRUB_Y).toInt()
    device.swipe(
        (device.displayWidth * CHART_SCRUB_FROM_X).toInt(),
        y,
        (device.displayWidth * CHART_SCRUB_TO_X).toInt(),
        y,
        GESTURE_STEPS,
    )
    device.waitForIdle()
}

private fun MacrobenchmarkScope.backUntilGone(tag: String) {
    repeat(MAX_BACK_PRESSES) {
        if (!device.hasObject(By.res(tag))) return
        device.pressBack()
        device.wait(Until.gone(By.res(tag)), UI_TIMEOUT_MS)
    }
    check(!device.hasObject(By.res(tag))) { "$tag still showing after $MAX_BACK_PRESSES back presses" }
}
