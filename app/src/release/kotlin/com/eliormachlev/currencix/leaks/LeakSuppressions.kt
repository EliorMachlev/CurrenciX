package com.eliormachlev.currencix.leaks

import android.app.Application

// Release variant: no-op. LeakCanary is a debugImplementation dependency
// and isn't present in release builds, so there are no matchers to tune —
// the identical entry point keeps Application.onCreate variant-agnostic.
fun Application.suppressKnownPlatformLeaks() = Unit
