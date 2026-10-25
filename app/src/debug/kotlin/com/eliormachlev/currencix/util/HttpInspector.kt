package com.eliormachlev.currencix.util

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.eliormachlev.currencix.model.provider.api.AUTHORIZATION
import okhttp3.Interceptor

// Debug builds: Chucker captures every call made through the shared
// OkHttpClient and shows it from its launcher activity and notification.
// Release builds have no inspector (src/release/…/HttpInspector.kt).
internal val httpInspector: ((Context) -> Interceptor)? = { context ->
    ChuckerInterceptor
        .Builder(context)
        // The header that carries a user's API key: shown as ** in the
        // inspector, so a key never sits in its captured calls or a shared
        // screenshot of them.
        .redactHeaders(AUTHORIZATION)
        .build()
}
