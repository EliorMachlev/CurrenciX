package com.eliormachlev.currencix.model

/**
 * Persisted keyboard-picker preference. [prefValue] is the stable SharedPreferences
 * int (kept explicit so old installs continue to deserialize into the same
 * option — never reorder entries or reassign existing values).
 */
enum class KeyboardType(
    val prefValue: Int,
) {
    BASIC(0),
    EXPANDED(1),
    SYSTEM_NUMPAD(2),
    SYSTEM_FULL(3),
    ;

    // Either system-IME variant hosts the inline EditText that owns the
    // typed expression, so field-configuration / IME visibility / preview
    // writeback branches key off this rather than repeating a two-way `==`.
    val isSystem: Boolean get() = this == SYSTEM_NUMPAD || this == SYSTEM_FULL

    companion object {
        val DEFAULT: KeyboardType = BASIC

        fun fromPrefValue(value: Int): KeyboardType = entries.firstOrNull { it.prefValue == value } ?: DEFAULT
    }
}
