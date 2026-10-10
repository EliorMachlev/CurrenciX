package com.eliormachlev.currencix.util

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import okhttp3.Interceptor

// Debug builds: Chucker captures every call made through the shared
// OkHttpClient and shows it from its launcher activity and notification.
// Release builds have no inspector (src/release/…/HttpInspector.kt).
internal val httpInspector: ((Context) -> Interceptor)? = { context -> ChuckerInterceptor.Builder(context).build() }
