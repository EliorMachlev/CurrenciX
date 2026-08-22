package com.eliormachlev.currencix.util

import android.app.Activity
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

fun Activity.showSoftInputOn(view: View) {
    view.requestFocus()
    WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.ime())
}

fun Activity.hideSoftInputFrom(view: View) {
    WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.ime())
}
