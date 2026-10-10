package com.eliormachlev.currencix.view.main

import com.eliormachlev.currencix.viewmodel.main.MainViewModel
import com.eliormachlev.currencix.viewmodel.preference.PreferenceViewModel

/**
 * What the converter needs from its Activity: the ViewModels (Activity
 * scoped — hardware-keyboard input feeds the same converter), the status
 * tracker, the share action (which owns the hero-card capture), and the
 * one-shot wordmark reveal.
 */
internal class ConverterHost(
    val viewModel: MainViewModel,
    val preferenceModel: PreferenceViewModel,
    val status: ConverterStatus,
    val share: ConversionShare,
    private var revealPending: Boolean,
    val onWordmarkFirstFrame: () -> Unit,
) {
    /**
     * True exactly once per cold start: the wordmark reveal plays the first
     * time the converter shows, not again when the user comes back to it
     * from another screen or rotates.
     */
    fun takeWordmarkReveal(): Boolean = revealPending.also { revealPending = false }
}
