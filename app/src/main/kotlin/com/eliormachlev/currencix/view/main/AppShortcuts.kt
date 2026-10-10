package com.eliormachlev.currencix.view.main

import android.content.Context
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.CurrencyPair

/**
 * The launcher's long-press shortcuts: the most recent pairs (open the
 * converter on that pair) and the shopping cart. Dynamic rather than
 * declared in XML, since a static shortcut needs a fixed package name and the
 * debug build's differs. Kept in step with [com.eliormachlev.currencix.model.RecentPairs].
 */
object AppShortcuts {
    private const val MAX_PAIRS = 3
    private const val CART_ID = "cart"
    private const val PAIR_ID_PREFIX = "pair:"

    /** Replaces the shortcuts with the top [recents] (as many as the launcher shows) plus the cart. */
    fun update(
        context: Context,
        recents: List<CurrencyPair>,
    ) {
        val room = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(1)
        val pairs = recents.take(minOf(MAX_PAIRS, room - 1)).mapIndexed { rank, pair -> pairShortcut(context, pair, rank) }
        ShortcutManagerCompat.setDynamicShortcuts(context, pairs + cartShortcut(context, rank = pairs.size))
    }

    /** Tells the launcher [pair]'s shortcut was just used, so it ranks it well. */
    fun reportUsed(
        context: Context,
        pair: CurrencyPair,
    ) = ShortcutManagerCompat.reportShortcutUsed(context, pairId(pair))

    private fun pairId(pair: CurrencyPair) = "$PAIR_ID_PREFIX${pair.from.iso4217Alpha()}:${pair.to.iso4217Alpha()}"

    private fun pairShortcut(
        context: Context,
        pair: CurrencyPair,
        rank: Int,
    ): ShortcutInfoCompat {
        val from = pair.from.iso4217Alpha()
        val to = pair.to.iso4217Alpha()
        return ShortcutInfoCompat
            .Builder(context, pairId(pair))
            .setShortLabel(context.getString(R.string.shortcut_pair_short, from, to))
            .setLongLabel(context.getString(R.string.shortcut_pair_long, from, to))
            .setIcon(IconCompat.createWithResource(context, R.drawable.shortcut_pair))
            .setIntent(ConverterLaunch.convert(context, pair.from, pair.to))
            .setRank(rank)
            .build()
    }

    private fun cartShortcut(
        context: Context,
        rank: Int,
    ): ShortcutInfoCompat =
        ShortcutInfoCompat
            .Builder(context, CART_ID)
            .setShortLabel(context.getString(R.string.cart_title))
            .setIcon(IconCompat.createWithResource(context, R.drawable.shortcut_cart))
            .setIntent(ConverterLaunch.openCart(context))
            .setRank(rank)
            .build()
}
