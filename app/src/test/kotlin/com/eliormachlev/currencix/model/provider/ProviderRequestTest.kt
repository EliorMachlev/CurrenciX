package com.eliormachlev.currencix.model.provider

import com.eliormachlev.currencix.model.ApiSecrets
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.util.ApiHttpError
import com.eliormachlev.currencix.util.HttpClientProvider
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Modifier
import java.time.LocalDate

private const val HTTP_OK = 200
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_UNAVAILABLE = 503

private const val API_KEY = "test-key"
private val DATE: LocalDate = LocalDate.of(2024, 3, 15)
private val START: LocalDate = LocalDate.of(2024, 1, 1)
private val END: LocalDate = LocalDate.of(2024, 3, 31)

/**
 * Pins the exact request every provider sends, and the error contract its
 * transport applies — independent of whether that transport is Retrofit or
 * raw OkHttp. Both go through [HttpClientProvider.client], so swapping its
 * context-less client for one that records requests and returns a stub
 * response exercises the real provider code end to end, with no network.
 *
 * The expected URLs are the ones the raw-OkHttp implementations built before
 * the Retrofit migration, so this suite passes against both versions: a
 * provider that moves transport must not change what goes over the wire
 * (which would also cold-start its HTTP cache entries).
 */
class ProviderRequestTest {
    private val requests = mutableListOf<HttpUrl>()
    private var stubStatus = HTTP_OK

    // A JSON `null` body. Adapters that treat a non-object payload as "no data"
    // (Frankfurter, Open Exchange Rates, Bank of Israel) decode it to null,
    // which must surface as "<provider>: empty JSON". The Bank of Canada and
    // InforEuro adapters reject it with a JsonDataException instead — the same
    // on either transport, so those tests only pin the request.
    private var stubBody = "null"

    private val recordingClient =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                requests += chain.request().url
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(stubStatus)
                    .message("stub")
                    .body(stubBody.toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()

    // HttpClientProvider has no injection seam, and adding one to production
    // code just for this test isn't worth it — set its context-less client
    // slot directly. Fails loudly if the field is renamed.
    private val uncachedClientField =
        HttpClientProvider::class.java.getDeclaredField("uncachedInstance").apply { isAccessible = true }
    private val fieldOwner: Any? =
        if (Modifier.isStatic(uncachedClientField.modifiers)) null else HttpClientProvider

    @Before
    fun installRecordingClient() = uncachedClientField.set(fieldOwner, recordingClient)

    @After
    fun restoreClient() = uncachedClientField.set(fieldOwner, null)

    private fun assertRequested(vararg expected: String) = assertEquals(expected.toList(), requests.map { it.toString() })

    private fun assertEmptyJson(
        result: Result<*>,
        providerName: String,
    ) {
        val error = result.exceptionOrNull()
        assertTrue("expected IOException, got $error", error is IOException && error !is ApiHttpError)
        assertEquals("$providerName: empty JSON", error?.message)
    }

    // --- Frankfurter (Retrofit) ------------------------------------------------

    @Test
    fun `frankfurter latest rates`() =
        runBlocking {
            val provider = FrankfurterApp()
            val result = provider.getRates(null, null, ApiSecrets.EMPTY)
            assertRequested("https://api.frankfurter.dev/v1/latest?base=EUR")
            assertEmptyJson(result, provider.name)
        }

    @Test
    fun `frankfurter timeline`() =
        runBlocking {
            FrankfurterApp().getTimeline(null, Currency.EUR, Currency.USD, START, END)
            assertRequested("https://api.frankfurter.dev/v1/2024-01-01..2024-03-31?base=EUR&symbols=USD")
        }

    // --- Open Exchange Rates (Retrofit) ------------------------------------------

    @Test
    fun `openexchangerates latest rates`() =
        runBlocking {
            val provider = OpenExchangerates()
            val result = provider.getRates(null, null, ApiSecrets(API_KEY))
            assertRequested(
                "https://openexchangerates.org/api/latest.json?app_id=$API_KEY&prettyprint=false&show_alternative=false",
            )
            assertEmptyJson(result, provider.name)
        }

    @Test
    fun `openexchangerates historical rates`() =
        runBlocking {
            OpenExchangerates().getRates(null, DATE, ApiSecrets(API_KEY))
            assertRequested(
                "https://openexchangerates.org/api/historical/2024-03-15.json" +
                    "?app_id=$API_KEY&prettyprint=false&show_alternative=false",
            )
        }

    @Test
    fun `openexchangerates maps 401 to the invalid-key error, not a raw HTTP error`() =
        runBlocking {
            stubStatus = HTTP_UNAUTHORIZED
            val result = OpenExchangerates().getRates(null, null, ApiSecrets(API_KEY))
            assertTrue(result.isFailure)
            assertFalse(result.exceptionOrNull() is ApiHttpError)
        }

    @Test
    fun `openexchangerates without a key never hits the network`() =
        runBlocking {
            val result = OpenExchangerates().getRates(null, null, ApiSecrets.EMPTY)
            assertTrue(result.isFailure)
            assertRequested()
        }

    // --- InforEuro (Retrofit) ------------------------------------------------------

    @Test
    fun `inforeuro latest rates`() =
        runBlocking {
            InforEuro().getRates(null, null, ApiSecrets.EMPTY)
            assertRequested("https://ec.europa.eu/budg/inforeuro/api/public/monthly-rates")
        }

    @Test
    fun `inforeuro historical rates`() =
        runBlocking {
            InforEuro().getRates(null, DATE, ApiSecrets.EMPTY)
            assertRequested("https://ec.europa.eu/budg/inforeuro/api/public/monthly-rates?year=2024&month=3")
        }

    @Test
    fun `inforeuro timeline fetches both legs, FOK as DKK`() =
        runBlocking {
            InforEuro().getTimeline(null, Currency.FOK, Currency.GBP, START, END)
            assertRequested(
                "https://ec.europa.eu/budg/inforeuro/api/public/currencies/DKK",
                "https://ec.europa.eu/budg/inforeuro/api/public/currencies/GBP",
            )
        }

    // --- Bank of Canada (Retrofit) -------------------------------------------------

    @Test
    fun `bank of canada latest rates`() =
        runBlocking {
            BankOfCanada().getRates(null, null, ApiSecrets.EMPTY)
            assertRequested(
                "https://www.bankofcanada.ca/valet/observations/group/FX_RATES_DAILY_CURRENT/json?recent=1&order_dir=desc",
            )
        }

    @Test
    fun `bank of canada historical rates use the lookback window`() =
        runBlocking {
            BankOfCanada().getRates(null, DATE, ApiSecrets.EMPTY)
            assertRequested(
                "https://www.bankofcanada.ca/valet/observations/group/FX_RATES_DAILY_CURRENT/json" +
                    "?start_date=2024-03-08&end_date=2024-03-15&order_dir=desc",
            )
        }

    @Test
    fun `bank of canada timeline, FOK as DKK`() =
        runBlocking {
            BankOfCanada().getTimeline(null, Currency.USD, Currency.FOK, START, END)
            assertRequested(
                "https://www.bankofcanada.ca/valet/observations/FXUSDCAD,FXDKKCAD/json" +
                    "?start_date=2024-01-01&end_date=2024-03-31&order_dir=asc",
            )
        }

    @Test
    fun `a non-object body is a labelled empty-JSON error`() =
        runBlocking {
            // Any non-object top-level value, not just `null`, must be consumed
            // by the adapter so the transport sees a complete document.
            stubBody = "[]"
            val provider = OpenExchangerates()
            assertEmptyJson(provider.getRates(null, null, ApiSecrets(API_KEY)), provider.name)
        }

    @Test
    fun `non-2xx surfaces as ApiHttpError with the status code`() =
        runBlocking {
            stubStatus = HTTP_UNAVAILABLE
            val error = BankOfCanada().getRates(null, null, ApiSecrets.EMPTY).exceptionOrNull()
            assertTrue("expected ApiHttpError, got $error", error is ApiHttpError)
            assertEquals(HTTP_UNAVAILABLE, (error as ApiHttpError).statusCode)
        }

    // --- Bank of Israel (Retrofit for PublicApi, raw OkHttp for SDMX) -------------

    @Test
    fun `bank of israel latest rates`() =
        runBlocking {
            val provider = BankOfIsrael()
            val result = provider.getRates(null, null, ApiSecrets.EMPTY)
            assertRequested("https://boi.org.il/PublicApi/GetExchangeRates")
            assertEmptyJson(result, provider.name)
        }

    @Test
    fun `bank of israel historical rates stay on the SDMX feed`() =
        runBlocking {
            BankOfIsrael().getRates(null, DATE, ApiSecrets.EMPTY)
            assertRequested(
                "https://edge.boi.gov.il/FusionEdgeServer/sdmx/v2/data/dataflow/BOI.STATISTICS/EXR/1.0" +
                    "?startPeriod=2024-03-15&endPeriod=2024-03-15&format=sdmx-json",
            )
        }
}
