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
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
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
import com.eliormachlev.currencix.util.AppDispatchers
import com.eliormachlev.currencix.util.roundForDisplay
import com.eliormachlev.currencix.util.toHumanReadableNumber
import com.eliormachlev.currencix.view.compose.theme.Spacing
import com.eliormachlev.currencix.view.main.ConverterLaunch
import com.eliormachlev.currencix.view.main.formatRatesTimestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate

private const val WIDGET_DISPLAY_SCALE = 4

// Amounts above one are prices, shown with cents.
private const val WIDGET_AMOUNT_SCALE = 2

private val WIDGET_PADDING = Spacing.Medium
private val RATE_FONT_SIZE = 18.sp
private val FOOTER_FONT_SIZE = 11.sp
private val BUTTON_SIZE = 32.dp
private val BUTTON_ICON_SIZE = 18.dp
private val BUTTON_FONT_SIZE = 12.sp

/**
 * Home-screen widget rendering a pair — the converter's last-used one, or its
 * own ([WidgetPairState], set in [WidgetConfigureActivity]) — its cached
 * rate for one of [WidgetAmounts] (cycled by a button, per widget), a swap
 * button, and on wider widgets a 30-day trend line. Tapping the rate opens
 * the converter on that pair and amount. Backed by the same DataStore namespaces the app
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
        private val scope = CoroutineScope(SupervisorJob() + AppDispatchers.production.default)

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
private val SPARKLINE_GAP = Spacing.Medium

/**
 * One widget's pair, in its own Glance state: by default it follows the
 * converter's last-used pair; [WidgetConfigureActivity] can pin it to a pair
 * of its own.
 */
internal object WidgetPairState {
    val FOLLOW_CONVERTER = booleanPreferencesKey("follow_converter")
    val FROM = stringPreferencesKey("from")
    val TO = stringPreferencesKey("to")

    /** Which of [WidgetAmounts.PRESETS] the widget converts; the first when unset. */
    val AMOUNT_INDEX = intPreferencesKey("amount_index")

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
        val from = db.lastState.lastBaseCurrency.get() ?: return null
        val to = db.lastState.lastDestinationCurrency.get() ?: return null
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
        provideContent { WidgetContent(context, db, currentState()) }
    }

    // The widget picker's preview (Android 15+, see publishWidgetPreviews): the
    // widget as it would look added now — the converter's pair and its cached
    // rate, or the empty state before any rates are cached.
    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val db = Database(context)
        provideContent { WidgetContent(context, db, emptyPreferences()) }
    }
}

// A widget with [state] (its own pair, or none to follow the converter).
@Composable
private fun WidgetContent(
    context: Context,
    db: Database,
    state: Preferences,
) {
    val pair = WidgetPairState.resolve(state, db)
    val amount = WidgetAmounts.at(state[WidgetPairState.AMOUNT_INDEX])
    val wide = LocalSize.current.width >= WIDE_SIZE.width
    val snapshot = readSnapshot(context, db, pair, amount, withTrend = wide)
    val launch =
        pair?.let { ConverterLaunch.convert(context, it.from, it.to, amount.takeIf { a -> a > BigDecimal.ONE }) }
            ?: ConverterLaunch.openConverter(context)
    WidgetBody(snapshot = snapshot, launchIntent = launch)
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
                    .padding(WIDGET_PADDING),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(launchIntent))) {
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
            if (snapshot.pair != null) WidgetButtons(snapshot.amount)
        }
    }
}

// "×100" (the next amount on a tap) and ⇄ (swap the pair), at the end.
@Composable
private fun WidgetButtons(amount: BigDecimal) {
    val context = LocalContext.current
    val label = WidgetAmounts.label(amount)
    Spacer(GlanceModifier.width(Spacing.Small))
    Box(
        modifier =
            GlanceModifier
                .size(BUTTON_SIZE)
                .cornerRadius(BUTTON_SIZE / 2)
                .background(GlanceTheme.colors.secondaryContainer)
                .clickable(actionRunCallback<CycleWidgetAmount>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = BUTTON_FONT_SIZE, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.semantics { contentDescription = context.getString(R.string.widget_amount_desc, label) },
        )
    }
    Spacer(GlanceModifier.width(Spacing.Tiny))
    Box(
        modifier =
            GlanceModifier
                .size(BUTTON_SIZE)
                .cornerRadius(BUTTON_SIZE / 2)
                .background(GlanceTheme.colors.secondaryContainer)
                .clickable(actionRunCallback<SwapWidgetPair>()),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_swap_horiz),
            contentDescription = context.getString(R.string.desc_toggle_currencies),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
            modifier = GlanceModifier.size(BUTTON_ICON_SIZE),
        )
    }
}

private fun readSnapshot(
    context: Context,
    db: Database,
    pair: CurrencyPair?,
    amount: BigDecimal,
    withTrend: Boolean,
): WidgetSnapshot {
    val rates = db.rates.getExchangeRatesBlocking()
    val scale = if (amount > BigDecimal.ONE) WIDGET_AMOUNT_SCALE else WIDGET_DISPLAY_SCALE
    val converted =
        pair?.let { rates?.convert(amount, it.from, it.to) }?.roundForDisplay(scale)?.toHumanReadableNumber(context, decimalPlaces = scale)
    val trend =
        if (withTrend && pair != null) {
            trendBitmap(
                context,
                sparklinePoints(db.rates.getCachedTimeline(db.providers.apiProvider.get(), pair.from, pair.to), LocalDate.now()),
            )
        } else {
            null
        }
    return WidgetSnapshot(
        pair = pair,
        amount = amount,
        converted = converted,
        date = formatRatesTimestamp(db.display.dateFormat.get(), rates?.date, rates?.time),
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
    val amount: BigDecimal,
    // Already formatted for the locale ("1,234.50").
    val converted: String?,
    val date: String?,
    val hasAnyCachedRate: Boolean,
    val trend: Bitmap?,
) {
    fun rateLine(context: Context): String =
        if (pair != null && converted != null) {
            context.getString(
                R.string.widget_amount_line,
                amount.toHumanReadableNumber(context, decimalPlaces = 0),
                pair.from.iso4217Alpha(),
                converted,
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

/** The amounts the widget's button steps through: 1, 10, 100, 1,000, then 1 again. */
internal object WidgetAmounts {
    val PRESETS: List<BigDecimal> = listOf(1, 10, 100, 1000).map(::BigDecimal)
    private val THOUSAND = BigDecimal(1000)

    /** The preset at [index] (wrapping); the first for null. */
    fun at(index: Int?): BigDecimal = PRESETS[Math.floorMod(index ?: 0, PRESETS.size)]

    /** The index after [index]. */
    fun next(index: Int?): Int = Math.floorMod((index ?: 0) + 1, PRESETS.size)

    /** "×1", "×10", "×100", "×1K": short enough for the round button. */
    fun label(amount: BigDecimal): String =
        if (amount >=
            THOUSAND
        ) {
            "×${amount.divide(THOUSAND).toPlainString()}K"
        } else {
            "×${amount.toPlainString()}"
        }
}

/** The amount button: the widget converts the next of [WidgetAmounts]. */
internal class CycleWidgetAmount : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        updateAppWidgetState(context, glanceId) { state ->
            state[WidgetPairState.AMOUNT_INDEX] = WidgetAmounts.next(state[WidgetPairState.AMOUNT_INDEX])
        }
        CurrencyGlanceWidget.update(context, glanceId)
    }
}

/**
 * The swap button: the widget shows its pair the other way round, as a pair
 * of its own from then on (the converter's pair is left alone).
 */
internal class SwapWidgetPair : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val db = Database(context)
        updateAppWidgetState(context, glanceId) { state ->
            val pair = WidgetPairState.resolve(state, db) ?: return@updateAppWidgetState
            state[WidgetPairState.FOLLOW_CONVERTER] = false
            state[WidgetPairState.FROM] = pair.to.iso4217Alpha()
            state[WidgetPairState.TO] = pair.from.iso4217Alpha()
        }
        CurrencyGlanceWidget.update(context, glanceId)
    }
}
