package com.eliormachlev.currencix.alert

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.AlertDirection
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.RateAlert
import com.eliormachlev.currencix.model.convert
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.roundForDisplay
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.main.ConverterLaunch
import java.math.BigDecimal

private const val CHANNEL_ID = "rate_alerts"

// Rates have more places than prices: four shows a move of 0.01%.
internal const val ALERT_RATE_DECIMALS = 4

/** Whether the app may post notifications (Android 13+ asks the user). */
fun canNotify(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()

/**
 * After new rates are stored: every alert they reach is taken out of the
 * store and shown as a notification. Nothing happens for a pinned past date
 * (those rates aren't today's) or while the app can't post notifications
 * (the alerts wait for the next check).
 */
internal fun checkRateAlerts(
    context: Context,
    db: Database,
    rates: ExchangeRates,
) {
    if (db.alerts.alerts
            .get()
            .isEmpty() || db.lastState.historicalDate.get() != null
    ) {
        return
    }
    if (!canNotify(context)) return
    val rateOf = { pair: CurrencyPair -> rates.convert(BigDecimal.ONE, pair.from, pair.to) }
    db.alerts.takeReached(rateOf).forEach { alert ->
        rateOf(alert.pair)?.let { notifyReached(context, alert, it) }
    }
}

// "USD → ILS is at 3.4987", "You asked to hear when 1 USD is 3.5000 ILS or less."
private fun notifyReached(
    context: Context,
    alert: RateAlert,
    rate: BigDecimal,
) {
    // Checked here, where it's posted: the user can take the permission back at any time.
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    ensureChannel(context)
    val (from, to) = alert.pair
    val number = { value: BigDecimal ->
        value.roundForDisplay(ALERT_RATE_DECIMALS).toHumanReadableNumber(context, decimalPlaces = ALERT_RATE_DECIMALS)
    }
    val rateText = number(rate)
    val title = context.getString(R.string.alert_notification_title, from.iso4217Alpha(), to.iso4217Alpha(), rateText)
    val textRes = if (alert.direction == AlertDirection.ABOVE) R.string.alert_notification_above else R.string.alert_notification_below
    val text = context.getString(textRes, from.iso4217Alpha(), number(alert.threshold), to.iso4217Alpha())
    val open =
        PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            ConverterLaunch.convert(context, from, to),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    val notification =
        NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert_active)
            .setContentTitle(withRateColor(title, rateText, alert.direction))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
    NotificationManagerCompat.from(context).notify(alert.id.hashCode(), notification)
}

// Android 17's notification colors: the rate in the title green when it has
// risen to the alert, red when it has fallen to it, the way a ticker shows a
// move. Earlier versions show it plain.
private fun withRateColor(
    title: String,
    rateText: String,
    direction: AlertDirection,
): CharSequence =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
        title.withSpanOnLast(rateText, Notification.createSemanticStyleAnnotation(direction.semanticStyle))
    } else {
        title
    }

/** The notification color for an alert in this direction: up is green ("safe"), down is red ("danger"). */
@get:RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
internal val AlertDirection.semanticStyle: Int
    get() =
        when (this) {
            AlertDirection.ABOVE -> Notification.SEMANTIC_STYLE_SAFE
            AlertDirection.BELOW -> Notification.SEMANTIC_STYLE_DANGER
        }

/** This text with [span] over the last occurrence of [part]; unchanged when [part] isn't in it. */
internal fun CharSequence.withSpanOnLast(
    part: String,
    span: Any,
): CharSequence {
    val start = toString().lastIndexOf(part)
    if (part.isEmpty() || start < 0) return this
    return SpannableString(this).apply { setSpan(span, start, start + part.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
}

// One channel, "Rate alerts", so the user can tune them in the system settings.
private fun ensureChannel(context: Context) {
    val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.alert_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}
