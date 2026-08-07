package de.salomax.currencies.view.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import de.salomax.currencies.R
import de.salomax.currencies.model.Currency
import de.salomax.currencies.util.KEY_RATES_BASE
import de.salomax.currencies.util.KEY_RATES_DATE
import de.salomax.currencies.util.PREFS_LAST_STATE
import de.salomax.currencies.util.PREFS_RATES
import de.salomax.currencies.util.roundForDisplay
import de.salomax.currencies.view.main.MainActivity
import java.math.BigDecimal
import java.math.MathContext

private const val KEY_LAST_FROM = "_last_from"
private const val KEY_LAST_TO = "_last_to"
private const val DEFAULT_FROM = "USD"
private const val DEFAULT_TO = "EUR"
private const val WIDGET_DISPLAY_SCALE = 4

// Home-screen widget rendering the last-used base → destination pair and the
// cached conversion rate. Backed by the same SharedPreferences the app writes
// to (rates + last_state), so there's no separate widget data source. Refresh
// happens on the AppWidget update tick (see currency_widget_info.xml) and via
// [refreshWidgets], called after every successful rate insert.
class CurrencyWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildRemoteViews(context))
        }
    }

    companion object {
        // Called from the repository after new rates land, so an open widget
        // reflects the fresh values without waiting for the next system tick.
        fun refreshWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, CurrencyWidget::class.java))
            if (ids.isEmpty()) return
            val views = buildRemoteViews(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_currency)
            val snapshot = readSnapshot(context)
            views.setTextViewText(R.id.widget_rate_line, snapshot.rateLine(context))
            views.setTextViewText(R.id.widget_footer, snapshot.footerLine(context))
            views.setOnClickPendingIntent(R.id.widget_root, launchAppIntent(context))
            return views
        }

        private fun launchAppIntent(context: Context): PendingIntent {
            val intent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
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
            val fromRate = fromCode.let { ratesPrefs.getString(it, null)?.toBigDecimalOrNull() }
            val toRate = toCode.let { ratesPrefs.getString(it, null)?.toBigDecimalOrNull() }
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
    }
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
