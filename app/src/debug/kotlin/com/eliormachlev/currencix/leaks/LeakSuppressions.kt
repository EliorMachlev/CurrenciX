package com.eliormachlev.currencix.leaks

import android.app.Application
import leakcanary.LeakCanary
import shark.IgnoredReferenceMatcher
import shark.ReferencePattern

// A fork of Android used by several OEMs added a process-wide static
// field `ResourcesImpl.mAppContext` that ends up pinning whichever
// ContextImpl the platform last handed out — in our case, the
// ContextImpl backing WorkManager's SystemJobService. Every scheduled
// rate-refresh cycle leaves another JobService instance retained,
// producing dozens of Library-Leak notifications that crowd out real
// app-side leaks on affected devices. The reference is upstream of any
// code we own, so mark it ignored to short-circuit the traversal before
// it reaches our WorkManager service.
fun Application.suppressKnownPlatformLeaks() {
    LeakCanary.config =
        LeakCanary.config.copy(
            referenceMatchers =
                LeakCanary.config.referenceMatchers +
                    IgnoredReferenceMatcher(
                        pattern =
                            ReferencePattern.StaticFieldPattern(
                                className = "android.content.res.ResourcesImpl",
                                fieldName = "mAppContext",
                            ),
                    ),
        )
}
