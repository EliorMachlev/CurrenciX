package com.eliormachlev.currencix.util

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Makes this mocked [Context] answer like a phone whose app language was never
 * set: its [LocaleManager] holds no app locales, so [getLocale] falls back to
 * the `locale_language` / `locale_country` resources the test stubs.
 */
internal fun Context.withNoAppLocales(): Context {
    val manager = mock(LocaleManager::class.java)
    `when`(manager.applicationLocales).thenReturn(mock(LocaleList::class.java))
    `when`(getSystemService(LocaleManager::class.java)).thenReturn(manager)
    return this
}
