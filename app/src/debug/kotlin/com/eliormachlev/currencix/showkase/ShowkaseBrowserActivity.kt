package com.eliormachlev.currencix.showkase

import android.app.Activity
import android.os.Bundle
import com.airbnb.android.showkase.models.Showkase

// Thin launcher: forwards straight to the generated Showkase browser
// intent and finishes so the browser Activity itself becomes the top
// of the task. Registered as a debug-only LAUNCHER in
// src/debug/AndroidManifest.xml — appears as a second app icon
// alongside the main app in debug builds.
class ShowkaseBrowserActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Showkase.getBrowserIntent(this))
        finish()
    }
}
