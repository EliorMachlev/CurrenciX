package com.eliormachlev.currencix.util

import android.content.Context
import okhttp3.Interceptor

// Release variant: pass-through interceptor. Chucker's UI + storage code
// must never ship in release, so this variant does not import anything
// from the Chucker package — the build type picks this file over the
// src/debug counterpart for release variants.
object ChuckerInterceptorProvider {
    fun create(
        @Suppress("UNUSED_PARAMETER") context: Context,
    ): Interceptor = Interceptor { chain -> chain.proceed(chain.request()) }
}
