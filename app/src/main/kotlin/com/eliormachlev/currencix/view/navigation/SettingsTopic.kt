package com.eliormachlev.currencix.view.navigation

/**
 * The screens under Settings, each one category of it ([Screen.SettingsPage]);
 * a topic with a [parent] opens from that topic's screen, the rest from
 * Settings itself. [id] is saved with the back stack: never rename one.
 */
enum class SettingsTopic(
    internal val id: String,
    val parent: SettingsTopic? = null,
) {
    CONVERTER("converter"),
    RATES("rates"),
    PROVIDERS("providers", parent = RATES),
    APPEARANCE("appearance"),
    CHART("chart"),
    SECURITY("security"),
    ABOUT("about"),
    CREDITS("credits", parent = ABOUT),
    ;

    companion object {
        /** The topics Settings lists, in its order. */
        val categories: List<SettingsTopic> get() = entries.filter { it.parent == null }

        internal fun byId(id: String): SettingsTopic? = entries.firstOrNull { it.id == id }
    }
}
