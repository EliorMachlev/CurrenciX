package com.eliormachlev.currencix.util

// Preference keys inside the `rates` namespace (see PersistenceKey.RATES).
// Shared with Database (writer), CurrencyWidget (reader), and any future
// namespace consumer; the leading underscore separates metadata keys from
// currency-code entries (e.g. "USD", "EUR").
internal const val KEY_RATES_BASE = "_base"
internal const val KEY_RATES_DATE = "_date"
internal const val KEY_RATES_TIME = "_time"
internal const val KEY_RATES_PROVIDER = "_provider"

// Sentinel for "no API provider stored yet"; ApiProvider.fromId maps it to the
// default provider. Kept as -1 to match previously persisted values.
internal const val NO_PROVIDER_ID = -1
