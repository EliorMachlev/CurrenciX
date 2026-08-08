package com.eliormachlev.currencix.util

import android.view.View
import android.widget.AdapterView
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate

/**
 * OnItemSelectedListener that forwards the picked [Rate]'s currency to
 * [onCurrencySelected]. Guards against the spurious "no selection" callbacks
 * that fire while a searchable spinner is rebuilding its adapter — those
 * arrive with `position == -1` or against an empty adapter and would
 * otherwise clobber the user's real selection with a null.
 *
 * Every currency-picker spinner in the app shares this exact shape (main
 * screen from/to, cart from/to), so the listener is factored here instead of
 * being re-declared per activity.
 */
fun rateSpinnerListener(onCurrencySelected: (Currency) -> Unit): AdapterView.OnItemSelectedListener =
    object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit

        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long,
        ) {
            if (position == -1 || parent?.adapter?.isEmpty == true) return
            (parent?.adapter?.getItem(position) as Rate?)?.let { onCurrencySelected(it.currency) }
        }
    }
