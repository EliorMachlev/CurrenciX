package com.eliormachlev.currencix.util

import okhttp3.Interceptor

// Release builds log no calls: the library that does it isn't in them, so
// nothing from its package is imported here. Debug builds provide one
// (src/debug/…/HttpLogger.kt).
internal val httpLogger: Interceptor? = null
