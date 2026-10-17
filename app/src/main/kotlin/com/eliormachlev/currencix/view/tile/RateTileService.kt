package com.eliormachlev.currencix.view.tile

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.convert
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.roundForDisplay
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.worker.RateRefreshScheduler
import java.math.BigDecimal

// Rates have more places than prices: four shows a move of 0.01%.
private const val TILE_RATE_DECIMALS = 4

/**
 * A Quick Settings tile with the converter's pair and its rate ("USD → ILS",
 * "3.6512"), from the rates on the phone. A tap fetches fresh rates (in the
 * background, like the scheduled refresh) and the tile redraws when they
 * arrive; a long-press opens the app, through the manifest's
 * QS_TILE_PREFERENCES filter.
 */
class RateTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        RateRefreshScheduler.refreshNow(applicationContext)
    }

    // Redraws the tile from the stored rates.
    private fun refresh() {
        val tile = qsTile ?: return
        val db = Database(applicationContext)
        val from = db.lastState.lastBaseCurrency.get()
        val to = db.lastState.lastDestinationCurrency.get()
        val label = tileLabel(from, to)
        val rate = tileRate(db.rates.getExchangeRatesBlocking(), from, to)
        tile.label = label ?: getString(R.string.tile_label)
        tile.subtitle = rate?.toHumanReadableNumber(this, decimalPlaces = TILE_RATE_DECIMALS)
        tile.contentDescription = listOfNotNull(label, tile.subtitle).joinToString(" ")
        tile.state = if (rate == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.updateTile()
    }

    companion object {
        /** Asks the system to redraw the tile (after new rates); a no-op when it isn't in Quick Settings. */
        fun requestRedraw(context: Context) = requestListeningState(context, ComponentName(context, RateTileService::class.java))
    }
}

/** "USD → ILS"; null without a pair. */
internal fun tileLabel(
    from: Currency?,
    to: Currency?,
): String? = if (from == null || to == null) null else "${from.iso4217Alpha()} → ${to.iso4217Alpha()}"

/** One [from] in [to] at [rates], rounded for the tile; null without both rates. */
internal fun tileRate(
    rates: ExchangeRates?,
    from: Currency?,
    to: Currency?,
): BigDecimal? {
    if (rates == null || from == null || to == null) return null
    return rates.convert(BigDecimal.ONE, from, to)?.roundForDisplay(TILE_RATE_DECIMALS)
}
