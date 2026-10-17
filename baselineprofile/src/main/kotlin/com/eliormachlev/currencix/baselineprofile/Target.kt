package com.eliormachlev.currencix.baselineprofile

import androidx.test.platform.app.InstrumentationRegistry

// Fallback when the Gradle wiring (androidComponents in build.gradle.kts)
// didn't pass the tested APK's application id.
private const val DEFAULT_TARGET_PACKAGE = "com.eliormachlev.currencix"

/** A realistic amount to type — digits only, so it's locale-independent. */
internal const val TYPED_AMOUNT = "1250"

/** Application id of the APK under test, as passed by the Gradle build. */
internal fun targetPackage(): String = InstrumentationRegistry.getArguments().getString("targetAppId") ?: DEFAULT_TARGET_PACKAGE
