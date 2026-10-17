package com.eliormachlev.currencix.view.preference.compose

/** What a single-choice picker offers: the [options], the one [selected] now, and how each reads. */
class Choices<T>(
    val options: List<T>,
    val selected: T?,
    val label: (T) -> String,
)
