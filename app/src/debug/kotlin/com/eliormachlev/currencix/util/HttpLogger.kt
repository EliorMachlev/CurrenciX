package com.eliormachlev.currencix.util

import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber

// Debug builds log each call (method, URL, status, time) under the HTTP tag.
// Release builds have no logger (src/release/…/HttpLogger.kt), so neither
// the interceptor nor its library is in them.
internal val httpLogger: Interceptor? =
    HttpLoggingInterceptor { line -> Timber.tag("HTTP").d(line) }.apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }
