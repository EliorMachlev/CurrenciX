package com.eliormachlev.currencix.util

import android.app.Activity
import android.view.View
import android.widget.EditText
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

fun Activity.showSoftInputOn(view: View) {
    view.requestFocus()
    WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.ime())
}

fun Activity.hideSoftInputFrom(view: View) {
    WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.ime())
}

// Replace the field contents and drop the caret at the end — with a no-op
// short-circuit when the text is unchanged so re-emitted LiveData values
// don't trigger a pointless setText (which on some Android versions still
// fires TextWatchers and always kicks a measure/layout pass).
fun EditText.setTextAndCursorToEnd(text: CharSequence) {
    if (this.text?.toString() == text.toString()) return
    setText(text)
    setSelection(text.length)
}
