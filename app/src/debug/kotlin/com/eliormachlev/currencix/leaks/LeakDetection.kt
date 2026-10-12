package com.eliormachlev.currencix.leaks

import leakcanary.LeakCanary
import shark.AndroidReferenceMatchers
import shark.IgnoredReferenceMatcher
import shark.ReferencePattern.StaticFieldPattern

// Some vendors' Android (Xiaomi's among them) adds a static
// ResourcesImpl.mAppContext that keeps the last service's context, here a
// finished WorkManager service. It's in the framework, held one at a time
// and replaced by the next; the app has no reference it could let go of.
// LeakCanary reports it on every such phone, so it's ignored: exception 3 in
// docs/markDown/exceptions.md, the maintainer's decision.
private val vendorAppContext = StaticFieldPattern("android.content.res.ResourcesImpl", "mAppContext")

/** LeakCanary's usual known-leak list, the vendor field above ignored rather than reported. */
fun configureLeakDetection() {
    LeakCanary.config =
        LeakCanary.config.copy(
            referenceMatchers =
                AndroidReferenceMatchers.appDefaults.filterNot { it.pattern == vendorAppContext } +
                    IgnoredReferenceMatcher(vendorAppContext),
        )
}
