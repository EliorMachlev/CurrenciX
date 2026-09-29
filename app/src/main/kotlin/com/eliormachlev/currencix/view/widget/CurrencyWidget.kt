package com.eliormachlev.currencix.view.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.CurrencyPair
import com.eliormachlev.currencix.model.convert
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.repository.persistence.WidgetRefreshBus
import com.eliormachlev.currencix.util.roundForDisplay
import com.eliormachlev.currencix.view.main.ConverterLaunch
import com.eliormachlev.currencix.view.main.formatRatesTimestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

private const val WIDGET_DISPLAY_SCALE = 4

private val WIDGET_PADDING = 12.dp
private val RATE_FONT_SIZE = 18.sp
private val FOOTER_FONT_SIZE = 11.sp

/**
 * Home-screen widget rendering a pair — the converter's last-used one, or its
 * own ([WidgetPairState], set in [WidgetConfigureActivity]) — its cached
 * rate, and on wider widgets a 30-day trend line. Tapping opens the
 * converter on that pair. Backed by the same DataStore namespaces the app
 * writes to (rates + last_state) via PersistenceKey, so there's no separate
 * widget data source. Refresh happens on the AppWidget update tick (see
 * currency_widget_info.xml) and via [WidgetRefreshBus], which the repository
 * layer signals after every successful rate insert without importing this
 * class (keeps the Konsist layer boundary intact).
 *
 * Migrated from RemoteViews to Glance: the receiver class name is unchanged so
 * the AndroidManifest entry still points here; the composable content lives in
 * [CurrencyGlanceWidget] and the loading placeholder in `widget_currency.xml`
 * is briefly visible on first bind before Glance takes over.
 */
class CurrencyWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CurrencyGlanceWidget

    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
        context?.let(::ensureRefreshBusBound)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        ensureRefreshBusBound(context)
    }

    companion object {
        // Widget refreshes are fire-and-forget — no caller awaits the result —
        // so a single supervised scope owns the coroutine rather than pushing
        // a suspend contract into the repository layer. WidgetRefreshBus lets
        // the repository signal "data changed" without importing this class,
        // preserving the Konsist layer boundary (see WidgetRefreshBus).
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Volatile
        private var busBound: Boolean = false

        fun ensureRefreshBusBound(context: Context) {
            if (busBound) return
            synchronized(this) {
                if (busBound) return
                busBound = true
                val appContext = context.applicationContext
                scope.launch {
                    WidgetRefreshBus.events.collectLatest {
                        CurrencyGlanceWidget.updateAll(appContext)
                    }
                }
            }
        }
    }
}

// Narrow (the 2×1 default) shows the rate; from WIDE_SIZE on there's room
// for the 30-day trend line beside it.
private val NARROW_SIZE = DpSize(180.dp, 60.dp)
private val WIDE_SIZE = DpSize(260.dp, 60.dp)
private val SPARKLINE_WIDTH = 88.dp
private val SPARKLINE_HEIGHT = 32.dp
private val SPARKLINE_STROKE = 2.dp
private val SPARKLINE_GAP = 12.dp

/**
 * One widget's pair, in its own Glance state: by default it follows the
 * converter's last-used pair; [WidgetConfigureActivity] can pin it to a pair
 * of its own.
 */
internal object WidgetPairState {
    val FOLLOW_CONVERTER = booleanPreferencesKey("follow_converter")
    val FROM = stringPreferencesKey("from")
    val TO = stringPreferencesKey("to")

    /** The pair a widget with [state] shows; null before any pair exists. */
    fun resolve(
        state: Preferences,
        db: Database,
    ): CurrencyPair? {
        val own =
            if (state[FOLLOW_CONVERTER] == false) {
                val from = state[FROM]?.let(Currency::fromString)
                val to = state[TO]?.let(Currency::fromString)
                if (from != null && to != null && from != to) CurrencyPair(from, to) else null
            } else {
                null
            }
        return own ?: converterPair(db)
    }

    private fun converterPair(db: Database): CurrencyPair? {
        val from = db.getLastBaseCurrencyBlocking() ?: return null
        val to = db.getLastDestinationCurrencyBlocking() ?: return null
        return CurrencyPair(from, to)
    }
}

/**
 * Glance body for [CurrencyWidget]. Everything is read in composition from
 * DataStore's in-memory snapshots, so a rates refresh ([WidgetRefreshBus])
 * or a new pair from the configure screen recomposes with fresh values.
 */
internal object CurrencyGlanceWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(setOf(NARROW_SIZE, WIDE_SIZE))

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val db = Database(context)
        provideContent {
            val pair = WidgetPairState.resolve(currentState(), db)
            val wide = LocalSize.current.width >= WIDE_SIZE.width
            val snapshot = readSnapshot(context, db, pair, withTrend = wide)
            val launch = pair?.let { ConverterLaunch.convert(context, it.from, it.to) } ?: ConverterLaunch.openConverter(context)
            WidgetBody(snapshot = snapshot, launchIntent = launch)
        }
    }
}

@Composable
private fun WidgetBody(
    snapshot: WidgetSnapshot,
    launchIntent: Intent,
) {
    val context = LocalContext.current
    GlanceTheme {
        Row(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(imageProvider = ImageProvider(R.drawable.widget_background))
                    .clickable(actionStartActivity(launchIntent))
                    .padding(WIDGET_PADDING),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = snapshot.rateLine(context),
                    maxLines = 1,
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = RATE_FONT_SIZE,
                            fontWeight = FontWeight.Bold,
                        ),
                )
                Text(
                    text = snapshot.footerLine(context),
                    maxLines = 1,
                    style =
                        TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = FOOTER_FONT_SIZE,
                        ),
                )
            }
            snapshot.trend?.let { trend ->
                Spacer(GlanceModifier.width(SPARKLINE_GAP))
                Image(
                    provider = ImageProvider(trend),
                    contentDescription = null,
                    modifier = GlanceModifier.size(SPARKLINE_WIDTH, SPARKLINE_HEIGHT),
                )
            }
        }
    }
}

private fun readSnapshot(
    context: Context,
    db: Database,
    pair: CurrencyPair?,
    withTrend: Boolean,
): WidgetSnapshot {
    val rates = db.getExchangeRatesBlocking()
    val converted =
        pair?.let { rates?.convert(BigDecimal.ONE, it.from, it.to) }?.roundForDisplay(WIDGET_DISPLAY_SCALE)
    val trend =
        if (withTrend && pair != null) {
            trendBitmap(context, sparklinePoints(db.getCachedTimeline(db.getApiProvider(), pair.from, pair.to), LocalDate.now()))
        } else {
            null
        }
    return WidgetSnapshot(
        pair = pair,
        converted = converted,
        date = formatRatesTimestamp(db.getDateFormatBlocking(), rates?.date, rates?.time),
        hasAnyCachedRate = rates != null,
        trend = trend,
    )
}

// Green when the rate ended the month higher, red when lower. Null when
// there's no cached history for the pair (the timeline hasn't loaded it yet).
private fun trendBitmap(
    context: Context,
    points: List<BigDecimal>,
): Bitmap? {
    if (points.isEmpty()) return null
    val density = context.resources.displayMetrics.density
    val rising = points.last() >= points.first()
    return sparklineBitmap(
        points = points,
        widthPx = (SPARKLINE_WIDTH.value * density).toInt(),
        heightPx = (SPARKLINE_HEIGHT.value * density).toInt(),
        strokePx = SPARKLINE_STROKE.value * density,
        color = ContextCompat.getColor(context, if (rising) R.color.rate_diff_positive else R.color.app_error),
    )
}

private data class WidgetSnapshot(
    val pair: CurrencyPair?,
    val converted: BigDecimal?,
    val date: String?,
    val hasAnyCachedRate: Boolean,
    val trend: Bitmap?,
) {
    fun rateLine(context: Context): String =
        if (pair != null && converted != null) {
            context.getString(
                R.string.widget_rate_line,
                pair.from.iso4217Alpha(),
                converted.toPlainString(),
                pair.to.iso4217Alpha(),
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
