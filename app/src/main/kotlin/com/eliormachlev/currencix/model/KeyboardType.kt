package com.eliormachlev.currencix.model

/**
 * Persisted keyboard-picker preference. Serialized to DataStore by
 * [ordinal], so entries here must not be reordered — append new ones at the
 * end.
 */
enum class KeyboardType {
    BASIC,
    EXPANDED,
    ;

    companion object {
        val DEFAULT: KeyboardType = BASIC

        fun fromOrdinal(value: Int): KeyboardType = entries.getOrNull(value) ?: DEFAULT
    }
}
