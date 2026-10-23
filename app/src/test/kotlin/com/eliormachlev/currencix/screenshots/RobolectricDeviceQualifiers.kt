package com.eliormachlev.currencix.screenshots

// Robolectric `@Config(qualifiers = …)` strings for the devices we render
// against. Consolidated here so every screenshot test picks the same
// canvas — a Pixel 5 (411 × 891 dp, xhdpi) matches the design ceiling
// this app was tuned for.
internal object RobolectricDeviceQualifiers {
    const val PIXEL_5 = "w411dp-h891dp-xhdpi"

    // An unfolded foldable, wide enough for the one-handed keypad.
    const val UNFOLDED = "w673dp-h841dp-xhdpi"
}
