package com.eliormachlev.currencix.util

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

fun Activity.showSoftInputOn(view: View) {
    view.requestFocus()
    imm()?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

fun Activity.hideSoftInputFrom(view: View) {
    imm()?.hideSoftInputFromWindow(view.windowToken, 0)
}

private fun Context.imm(): InputMethodManager? = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
