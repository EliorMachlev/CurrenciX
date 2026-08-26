package com.eliormachlev.currencix.model

/**
 * Persisted keyboard-picker preference. Serialized to SharedPreferences by
 * [ordinal], so entries here must not be reordered — append new ones at the
 * end.
 */
enum class KeyboardType {
    BASIC,
    EXPANDED,
    SYSTEM_NUMPAD,
    SYSTEM_FULL,
    ;

    // Either system-IME variant hosts the inline EditText that owns the
    // typed expression, so field-configuration / IME visibility / preview
    // writeback branches key off this rather than repeating a two-way `==`.
    val isSystem: Boolean get() = this == SYSTEM_NUMPAD || this == SYSTEM_FULL

    companion object {
        val DEFAULT: KeyboardType = BASIC

        fun fromOrdinal(value: Int): KeyboardType = entries.getOrNull(value) ?: DEFAULT
    }
}
