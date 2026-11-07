package com.eliormachlev.currencix.model

import android.app.UiModeManager
import android.content.res.Configuration

/**
 * The five theme choices exposed to the user, unified into a single enum so the
 * (id, night mode, pure-black) triple stays in one place instead of being
 * mirrored across the DB, application, view-model and view layers.
 *
 * [nightMode] is a [UiModeManager] mode; [UiModeManager.MODE_NIGHT_AUTO] makes the
 * app follow the phone's own dark theme.
 *
 * `id` is the value persisted in DataStore and referenced from
 * `arrays_preference.xml`, so **it must not change once shipped**.
 */
enum class AppTheme(
    val id: Int,
    val nightMode: Int,
    val isPureBlack: Boolean,
) {
    LIGHT(0, UiModeManager.MODE_NIGHT_NO, false),
    DARK(1, UiModeManager.MODE_NIGHT_YES, false),
    OLED(2, UiModeManager.MODE_NIGHT_YES, true),
    SYSTEM(3, UiModeManager.MODE_NIGHT_AUTO, false),
    SYSTEM_OLED(4, UiModeManager.MODE_NIGHT_AUTO, true),
    ;

    /** True when this theme currently renders dark under [config]. */
    fun isDarkActive(config: Configuration): Boolean =
        when (nightMode) {
            UiModeManager.MODE_NIGHT_YES -> {
                true
            }

            UiModeManager.MODE_NIGHT_AUTO -> {
                (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }

            else -> {
                false
            }
        }

    companion object {
        val DEFAULT = SYSTEM

        fun fromId(id: Int): AppTheme = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
