package com.eliormachlev.currencix.util

import android.content.Context
import okhttp3.Interceptor

// Release builds ship no HTTP inspector: Chucker's UI and storage must never
// be in them, so nothing from its package is imported here. Debug builds
// provide one (src/debug/…/HttpInspector.kt).
internal val httpInspector: ((Context) -> Interceptor)? = null
