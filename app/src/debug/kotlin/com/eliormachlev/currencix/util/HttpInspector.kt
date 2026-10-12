package com.eliormachlev.currencix.util

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import okhttp3.Interceptor

// Headers that carry a user's API key: shown as ** in the inspector, so a
// key never sits in its captured calls or a shared screenshot of them.
private const val AUTHORIZATION = "Authorization"

// Debug builds: Chucker captures every call made through the shared
// OkHttpClient and shows it from its launcher activity and notification.
// Release builds have no inspector (src/release/…/HttpInspector.kt).
internal val httpInspector: ((Context) -> Interceptor)? = { context ->
    ChuckerInterceptor
        .Builder(context)
        .redactHeaders(AUTHORIZATION)
        .build()
}
