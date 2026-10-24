package com.eliormachlev.currencix.model

import android.content.Context
import androidx.annotation.StringRes
import com.eliormachlev.currencix.model.provider.AllRatesToday
import com.eliormachlev.currencix.model.provider.BankOfCanada
import com.eliormachlev.currencix.model.provider.BankOfIsrael
import com.eliormachlev.currencix.model.provider.BankRossii
import com.eliormachlev.currencix.model.provider.FrankfurterApp
import com.eliormachlev.currencix.model.provider.InforEuro
import com.eliormachlev.currencix.model.provider.NorgesBank
import com.eliormachlev.currencix.model.provider.OpenExchangerates
import com.squareup.moshi.JsonClass
import java.net.URI
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private const val ID_FRANKFURTER_APP = 1
private const val ID_INFOR_EURO = 3
private const val ID_NORGES_BANK = 4
private const val ID_BANK_ROSSII = 5
private const val ID_BANK_OF_CANADA = 6
private const val ID_OPEN_EXCHANGERATES = 7
private const val ID_BANK_OF_ISRAEL = 8
private const val ID_ALL_RATES_TODAY = 9

// A table published every day at [hour]:[minute] in [zone].
private fun dailyAt(
    hour: Int,
    minute: Int,
    zone: String,
) = PublicationSchedule.Daily(LocalTime.of(hour, minute), ZoneId.of(zone))

/** How often a provider publishes new rates — the picker lists the more frequent ones first. */
enum class UpdateCadence { EVERY_MINUTE, HOURLY, DAILY, MONTHLY }

/**
 * How far a provider can be trusted. Anything but [RELIABLE] is hidden from
 * the pickers unless the user turns on "Show unreliable providers", and is
 * labelled with why when shown.
 */
enum class ProviderReliability {
    RELIABLE,

    /** Often down: requests fail for hours (HTTP 422 / 503). */
    DOWNTIME,

    /** Answers, but its rates don't match the market (e.g. set once a month). */
    INACCURATE_DATA,
    ;

    val isReliable: Boolean get() = this == RELIABLE
}

/**
 * Declaration order is the picker's order within a group ([pickerOrder]):
 * most useful first — wider coverage and steadier service. Nothing persists
 * the ordinal (the stored value is [id]), so reordering entries is safe.
 */
@JsonClass(generateAdapter = false) // see https://stackoverflow.com/a/64085370/421140
enum class ApiProvider(
    val id: Int, // safer ordinal; DON'T CHANGE!
    private val implementation: Api,
    /** When it publishes new rates: what auto-refresh follows (see [PublicationSchedule]). */
    val publication: PublicationSchedule,
    val needsApiKey: Boolean = false,
    val reliability: ProviderReliability = ProviderReliability.RELIABLE,
) {
    // European Central Bank reference rates, ~30 currencies, reliable.
    // ECB reference rates are published around 16:00 CET.
    FRANKFURTER_APP(ID_FRANKFURTER_APP, FrankfurterApp(), dailyAt(16, 0, "Europe/Berlin")),

    // ~44 currencies.
    // Official rates are set as of 15:30 Moscow time.
    BANK_ROSSII(ID_BANK_ROSSII, BankRossii(), dailyAt(15, 30, "Europe/Moscow")),

    // ~40 currencies, but regular HTTP 503 downtimes.
    // Published around 16:00 CET.
    NORGES_BANK(ID_NORGES_BANK, NorgesBank(), dailyAt(16, 0, "Europe/Oslo"), reliability = ProviderReliability.DOWNTIME),

    // Published by 16:30 ET.
    BANK_OF_CANADA(ID_BANK_OF_CANADA, BankOfCanada(), dailyAt(16, 30, "America/Toronto")),

    // Representative rates are published around 15:30 Israel time.
    BANK_OF_ISRAEL(ID_BANK_OF_ISRAEL, BankOfIsrael(), dailyAt(15, 30, "Asia/Jerusalem")),

    // ~150 currencies, but accounting rates set once a month, in force from
    // the 1st (they're the ECB's rates near the end of the month before).
    INFOR_EURO(
        ID_INFOR_EURO,
        InforEuro(),
        PublicationSchedule.Monthly(dayOfMonth = 1, at = LocalTime.of(6, 0), zone = ZoneId.of("Europe/Brussels")),
        reliability = ProviderReliability.INACCURATE_DATA,
    ),

    // Hourly.
    OPEN_EXCHANGERATES(ID_OPEN_EXCHANGERATES, OpenExchangerates(), PublicationSchedule.Frequent(UpdateCadence.HOURLY), needsApiKey = true),

    // ~160 currencies, mid-market, about every minute; the free plan is 300
    // requests in all.
    ALL_RATES_TODAY(ID_ALL_RATES_TODAY, AllRatesToday(), PublicationSchedule.Frequent(UpdateCadence.EVERY_MINUTE), needsApiKey = true),
    ;

    /** How often it publishes: the picker's order and its "Updates …" line. */
    val cadence: UpdateCadence get() = publication.cadence

    companion object {
        /** Every host a provider's requests may go to, the main and fallback providers alike. */
        val allHosts: Set<String> by lazy { entries.flatMapTo(mutableSetOf()) { it.implementation.hosts } }

        fun fromId(value: Int): ApiProvider =
            entries.firstOrNull { it.id == value }
                // this is our fallback, e.g. if an API is removed from the app
                ?: BANK_OF_ISRAEL

        /**
         * The picker's order: free providers before ones that need an API key,
         * each group from the most to the least frequently updated, then most
         * useful first (declaration order).
         */
        val pickerOrder: List<ApiProvider> =
            entries.sortedWith(compareBy<ApiProvider> { it.needsApiKey }.thenBy { it.cadence }.thenBy { it.ordinal })

        /**
         * The providers a picker lists: [pickerOrder], minus the unreliable
         * ones unless [showUnreliable] — though never minus one in [keep] (the
         * provider in use, say), so a choice never vanishes from its own list.
         */
        fun listed(
            showUnreliable: Boolean,
            keep: Set<ApiProvider> = emptySet(),
        ): List<ApiProvider> = pickerOrder.filter { showUnreliable || it.reliability.isReliable || it in keep }

        /**
         * The fallback when the user hasn't picked one, or picked what is now
         * the [main] provider: the first free, reliable provider in
         * [pickerOrder] that isn't [main].
         */
        fun defaultFallback(main: ApiProvider): ApiProvider =
            pickerOrder.first { !it.needsApiKey && it.reliability.isReliable && it != main }
    }

    fun getName(context: Context): CharSequence = context.getText(this.implementation.nameRes)

    fun getDescriptionShort(context: Context): CharSequence = this.implementation.descriptionShort(context)

    fun getDescriptionLong(context: Context): CharSequence = this.implementation.getDescriptionLong(context)

    fun getDescriptionUpdateInterval(context: Context): CharSequence = this.implementation.descriptionUpdateInterval(context)

    fun getHint(context: Context): CharSequence? = this.implementation.descriptionHint(context)

    // Host portion of [baseUrl], used to prewarm DNS at app start.
    fun getHost(): String? = hostOf(this.implementation.baseUrl)

    suspend fun getRates(
        context: Context?,
        date: LocalDate?,
        secrets: ApiSecrets = ApiSecrets.EMPTY,
    ): Result<ExchangeRates> = this.implementation.getRates(context, date, secrets)

    suspend fun getTimeline(
        context: Context?,
        base: Currency,
        symbol: Currency,
        startDate: LocalDate,
        endDate: LocalDate,
        secrets: ApiSecrets = ApiSecrets.EMPTY,
    ): Result<Timeline> = this.implementation.getTimeline(context, base, symbol, startDate, endDate, secrets)

    interface Api {
        // Stable English identifier used for log/error tags — never shown to
        // users; keep in ASCII so backend log grep stays predictable.
        val name: String

        // Localized display name shown in the UI (provider picker, share
        // footer, timeline attribution). Central-bank providers translate;
        // pure product brands (Frankfurter.app, …) fall back to the
        // base-locale string.
        @get:StringRes
        val nameRes: Int

        fun descriptionShort(context: Context): CharSequence

        fun getDescriptionLong(context: Context): CharSequence

        fun descriptionUpdateInterval(context: Context): CharSequence

        fun descriptionHint(context: Context): CharSequence?

        val baseUrl: String

        /** Every host its requests go to: [baseUrl]'s, and any other a feed of its lives on. */
        val hosts: Set<String> get() = setOfNotNull(hostOf(baseUrl))

        suspend fun getRates(
            context: Context?,
            date: LocalDate?,
            secrets: ApiSecrets,
        ): Result<ExchangeRates>

        suspend fun getTimeline(
            context: Context?,
            base: Currency,
            symbol: Currency,
            startDate: LocalDate,
            endDate: LocalDate,
            secrets: ApiSecrets = ApiSecrets.EMPTY,
        ): Result<Timeline>
    }
}

/** The host of [url], or null when it has none. */
internal fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()
