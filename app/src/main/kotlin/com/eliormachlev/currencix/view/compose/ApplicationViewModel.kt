package com.eliormachlev.currencix.view.compose

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

/** The application, which every view model here is built from. */
@Composable
internal fun currentApplication(): Application = LocalContext.current.applicationContext as Application

/**
 * The view model [create] builds from the application, kept by the nearest
 * ViewModelStoreOwner as [viewModel] keeps it. Built by [create] rather than by
 * the default factory, which finds the class's constructor by reflection.
 */
@Composable
internal inline fun <reified VM : ViewModel> applicationViewModel(
    key: String? = null,
    crossinline create: (Application) -> VM,
): VM {
    val application = currentApplication()
    return viewModel(key = key) { create(application) }
}
