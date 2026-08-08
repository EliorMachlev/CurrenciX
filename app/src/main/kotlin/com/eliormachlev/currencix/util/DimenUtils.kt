package com.eliormachlev.currencix.util

import android.content.res.Resources

fun Float.dpToPx(): Float = (this * Resources.getSystem().displayMetrics.density)
