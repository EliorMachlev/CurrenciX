package com.eliormachlev.currencix.model

import android.content.Context
import androidx.annotation.StringRes
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

private const val ID_FRANKFURTER_APP = 1
private const val ID_INFOR_EURO = 3
private const val ID_NORGES_BANK = 4
private const val ID_BANK_ROSSII = 5
private const val ID_BANK_OF_CANADA = 6
private const val ID_OPEN_EXCHANGERATES = 7
private const val ID_BANK_OF_ISRAEL = 8

/** How often a provider publishes new rates — the picker lists the more frequent ones first. */
enum class UpdateCadence { HOURLY, DAILY, MONTHLY }

/**
 * Declaration order is the picker's order within a group ([pickerOrder]):
 * most useful first — wider coverage and steadier service. Nothing persists
 * the ordinal (the stored value is [id]), so reordering entries is safe.
 */
@JsonClass(generateAdapter = false) // see https://stackoverflow.com/a/64085370/421140
enum class ApiProvider(
    val id: Int, // safer ordinal; DON'T CHANGE!
    private val implementation: Api,
    val cadence: UpdateCadence,
    val needsApiKey: Boolean = false,
) {
    // EXCHANGERATE_HOST(0, "https://api.exchangerate.host"), // removed, as API was shut down
    // FER_EE(2, FerEe()), // deactivated: API returns HTTP 422 most of the time with no response
    //   from developers — see https://github.com/narorolib/fer/issues/6

    // European Central Bank reference rates, ~30 currencies, reliable.
    FRANKFURTER_APP(ID_FRANKFURTER_APP, FrankfurterApp(), UpdateCadence.DAILY),

    // ~44 currencies.
    BANK_ROSSII(ID_BANK_ROSSII, BankRossii(), UpdateCadence.DAILY),

    // ~40 currencies, but regular HTTP 503 downtimes.
    NORGES_BANK(ID_NORGES_BANK, NorgesBank(), UpdateCadence.DAILY),
    BANK_OF_CANADA(ID_BANK_OF_CANADA, BankOfCanada(), UpdateCadence.DAILY),
    BANK_OF_ISRAEL(ID_BANK_OF_ISRAEL, BankOfIsrael(), UpdateCadence.DAILY),

    // ~150 currencies, but accounting rates set once a month.
    INFOR_EURO(ID_INFOR_EURO, InforEuro(), UpdateCadence.MONTHLY),
    OPEN_EXCHANGERATES(ID_OPEN_EXCHANGERATES, OpenExchangerates(), UpdateCadence.HOURLY, needsApiKey = true),
    ;

    companion object {
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
         * The fallback when the user hasn't picked one, or picked what is now
         * the [main] provider: the first free provider in [pickerOrder] that
         * isn't [main].
         */
        fun defaultFallback(main: ApiProvider): ApiProvider = pickerOrder.first { !it.needsApiKey && it != main }
    }

    fun getName(context: Context): CharSequence = context.getText(this.implementation.nameRes)

    fun getDescriptionShort(context: Context): CharSequence = this.implementation.descriptionShort(context)

    fun getDescriptionLong(context: Context): CharSequence = this.implementation.getDescriptionLong(context)

    fun getDescriptionUpdateInterval(context: Context): CharSequence = this.implementation.descriptionUpdateInterval(context)

    fun getHint(context: Context): CharSequence? = this.implementation.descriptionHint(context)

    // Host portion of [baseUrl], used to prewarm DNS at app start.
    fun getHost(): String? = runCatching { URI(this.implementation.baseUrl).host }.getOrNull()

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
    ): Result<Timeline> = this.implementation.getTimeline(context, base, symbol, startDate, endDate)

    interface Api {
        // Stable English identifier used for log/error tags — never shown to
        // users; keep in ASCII so backend log grep stays predictable.
        val name: String

        // Localized display name shown in the UI (provider picker, share
        // footer, timeline attribution). Central-bank providers translate;
        // pure product brands (Frankfurter.app, Fer.ee, …) fall back to the
        // base-locale string.
        @get:StringRes
        val nameRes: Int

        fun descriptionShort(context: Context): CharSequence

        fun getDescriptionLong(context: Context): CharSequence

        fun descriptionUpdateInterval(context: Context): CharSequence

        fun descriptionHint(context: Context): CharSequence?

        val baseUrl: String

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
        ): Result<Timeline>
    }
}
