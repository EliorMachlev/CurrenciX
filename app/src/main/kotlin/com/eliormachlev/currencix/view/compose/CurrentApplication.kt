package com.eliormachlev.currencix.view.compose

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** The application, which the state holders here are built from (never the Activity, which `retain` must not hold). */
@Composable
internal fun currentApplication(): Application = LocalContext.current.applicationContext as Application
