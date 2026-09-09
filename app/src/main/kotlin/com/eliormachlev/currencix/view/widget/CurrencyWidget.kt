package com.eliormachlev.currencix.view.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.KEY_RATES_BASE
import com.eliormachlev.currencix.util.KEY_RATES_DATE
import com.eliormachlev.currencix.util.PREFS_LAST_STATE
import com.eliormachlev.currencix.util.PREFS_RATES
import com.eliormachlev.currencix.util.roundForDisplay
import com.eliormachlev.currencix.view.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext

private const val KEY_LAST_FROM = "_last_from"
private const val KEY_LAST_TO = "_last_to"
private const val DEFAULT_FROM = "USD"
private const val DEFAULT_TO = "EUR"
private const val WIDGET_DISPLAY_SCALE = 4

private val WIDGET_PADDING = 12.dp
private val RATE_FONT_SIZE = 18.sp
private val FOOTER_FONT_SIZE = 11.sp

/**
 * Home-screen widget rendering the last-used base → destination pair and the
 * cached conversion rate. Backed by the same SharedPreferences the app writes
 * to (rates + last_state), so there's no separate widget data source. Refresh
 * happens on the AppWidget update tick (see currency_widget_info.xml) and via
 * [CurrencyWidget.refreshWidgets], called after every successful rate insert.
 *
 * Migrated from RemoteViews to Glance: the receiver class name is unchanged so
 * the AndroidManifest entry still points here; the composable content lives in
 * [CurrencyGlanceWidget] and the loading placeholder in `widget_currency.xml`
 * is briefly visible on first bind before Glance takes over.
 */
class CurrencyWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CurrencyGlanceWidget

    companion object {
        // Widget refreshes are fire-and-forget from the repository — no caller
        // awaits the result — so a single supervised scope owns the coroutine
        // rather than pushing the suspend contract up into Database.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun refreshWidgets(context: Context) {
            scope.launch { CurrencyGlanceWidget.updateAll(context) }
        }
    }
}

/**
 * Glance composable body for [CurrencyWidget]. Reads the snapshot on the
 * suspend side of [provideGlance] so the composable receives plain strings and
 * doesn't touch SharedPreferences during recomposition.
 */
private object CurrencyGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val snapshot = readSnapshot(context)
        val rateLine = snapshot.rateLine(context)
        val footerLine = snapshot.footerLine(context)
        provideContent {
            WidgetBody(rateLine = rateLine, footerLine = footerLine)
        }
    }
}

@Composable
private fun WidgetBody(
    rateLine: String,
    footerLine: String,
) {
    GlanceTheme {
        Column(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(imageProvider = ImageProvider(R.drawable.widget_background))
                    .clickable(actionStartActivity(MainActivity::class.java))
                    .padding(WIDGET_PADDING),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = rateLine,
                maxLines = 1,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = RATE_FONT_SIZE,
                        fontWeight = FontWeight.Bold,
                    ),
            )
            Text(
                text = footerLine,
                maxLines = 1,
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = FOOTER_FONT_SIZE,
                    ),
            )
        }
    }
}

private fun readSnapshot(context: Context): WidgetSnapshot {
    val ratesPrefs = context.getSharedPreferences(PREFS_RATES, Context.MODE_PRIVATE)
    val lastState = context.getSharedPreferences(PREFS_LAST_STATE, Context.MODE_PRIVATE)
    val fromCode = lastState.getString(KEY_LAST_FROM, DEFAULT_FROM) ?: DEFAULT_FROM
    val toCode = lastState.getString(KEY_LAST_TO, DEFAULT_TO) ?: DEFAULT_TO
    val from = Currency.fromString(fromCode)
    val to = Currency.fromString(toCode)
    val date = ratesPrefs.getString(KEY_RATES_DATE, null)
    val baseCode = ratesPrefs.getString(KEY_RATES_BASE, null)
    val fromRate = ratesPrefs.getString(fromCode, null)?.toBigDecimalOrNull()
    val toRate = ratesPrefs.getString(toCode, null)?.toBigDecimalOrNull()
    val converted =
        if (fromRate != null && toRate != null && fromRate.signum() != 0) {
            BigDecimal.ONE
                .divide(fromRate, MathContext.DECIMAL64)
                .multiply(toRate)
                .roundForDisplay(WIDGET_DISPLAY_SCALE)
        } else {
            null
        }
    return WidgetSnapshot(
        fromCode = from?.iso4217Alpha() ?: fromCode,
        toCode = to?.iso4217Alpha() ?: toCode,
        converted = converted,
        date = date,
        hasAnyCachedRate = baseCode != null,
    )
}

private data class WidgetSnapshot(
    val fromCode: String,
    val toCode: String,
    val converted: BigDecimal?,
    val date: String?,
    val hasAnyCachedRate: Boolean,
) {
    fun rateLine(context: Context): String =
        if (converted != null) {
            context.getString(
                R.string.widget_rate_line,
                fromCode,
                converted.toPlainString(),
                toCode,
            )
        } else {
            context.getString(R.string.widget_no_rate)
        }

    fun footerLine(context: Context): String =
        when {
            date != null -> context.getString(R.string.widget_footer_date, date)
            hasAnyCachedRate -> context.getString(R.string.widget_no_rate)
            else -> context.getString(R.string.widget_footer_empty)
        }
}
