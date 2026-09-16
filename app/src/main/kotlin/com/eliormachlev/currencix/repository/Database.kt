package com.eliormachlev.currencix.repository

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.LiveData
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.AppTheme
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.ExchangeRates
import com.eliormachlev.currencix.model.Fee
import com.eliormachlev.currencix.model.FeeType
import com.eliormachlev.currencix.model.KeyboardType
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.SavedCart
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.repository.persistence.PersistenceKey
import com.eliormachlev.currencix.repository.persistence.PrefStore
import com.eliormachlev.currencix.repository.persistence.WidgetRefreshBus
import com.eliormachlev.currencix.repository.persistence.prefStore
import com.eliormachlev.currencix.util.KEY_RATES_BASE
import com.eliormachlev.currencix.util.KEY_RATES_DATE
import com.eliormachlev.currencix.util.KEY_RATES_PROVIDER
import com.eliormachlev.currencix.util.KEY_RATES_TIME
import com.eliormachlev.currencix.util.NO_PROVIDER_ID
import com.eliormachlev.currencix.util.toLocalDate
import com.eliormachlev.currencix.util.toMillis
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

// Sentinel for "no historical date stored" in the millis-since-epoch pref.
// -1L is used because it can't collide with any real epoch millis (1970-01-01
// stores as 0L; anything after is positive).
private const val NO_HISTORICAL_DATE = -1L

// Metadata keys inside the RATES namespace start with "_" to distinguish them
// from currency-code entries (e.g. "USD", "EUR"). Matches the legacy
// SharedPreferences layout so BackupManager exports keep round-tripping.
private const val METADATA_KEY_PREFIX = "_"

// LAST_STATE keys.
private const val KEY_LAST_STATE_FROM = "_last_from"
private const val KEY_LAST_STATE_TO = "_last_to"
private const val KEY_IS_UPDATING = "_isUpdating"
private const val KEY_HISTORICAL_DATE = "_historical_date"

// STARRED_CURRENCIES keys.
private const val KEY_STARS_ORDER = "_starsOrder"
private const val KEY_STARRED_ENABLED = "_starredActive"

// APP (prefs) keys.
private const val KEY_API = "_api"
private const val KEY_OPEN_EXCHANGERATES_API_KEY = "_api_openExchangeratesApiKey"
private const val KEY_THEME = "_theme"
private const val KEY_FEES_JSON = "_fees_json"
private const val KEY_ACTIVE_EXCHANGE_ID = "_active_exchange_id"
private const val KEY_ACTIVE_BANK_ID = "_active_bank_id"
private const val KEY_PREVIEW_CONVERSION_ENABLED = "_previewConversionEnabled"
private const val KEY_KEYBOARD_TYPE = "_keyboardType"
private const val KEY_HAPTIC_FEEDBACK = "_hapticFeedback"
private const val KEY_DECIMAL_PLACES = "_decimalPlaces"
private const val KEY_CHART_GRID = "_chartGrid"
private const val KEY_CHART_X_AXIS_LABEL = "_chartXAxisLabel"
private const val KEY_CHART_Y_AXIS_LABEL = "_chartYAxisLabel"
private const val KEY_CHART_HIGHLIGHT_EXTREMES = "_chartHighlightExtremes"
private const val KEY_CHART_HIGHLIGHT_PERIOD_CHANGE = "_chartHighlightPeriodChange"
private const val KEY_DATE_FORMAT = "_dateFormat"
private const val DEFAULT_DATE_FORMAT = "dd/MM/yy HH:mm"
private const val KEY_CART_CURRENT_JSON = "_cart_current_json"
private const val KEY_CARTS_SAVED_JSON = "_carts_saved_json"

// Auto-refresh (#151): default OFF at first launch — opting-in is explicit
// via Settings (until #147 onboarding lands the hero opt-in).
private const val KEY_AUTO_REFRESH_ENABLED = "_autoRefreshEnabled"

// Optional user override for the WorkManager refresh cadence. `null` means
// "use the provider-recommended default"; a positive Int overrides it.
private const val KEY_AUTO_REFRESH_INTERVAL_OVERRIDE = "_autoRefreshIntervalMinutesOverride"

// First-run onboarding gate (#147). Default `false` — the spotlight tour runs
// on the very first cold-start, then flips to `true` on Skip/Finish so future
// launches skip straight to the hero. Debug builds can flip it back via the
// "Reset onboarding" preference so QA can replay the tour.
private const val KEY_HAS_SEEN_ONBOARDING = "_hasSeenOnboarding"

private const val DEFAULT_FROM_CURRENCY = "USD"
private const val DEFAULT_TO_CURRENCY = "EUR"

// Mappers shared by the LiveData and Flow getters for each preference so both
// paths derive from a single source of truth (see #149 pilot).
private val apiProviderMapper: (Preferences) -> ApiProvider = {
    ApiProvider.fromId(it[intPreferencesKey(KEY_API)] ?: NO_PROVIDER_ID)
}
private val openExchangeratesApiKeyMapper: (Preferences) -> String? = {
    it[stringPreferencesKey(KEY_OPEN_EXCHANGERATES_API_KEY)]
}
private val previewConversionEnabledMapper: (Preferences) -> Boolean = {
    it[booleanPreferencesKey(KEY_PREVIEW_CONVERSION_ENABLED)] ?: false
}
private val keyboardTypeMapper: (Preferences) -> KeyboardType = {
    KeyboardType.fromOrdinal(it[intPreferencesKey(KEY_KEYBOARD_TYPE)] ?: KeyboardType.DEFAULT.ordinal)
}
private val hapticFeedbackEnabledMapper: (Preferences) -> Boolean = {
    it[booleanPreferencesKey(KEY_HAPTIC_FEEDBACK)] ?: true
}
private val decimalPlacesMapper: (Preferences) -> Int = {
    (it[stringPreferencesKey(KEY_DECIMAL_PLACES)] ?: "2").toIntOrNull()?.coerceIn(0, 6) ?: 2
}
private val dateFormatMapper: (Preferences) -> String = {
    it[stringPreferencesKey(KEY_DATE_FORMAT)] ?: DEFAULT_DATE_FORMAT
}
private val feesMapper: (Preferences) -> ImmutableList<Fee> = {
    parseFeeList(it[stringPreferencesKey(KEY_FEES_JSON)] ?: "[]").toImmutableList()
}
private val activeExchangeIdMapper: (Preferences) -> String? = {
    it[stringPreferencesKey(KEY_ACTIVE_EXCHANGE_ID)]
}
private val activeBankIdMapper: (Preferences) -> String? = {
    it[stringPreferencesKey(KEY_ACTIVE_BANK_ID)]
}
private val starredCurrenciesMapper: (Preferences) -> ImmutableList<Currency> = { prefs ->
    readOrderedStarCodes(prefs).mapNotNull { Currency.fromString(it) }.toImmutableList()
}
private val filterStarredEnabledMapper: (Preferences) -> Boolean = {
    it[booleanPreferencesKey(KEY_STARRED_ENABLED)] ?: false
}
private val isUpdatingMapper: (Preferences) -> Boolean = {
    it[booleanPreferencesKey(KEY_IS_UPDATING)] ?: false
}
private val autoRefreshEnabledMapper: (Preferences) -> Boolean = {
    it[booleanPreferencesKey(KEY_AUTO_REFRESH_ENABLED)] ?: false
}
private val autoRefreshIntervalOverrideMapper: (Preferences) -> Int? = {
    // 0 sentinel means "no override" — DataStore has no `intOrNull` primitive
    // and we want the pref backing store to keep working with plain Ints.
    val v = it[intPreferencesKey(KEY_AUTO_REFRESH_INTERVAL_OVERRIDE)] ?: 0
    if (v <= 0) null else v
}
private val hasSeenOnboardingMapper: (Preferences) -> Boolean = {
    it[booleanPreferencesKey(KEY_HAS_SEEN_ONBOARDING)] ?: false
}
private val historicalLiveDateMapper: (Preferences) -> LocalDate? = { prefs ->
    val v = prefs[longPreferencesKey(KEY_HISTORICAL_DATE)] ?: NO_HISTORICAL_DATE
    if (v == NO_HISTORICAL_DATE) null else v.toLocalDate()
}

// Ordered-star-codes parsing lives at file scope so the mapper above (invoked
// by both LiveData and Flow getters) can share it with the toggle/reorder
// mutators without threading a Database instance through.
private fun readOrderedStarCodes(prefs: Preferences): List<String> {
    val stored = prefs[stringPreferencesKey(KEY_STARS_ORDER)] ?: return emptyList()
    return if (stored.isEmpty()) emptyList() else stored.split(",")
}

// Fees JSON parsing lives at file scope so the mapper above (which is invoked
// by both LiveData and Flow getters) can share it with the blocking accessor
// and the fee mutators without threading a Database instance through.
private fun parseFeeList(json: String): List<Fee> =
    try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i -> parseFeeEntry(arr.optJSONObject(i)) }
    } catch (e: JSONException) {
        Timber.tag("Database").w(e, "Malformed fee JSON, resetting")
        emptyList()
    }

private fun parseFeeEntry(obj: JSONObject?): Fee? {
    obj ?: return null
    val id = obj.optString("id", "").ifEmpty { UUID.randomUUID().toString() }
    val name = obj.optString("name", "")
    val percent = obj.optString("percent", "0").toBigDecimalOrNull() ?: return null
    val isActive = obj.optBoolean("isActive", true)
    return when (FeeType.fromWire(obj.optString("type"))) {
        FeeType.GLOBAL_EXCHANGE -> Fee.GlobalExchange(id, name, percent, isActive)
        FeeType.GLOBAL_BANK -> Fee.GlobalBank(id, name, percent, isActive)
        FeeType.SPECIFIC_PAIR ->
            Fee.SpecificPair(
                id = id,
                name = name,
                percent = percent,
                from = obj.optString("from", ""),
                to = obj.optString("to", ""),
                bothWays = obj.optBoolean("bothWays", false),
                isActive = isActive,
            )
        null -> null
    }
}

class Database(
    private val context: Context,
) {
    private val ratesStore: PrefStore = PersistenceKey.RATES.prefStore(context)
    private val timelinesStore: PrefStore = PersistenceKey.TIMELINES.prefStore(context)
    private val lastStateStore: PrefStore = PersistenceKey.LAST_STATE.prefStore(context)
    private val starredStore: PrefStore = PersistenceKey.STARRED_CURRENCIES.prefStore(context)
    private val appStore: PrefStore = PersistenceKey.APP.prefStore(context)

    /*
     * current exchange rates from api =============================================================
     */

    fun insertExchangeRates(items: ExchangeRates) {
        // don't insert null-values. this would clear the cache
        val date = items.date ?: return
        ratesStore.edit {
            clear()
            this[stringPreferencesKey(KEY_RATES_DATE)] = date.toString()
            items.time?.let { this[stringPreferencesKey(KEY_RATES_TIME)] = it.toString() }
            items.base?.let { this[stringPreferencesKey(KEY_RATES_BASE)] = it.iso4217Alpha() }
            this[intPreferencesKey(KEY_RATES_PROVIDER)] = items.provider?.id ?: NO_PROVIDER_ID
            items.rates?.forEach { rate ->
                this[stringPreferencesKey(rate.currency.iso4217Alpha())] = rate.value.toPlainString()
            }
        }
        WidgetRefreshBus.signal()
    }

    fun getExchangeRates(): LiveData<ExchangeRates?> = ratesStore.mappedLiveData(::parseExchangeRates)

    fun getDate(): LocalDate? = ratesStore.snapshot()[stringPreferencesKey(KEY_RATES_DATE)]?.let { LocalDate.parse(it) }

    private fun parseExchangeRates(prefs: Preferences): ExchangeRates? {
        val baseString = prefs[stringPreferencesKey(KEY_RATES_BASE)] ?: return null
        val dateString = prefs[stringPreferencesKey(KEY_RATES_DATE)] ?: return null
        val rates =
            prefs
                .asMap()
                .entries
                .filter { (k, _) -> !k.name.startsWith(METADATA_KEY_PREFIX) }
                .sortedBy { (k, _) -> k.name }
                .mapNotNull { (k, v) ->
                    val str = v as? String ?: return@mapNotNull null
                    Currency.fromString(k.name)?.let { Rate(it, str.toBigDecimal()) }
                }
        if (rates.isEmpty()) return null
        return ExchangeRates(
            success = true,
            error = null,
            base = Currency.fromString(baseString),
            date = LocalDate.parse(dateString),
            time = prefs[stringPreferencesKey(KEY_RATES_TIME)]?.let { LocalTime.parse(it) },
            rates = rates,
            provider = ApiProvider.fromId(prefs[intPreferencesKey(KEY_RATES_PROVIDER)] ?: NO_PROVIDER_ID),
        )
    }

    /*
     * cached timelines ============================================================================
     *
     * Historical rate observations are immutable once a business day closes,
     * so we persist per-pair timelines and refresh only the tail on each open.
     * Keyed by "<providerId>|<baseCode>|<symbolCode>"; the JSON value is a
     * flat {date -> plainString value} map (provider/base/symbol are already
     * in the key).
     */

    private fun timelineKey(
        providerId: Int,
        base: Currency,
        symbol: Currency,
    ): String = "$providerId|${base.iso4217Alpha()}|${symbol.iso4217Alpha()}"

    fun getCachedTimeline(
        provider: ApiProvider,
        base: Currency,
        symbol: Currency,
    ): Timeline? {
        val json =
            timelinesStore.snapshot()[stringPreferencesKey(timelineKey(provider.id, base, symbol))]
                ?: return null
        return try {
            val obj = JSONObject(json)
            val rates = sortedMapOf<LocalDate, Rate>()
            obj.keys().forEach { key ->
                val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: return@forEach
                val value = obj.optString(key).toBigDecimalOrNull() ?: return@forEach
                rates[date] = Rate(symbol, value)
            }
            if (rates.isEmpty()) return null
            Timeline(
                success = true,
                error = null,
                base = base.iso4217Alpha(),
                startDate = rates.keys.first(),
                endDate = rates.keys.last(),
                rates = rates,
                provider = provider,
            )
        } catch (e: JSONException) {
            Timber.tag("Database").w(e, "Malformed cached timeline, dropping")
            null
        }
    }

    fun putCachedTimeline(
        timeline: Timeline,
        base: Currency,
        symbol: Currency,
    ) {
        val provider = timeline.provider ?: return
        val rates = timeline.rates ?: return
        val obj = JSONObject()
        rates.forEach { (date, rate) ->
            obj.put(date.toString(), rate.value.toPlainString())
        }
        timelinesStore.edit {
            this[stringPreferencesKey(timelineKey(provider.id, base, symbol))] = obj.toString()
        }
    }

    /*
     * last state ==================================================================================
     */

    fun saveLastUsedRates(
        from: Currency?,
        to: Currency?,
    ) {
        lastStateStore.edit {
            from?.let { this[stringPreferencesKey(KEY_LAST_STATE_FROM)] = it.iso4217Alpha() }
            to?.let { this[stringPreferencesKey(KEY_LAST_STATE_TO)] = it.iso4217Alpha() }
        }
        WidgetRefreshBus.signal()
    }

    fun getLastBaseCurrency(): LiveData<Currency?> =
        lastStateStore.mappedLiveData { prefs ->
            Currency.fromString(prefs[stringPreferencesKey(KEY_LAST_STATE_FROM)] ?: DEFAULT_FROM_CURRENCY)
        }

    fun getLastDestinationCurrency(): LiveData<Currency?> =
        lastStateStore.mappedLiveData { prefs ->
            Currency.fromString(prefs[stringPreferencesKey(KEY_LAST_STATE_TO)] ?: DEFAULT_TO_CURRENCY)
        }

    // Synchronous readers for callers that can't wait for the LiveData to
    // become active (e.g. the cart's initial state, built before any
    // observer is attached).
    fun getLastBaseCurrencyBlocking(): Currency? =
        Currency.fromString(lastStateStore.snapshot()[stringPreferencesKey(KEY_LAST_STATE_FROM)] ?: DEFAULT_FROM_CURRENCY)

    fun getLastDestinationCurrencyBlocking(): Currency? =
        Currency.fromString(lastStateStore.snapshot()[stringPreferencesKey(KEY_LAST_STATE_TO)] ?: DEFAULT_TO_CURRENCY)

    fun setUpdating(updating: Boolean) {
        lastStateStore.edit { this[booleanPreferencesKey(KEY_IS_UPDATING)] = updating }
    }

    fun isUpdating(): LiveData<Boolean> = lastStateStore.mappedLiveData(isUpdatingMapper)

    fun isUpdatingFlow(): Flow<Boolean> = lastStateStore.mappedFlow(isUpdatingMapper)

    fun isUpdatingBlocking(): Boolean = isUpdatingMapper(lastStateStore.snapshot())

    fun setHistoricalDate(date: LocalDate?) {
        lastStateStore.edit { this[longPreferencesKey(KEY_HISTORICAL_DATE)] = date?.toMillis() ?: NO_HISTORICAL_DATE }
    }

    fun getHistoricalLiveDate(): LiveData<LocalDate?> = lastStateStore.mappedLiveData(historicalLiveDateMapper)

    fun getHistoricalLiveDateFlow(): Flow<LocalDate?> = lastStateStore.mappedFlow(historicalLiveDateMapper)

    fun getHistoricalLiveDateBlocking(): LocalDate? = historicalLiveDateMapper(lastStateStore.snapshot())

    fun getHistoricalDate(): LocalDate? =
        when (val v = lastStateStore.snapshot()[longPreferencesKey(KEY_HISTORICAL_DATE)] ?: NO_HISTORICAL_DATE) {
            NO_HISTORICAL_DATE -> null
            else -> v.toLocalDate()
        }

    /*
     * starred currencies ==========================================================================
     */

    private fun writeOrderedStarCodes(codes: List<String>) {
        starredStore.edit { this[stringPreferencesKey(KEY_STARS_ORDER)] = codes.joinToString(",") }
    }

    fun toggleCurrencyStar(currency: Currency) {
        val code = currency.iso4217Alpha()
        val current = readOrderedStarCodes(starredStore.snapshot())
        val next = if (current.contains(code)) current.minus(code) else current.plus(code)
        writeOrderedStarCodes(next)
    }

    // ImmutableList so Compose stability inference can skip recomposition
    // when the collection identity changes but the content is equal (#161).
    fun getStarredCurrencies(): LiveData<ImmutableList<Currency>> = starredStore.mappedLiveData(starredCurrenciesMapper)

    fun getStarredCurrenciesFlow(): Flow<ImmutableList<Currency>> = starredStore.mappedFlow(starredCurrenciesMapper)

    fun getStarredCurrenciesBlocking(): ImmutableList<Currency> = starredCurrenciesMapper(starredStore.snapshot())

    fun setStarredCurrencyOrder(currencies: List<Currency>) {
        writeOrderedStarCodes(currencies.map { it.iso4217Alpha() })
    }

    fun isFilterStarredEnabled(): LiveData<Boolean> = starredStore.mappedLiveData(filterStarredEnabledMapper)

    fun isFilterStarredEnabledFlow(): Flow<Boolean> = starredStore.mappedFlow(filterStarredEnabledMapper)

    fun isFilterStarredEnabledBlocking(): Boolean = filterStarredEnabledMapper(starredStore.snapshot())

    fun toggleStarredActive() {
        starredStore.edit {
            val current = this[booleanPreferencesKey(KEY_STARRED_ENABLED)] ?: false
            this[booleanPreferencesKey(KEY_STARRED_ENABLED)] = !current
        }
    }

    /*
     * preferences =================================================================================
     */

    // api

    fun setApiProvider(api: ApiProvider) {
        appStore.edit { this[intPreferencesKey(KEY_API)] = api.id }
    }

    fun getApiProvider(): ApiProvider = ApiProvider.fromId(appStore.snapshot()[intPreferencesKey(KEY_API)] ?: NO_PROVIDER_ID)

    fun getApiProviderAsync(): LiveData<ApiProvider> = appStore.mappedLiveData(apiProviderMapper)

    fun getApiProviderFlow(): Flow<ApiProvider> = appStore.mappedFlow(apiProviderMapper)

    fun setOpenExchangeRatesApiKey(id: String?) {
        appStore.edit {
            if (id == null) {
                remove(stringPreferencesKey(KEY_OPEN_EXCHANGERATES_API_KEY))
            } else {
                this[stringPreferencesKey(KEY_OPEN_EXCHANGERATES_API_KEY)] = id
            }
        }
    }

    fun getOpenExchangeRatesApiKey(): String? = appStore.snapshot()[stringPreferencesKey(KEY_OPEN_EXCHANGERATES_API_KEY)]

    fun getOpenExchangeRatesApiKeyAsync(): LiveData<String?> = appStore.mappedLiveData(openExchangeratesApiKeyMapper)

    fun getOpenExchangeRatesApiKeyFlow(): Flow<String?> = appStore.mappedFlow(openExchangeratesApiKeyMapper)

    // theme

    fun setTheme(theme: AppTheme) {
        appStore.edit { this[intPreferencesKey(KEY_THEME)] = theme.id }
    }

    fun getTheme(): AppTheme = AppTheme.fromId(appStore.snapshot()[intPreferencesKey(KEY_THEME)] ?: AppTheme.DEFAULT.id)

    fun isPureBlackEnabled(): Boolean = getTheme().isPureBlack

    // fees

    // ImmutableList so Compose stability inference can skip recomposition
    // when the collection identity changes but the content is equal (#161).
    fun getFees(): LiveData<ImmutableList<Fee>> = appStore.mappedLiveData(feesMapper)

    fun getFeesFlow(): Flow<ImmutableList<Fee>> = appStore.mappedFlow(feesMapper)

    fun getFeesBlocking(): ImmutableList<Fee> = feesMapper(appStore.snapshot())

    fun addFee(fee: Fee) {
        writeFees(getFeesBlocking() + fee)
    }

    fun updateFee(fee: Fee) {
        writeFees(getFeesBlocking().map { if (it.id == fee.id) fee else it })
    }

    fun deleteFee(id: String) {
        writeFees(getFeesBlocking().filter { it.id != id })
    }

    // Active-picker IDs — which single named exchange / bank-or-card entry
    // participates in the fee stack. `null` means "no explicit pick"; the
    // FeeCalculator falls back to the first active entry of that category.

    fun getActiveExchangeId(): LiveData<String?> = appStore.mappedLiveData(activeExchangeIdMapper)

    fun getActiveExchangeIdFlow(): Flow<String?> = appStore.mappedFlow(activeExchangeIdMapper)

    fun getActiveExchangeIdBlocking(): String? = activeExchangeIdMapper(appStore.snapshot())

    fun setActiveExchangeId(id: String?) {
        appStore.edit {
            if (id == null) {
                remove(stringPreferencesKey(KEY_ACTIVE_EXCHANGE_ID))
            } else {
                this[stringPreferencesKey(KEY_ACTIVE_EXCHANGE_ID)] = id
            }
        }
    }

    fun getActiveBankId(): LiveData<String?> = appStore.mappedLiveData(activeBankIdMapper)

    fun getActiveBankIdFlow(): Flow<String?> = appStore.mappedFlow(activeBankIdMapper)

    fun getActiveBankIdBlocking(): String? = activeBankIdMapper(appStore.snapshot())

    fun setActiveBankId(id: String?) {
        appStore.edit {
            if (id == null) {
                remove(stringPreferencesKey(KEY_ACTIVE_BANK_ID))
            } else {
                this[stringPreferencesKey(KEY_ACTIVE_BANK_ID)] = id
            }
        }
    }

    private fun writeFees(list: List<Fee>) {
        appStore.edit { this[stringPreferencesKey(KEY_FEES_JSON)] = serializeFeeList(list) }
    }

    private fun serializeFeeList(list: List<Fee>): String {
        val arr = JSONArray()
        list.forEach { fee ->
            val obj = JSONObject()
            obj.put("id", fee.id)
            obj.put("name", fee.name)
            obj.put("percent", fee.percent.toPlainString())
            obj.put("isActive", fee.isActive)
            obj.put("type", fee.type.wire)
            if (fee is Fee.SpecificPair) {
                obj.put("from", fee.from)
                obj.put("to", fee.to)
                obj.put("bothWays", fee.bothWays)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    // auto-refresh (#151) — WorkManager-driven background rate refresh, opt-in.

    fun setAutoRefreshEnabled(enabled: Boolean) {
        appStore.edit { this[booleanPreferencesKey(KEY_AUTO_REFRESH_ENABLED)] = enabled }
    }

    fun isAutoRefreshEnabledFlow(): Flow<Boolean> = appStore.mappedFlow(autoRefreshEnabledMapper)

    fun isAutoRefreshEnabledBlocking(): Boolean = autoRefreshEnabledMapper(appStore.snapshot())

    fun setAutoRefreshIntervalMinutesOverride(minutes: Int?) {
        appStore.edit {
            if (minutes == null || minutes <= 0) {
                remove(intPreferencesKey(KEY_AUTO_REFRESH_INTERVAL_OVERRIDE))
            } else {
                this[intPreferencesKey(KEY_AUTO_REFRESH_INTERVAL_OVERRIDE)] = minutes
            }
        }
    }

    fun getAutoRefreshIntervalMinutesOverrideFlow(): Flow<Int?> = appStore.mappedFlow(autoRefreshIntervalOverrideMapper)

    fun getAutoRefreshIntervalMinutesOverrideBlocking(): Int? = autoRefreshIntervalOverrideMapper(appStore.snapshot())

    // onboarding (#147) — first-run spotlight tour gate.

    fun setHasSeenOnboarding(seen: Boolean) {
        appStore.edit { this[booleanPreferencesKey(KEY_HAS_SEEN_ONBOARDING)] = seen }
    }

    fun getHasSeenOnboardingFlow(): Flow<Boolean> = appStore.mappedFlow(hasSeenOnboardingMapper)

    fun getHasSeenOnboardingBlocking(): Boolean = hasSeenOnboardingMapper(appStore.snapshot())

    // preview conversion

    fun setPreviewConversionEnabled(enabled: Boolean) {
        appStore.edit { this[booleanPreferencesKey(KEY_PREVIEW_CONVERSION_ENABLED)] = enabled }
    }

    fun isPreviewConversionEnabled(): LiveData<Boolean> = appStore.mappedLiveData(previewConversionEnabledMapper)

    fun isPreviewConversionEnabledFlow(): Flow<Boolean> = appStore.mappedFlow(previewConversionEnabledMapper)

    fun isPreviewConversionEnabledBlocking(): Boolean = previewConversionEnabledMapper(appStore.snapshot())

    // keyboard type

    fun setKeyboardType(type: KeyboardType) {
        appStore.edit { this[intPreferencesKey(KEY_KEYBOARD_TYPE)] = type.ordinal }
    }

    fun getKeyboardType(): LiveData<KeyboardType> = appStore.mappedLiveData(keyboardTypeMapper)

    fun getKeyboardTypeFlow(): Flow<KeyboardType> = appStore.mappedFlow(keyboardTypeMapper)

    fun getKeyboardTypeBlocking(): KeyboardType =
        KeyboardType.fromOrdinal(appStore.snapshot()[intPreferencesKey(KEY_KEYBOARD_TYPE)] ?: KeyboardType.DEFAULT.ordinal)

    // haptic feedback

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        appStore.edit { this[booleanPreferencesKey(KEY_HAPTIC_FEEDBACK)] = enabled }
    }

    fun isHapticFeedbackEnabled(): LiveData<Boolean> = appStore.mappedLiveData(hapticFeedbackEnabledMapper)

    fun isHapticFeedbackEnabledFlow(): Flow<Boolean> = appStore.mappedFlow(hapticFeedbackEnabledMapper)

    fun isHapticFeedbackEnabledBlocking(): Boolean = appStore.snapshot()[booleanPreferencesKey(KEY_HAPTIC_FEEDBACK)] ?: true

    // decimal places

    fun setDecimalPlaces(places: Int) {
        // Historical shape kept: stored as String so old backups round-trip
        // (the SharedPreferences preference-screen used to write via
        // ListPreference which stringifies its value).
        appStore.edit { this[stringPreferencesKey(KEY_DECIMAL_PLACES)] = places.toString() }
    }

    fun getDecimalPlaces(): LiveData<Int> = appStore.mappedLiveData(decimalPlacesMapper)

    fun getDecimalPlacesFlow(): Flow<Int> = appStore.mappedFlow(decimalPlacesMapper)

    fun getDecimalPlacesBlocking(): Int = decimalPlacesMapper(appStore.snapshot())

    // graph options — all default to true (feature-on) so opting out is explicit.

    private fun setBool(
        key: String,
        value: Boolean,
    ) = appStore.edit { this[booleanPreferencesKey(key)] = value }

    private fun boolLive(
        key: String,
        default: Boolean,
    ): LiveData<Boolean> = appStore.mappedLiveData { it[booleanPreferencesKey(key)] ?: default }

    private fun boolBlocking(
        key: String,
        default: Boolean,
    ): Boolean = appStore.snapshot()[booleanPreferencesKey(key)] ?: default

    fun setChartGridEnabled(enabled: Boolean) = setBool(KEY_CHART_GRID, enabled)

    fun isChartGridEnabled(): LiveData<Boolean> = boolLive(KEY_CHART_GRID, true)

    fun isChartGridEnabledBlocking(): Boolean = boolBlocking(KEY_CHART_GRID, true)

    fun setChartXAxisLabelEnabled(enabled: Boolean) = setBool(KEY_CHART_X_AXIS_LABEL, enabled)

    fun isChartXAxisLabelEnabled(): LiveData<Boolean> = boolLive(KEY_CHART_X_AXIS_LABEL, true)

    fun isChartXAxisLabelEnabledBlocking(): Boolean = boolBlocking(KEY_CHART_X_AXIS_LABEL, true)

    fun setChartYAxisLabelEnabled(enabled: Boolean) = setBool(KEY_CHART_Y_AXIS_LABEL, enabled)

    fun isChartYAxisLabelEnabled(): LiveData<Boolean> = boolLive(KEY_CHART_Y_AXIS_LABEL, true)

    fun isChartYAxisLabelEnabledBlocking(): Boolean = boolBlocking(KEY_CHART_Y_AXIS_LABEL, true)

    fun setChartHighlightExtremesEnabled(enabled: Boolean) = setBool(KEY_CHART_HIGHLIGHT_EXTREMES, enabled)

    fun isChartHighlightExtremesEnabled(): LiveData<Boolean> = boolLive(KEY_CHART_HIGHLIGHT_EXTREMES, true)

    fun isChartHighlightExtremesEnabledBlocking(): Boolean = boolBlocking(KEY_CHART_HIGHLIGHT_EXTREMES, true)

    fun setChartHighlightPeriodChangeEnabled(enabled: Boolean) = setBool(KEY_CHART_HIGHLIGHT_PERIOD_CHANGE, enabled)

    fun isChartHighlightPeriodChangeEnabled(): LiveData<Boolean> = boolLive(KEY_CHART_HIGHLIGHT_PERIOD_CHANGE, true)

    fun isChartHighlightPeriodChangeEnabledBlocking(): Boolean = boolBlocking(KEY_CHART_HIGHLIGHT_PERIOD_CHANGE, true)

    fun setDateFormat(pattern: String) {
        appStore.edit { this[stringPreferencesKey(KEY_DATE_FORMAT)] = pattern }
    }

    fun getDateFormat(): LiveData<String> = appStore.mappedLiveData(dateFormatMapper)

    fun getDateFormatFlow(): Flow<String> = appStore.mappedFlow(dateFormatMapper)

    fun getDateFormatBlocking(): String = appStore.snapshot()[stringPreferencesKey(KEY_DATE_FORMAT)] ?: DEFAULT_DATE_FORMAT

    // cart ==================================================================================

    fun getCurrentCart(): LiveData<SavedCart?> = appStore.mappedLiveData { parseCart(it[stringPreferencesKey(KEY_CART_CURRENT_JSON)]) }

    fun getCurrentCartBlocking(): SavedCart? = parseCart(appStore.snapshot()[stringPreferencesKey(KEY_CART_CURRENT_JSON)])

    fun setCurrentCart(cart: SavedCart?) {
        appStore.edit {
            if (cart == null) {
                remove(stringPreferencesKey(KEY_CART_CURRENT_JSON))
            } else {
                this[stringPreferencesKey(KEY_CART_CURRENT_JSON)] = serializeCart(cart).toString()
            }
        }
    }

    fun getSavedCarts(): LiveData<List<SavedCart>> =
        appStore.mappedLiveData { parseCartList(it[stringPreferencesKey(KEY_CARTS_SAVED_JSON)] ?: "[]") }

    fun getSavedCartsBlocking(): List<SavedCart> = parseCartList(appStore.snapshot()[stringPreferencesKey(KEY_CARTS_SAVED_JSON)] ?: "[]")

    fun saveCart(cart: SavedCart) {
        val existing = getSavedCartsBlocking()
        val next =
            if (existing.any { it.id == cart.id }) {
                existing.map { if (it.id == cart.id) cart else it }
            } else {
                existing + cart
            }
        writeSavedCarts(next)
    }

    fun deleteSavedCart(id: String) {
        writeSavedCarts(getSavedCartsBlocking().filter { it.id != id })
    }

    private fun writeSavedCarts(list: List<SavedCart>) {
        appStore.edit { this[stringPreferencesKey(KEY_CARTS_SAVED_JSON)] = serializeCartList(list) }
    }
}
