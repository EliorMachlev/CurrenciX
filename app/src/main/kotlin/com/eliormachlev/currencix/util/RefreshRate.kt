package com.eliormachlev.currencix.util

import android.view.Display
import android.view.Window

// Above this a screen counts as high refresh: 60 Hz panels report 59.94–60.
private const val STANDARD_REFRESH_HZ = 61f

/** A display mode: its id, resolution, and refresh rate in Hz. */
internal data class ScreenMode(
    val id: Int,
    val width: Int,
    val height: Int,
    val hz: Float,
)

/**
 * The fastest of [modes] at [current]'s resolution (a rate change shouldn't
 * also change the resolution), or null when none is faster than 60 Hz.
 */
internal fun fastestOf(
    current: ScreenMode,
    modes: List<ScreenMode>,
): ScreenMode? =
    modes
        .filter { it.width == current.width && it.height == current.height }
        .maxByOrNull { it.hz }
        ?.takeIf { it.hz > STANDARD_REFRESH_HZ }

private fun Display.Mode.toScreenMode() = ScreenMode(modeId, physicalWidth, physicalHeight, refreshRate)

/** [this] display's fastest mode at its current resolution ([fastestOf]). */
internal fun Display.fastestMode(): ScreenMode? = fastestOf(mode.toScreenMode(), supportedModes.map { it.toScreenMode() })

/**
 * While this window shows, asks for [display]'s fastest rate ([enabled]), or
 * leaves the rate to the system. Some phones' automatic modes (Xiaomi's among
 * them) keep an app at 60 Hz unless it asks; battery saver and heat limits
 * still cap what it gets.
 */
internal fun Window.preferFastestRefreshRate(
    display: Display,
    enabled: Boolean,
) {
    val modeId = if (enabled) display.fastestMode()?.id ?: 0 else 0
    if (attributes.preferredDisplayModeId == modeId) return
    attributes = attributes.apply { preferredDisplayModeId = modeId }
}
