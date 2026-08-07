package de.salomax.currencies.util

import android.content.res.Resources

fun Float.dpToPx(): Float = (this * Resources.getSystem().displayMetrics.density)
