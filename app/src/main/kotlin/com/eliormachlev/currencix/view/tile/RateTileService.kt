package com.eliormachlev.currencix.view.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.convert
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.roundForDisplay
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.main.ConverterLaunch
import java.math.BigDecimal

// Rates have more places than prices: four shows a move of 0.01%.
private const val TILE_RATE_DECIMALS = 4

/**
 * A Quick Settings tile with the converter's pair and its rate ("USD → ILS",
 * "3.6512"), from the rates already on the phone. A tap opens the converter
 * on Android 14+, a long-press on every version. Nothing is fetched here: the
 * tile shows what the app last got.
 */
class RateTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    // Android 14+: opens the converter. Android 13 can only open an app from
    // a tile tap through a call that's deprecated from 14, so there a tap
    // just redraws the tile, and a long-press (on every version) opens the
    // app through the manifest's QS_TILE_PREFERENCES filter.
    override fun onClick() {
        super.onClick()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val intent = ConverterLaunch.openConverter(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            refresh()
        }
    }

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
