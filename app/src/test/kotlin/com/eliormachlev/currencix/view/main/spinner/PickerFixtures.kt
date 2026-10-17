package com.eliormachlev.currencix.view.main.spinner

import com.eliormachlev.currencix.model.Currency

/** Picker actions that do nothing, but for [onRemoveRecent]. */
internal fun pickerActions(onRemoveRecent: (Currency) -> Unit = {}) =
    CurrencyPickerActions(
        onRateClicked = {},
        onStarClicked = {},
        onToggleStarredFilter = {},
        onStarredOrderChanged = {},
        onRemoveRecent = onRemoveRecent,
    )
