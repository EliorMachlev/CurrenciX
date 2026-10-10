package com.eliormachlev.currencix.view.navigation

import com.eliormachlev.currencix.model.Currency

/**
 * Every destination in the app. The app is a single Activity; each of these
 * is one entry on the Compose back stack that [AppNavigator] owns and
 * NavDisplay renders (see AppNavHost).
 *
 * Arguments live on the key itself, so a screen can't be opened without
 * them and they survive process death along with the back stack
 * ([encode] / [decodeScreen]).
 */
sealed interface Screen {
    /** The converter: keypad, hero card, drawer. Always the bottom of the stack. */
    data object Converter : Screen

    /** Rate history for one currency pair. */
    data class Timeline(
        val from: Currency,
        val to: Currency,
    ) : Screen

    /**
     * The shopping cart. [mainBase] / [mainDest] are the converter's pair at
     * the moment the cart was opened: they seed a fresh cart and re-seed it
     * on Clear, so the cart mirrors what the user just saw.
     */
    data class Cart(
        val mainBase: Currency?,
        val mainDest: Currency?,
    ) : Screen

    data object Settings : Screen

    data object Fees : Screen

    data object Backup : Screen
}

// Stable on-disk names for the saved back stack. Never rename one: a stack
// saved by the previous app version is restored with these after an update.
private enum class Route(
    val id: String,
) {
    CONVERTER("converter"),
    TIMELINE("timeline"),
    CART("cart"),
    SETTINGS("settings"),
    FEES("fees"),
    BACKUP("backup"),
    ;

    companion object {
        fun byId(id: String): Route? = entries.firstOrNull { it.id == id }
    }
}

private const val SEPARATOR = ':'

// Stands in for a null currency argument (the cart's optional seed pair).
private const val NO_CURRENCY = "-"

private fun Currency?.encodeArg(): String = this?.iso4217Alpha() ?: NO_CURRENCY

private fun String.decodeArg(): Currency? = if (this == NO_CURRENCY) null else Currency.fromString(this)

/** One line per screen, e.g. `timeline:EUR:USD` — small enough for the saved-state Bundle. */
internal fun Screen.encode(): String =
    when (this) {
        Screen.Converter -> Route.CONVERTER.id
        is Screen.Timeline -> listOf(Route.TIMELINE.id, from.encodeArg(), to.encodeArg()).joinToString(SEPARATOR.toString())
        is Screen.Cart -> listOf(Route.CART.id, mainBase.encodeArg(), mainDest.encodeArg()).joinToString(SEPARATOR.toString())
        Screen.Settings -> Route.SETTINGS.id
        Screen.Fees -> Route.FEES.id
        Screen.Backup -> Route.BACKUP.id
    }

/**
 * Inverse of [encode]. Returns null for anything it can't read back (an
 * unknown route, a currency the app no longer ships) so a restore drops that
 * one screen instead of crashing.
 */
internal fun decodeScreen(encoded: String): Screen? {
    val parts = encoded.split(SEPARATOR)
    val args = parts.drop(1)
    return when (Route.byId(parts.first())) {
        Route.CONVERTER -> {
            Screen.Converter
        }

        Route.TIMELINE -> {
            val from = args.getOrNull(0)?.decodeArg()
            val to = args.getOrNull(1)?.decodeArg()
            if (from != null && to != null) Screen.Timeline(from, to) else null
        }

        Route.CART -> {
            Screen.Cart(args.getOrNull(0)?.decodeArg(), args.getOrNull(1)?.decodeArg())
        }

        Route.SETTINGS -> {
            Screen.Settings
        }

        Route.FEES -> {
            Screen.Fees
        }

        Route.BACKUP -> {
            Screen.Backup
        }

        null -> {
            null
        }
    }
}
