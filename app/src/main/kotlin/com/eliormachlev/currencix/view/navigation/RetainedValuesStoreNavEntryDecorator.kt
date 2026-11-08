package com.eliormachlev.currencix.view.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.RetainedValuesStoreRegistry
import androidx.compose.runtime.retain.retainRetainedValuesStoreRegistry
import androidx.navigation3.runtime.NavEntryDecorator

/**
 * Gives each back-stack entry its own store for `retain`, so what a screen
 * retains lives while its entry is on the back stack (also while another
 * screen covers it) and goes when the entry is popped. It does for `retain`
 * what the ViewModel decorator did for a ViewModel.
 */
internal class RetainedValuesStoreNavEntryDecorator<T : Any>(
    registry: RetainedValuesStoreRegistry,
) : NavEntryDecorator<T>(
        onPop = { key -> registry.clearChild(key) },
        decorate = { entry -> registry.LocalRetainedValuesStoreProvider(entry.contentKey) { entry.Content() } },
    )

/** A [RetainedValuesStoreNavEntryDecorator] over [registry], which is itself retained across rotation. */
@Composable
internal fun <T : Any> rememberRetainedValuesStoreNavEntryDecorator(
    registry: RetainedValuesStoreRegistry = retainRetainedValuesStoreRegistry(),
): RetainedValuesStoreNavEntryDecorator<T> = remember(registry) { RetainedValuesStoreNavEntryDecorator(registry) }
