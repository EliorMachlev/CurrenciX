package com.eliormachlev.currencix.util

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import okhttp3.Interceptor

// Debug variant: returns the real Chucker interceptor, which captures every
// HTTP call routed through the shared OkHttpClient and exposes it via the
// Chucker launcher activity + persistent notification. The release variant
// (see src/release/…/ChuckerInterceptorProvider.kt) returns a pass-through
// no-op, and the library-no-op artifact keeps the ChuckerInterceptor class
// reference resolvable in release even if some future caller widens usage.
object ChuckerInterceptorProvider {
    fun create(context: Context): Interceptor = ChuckerInterceptor.Builder(context).build()
}
