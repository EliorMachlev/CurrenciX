package com.eliormachlev.currencix.model.provider

import android.app.Application
import com.eliormachlev.currencix.R
import com.eliormachlev.currencix.model.ApiProvider
import com.eliormachlev.currencix.model.ApiSecrets
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.rateFor
import com.eliormachlev.currencix.util.ApiHttpError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate

private const val KEY = "art_live_test"
private val SECRETS = ApiSecrets(allRatesTodayApiKey = KEY)
private val START: LocalDate = LocalDate.of(2024, 1, 1)
private val END: LocalDate = LocalDate.of(2024, 3, 31)

/**
 * AllRatesToday, built from its published API spec: what goes over the wire
 * (the key only ever in the Authorization header) and what the app makes of
 * its answers. Robolectric for the error messages, which are app strings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AllRatesTodayTest {
    @get:Rule val http = StubHttpRule()

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `without a key nothing is requested`() =
        runBlocking {
            val result = AllRatesToday().getRates(null, null, ApiSecrets.EMPTY)
            assertTrue(http.requests.isEmpty())
            assertTrue(result.isFailure)
        }

    @Test
    fun `latest rates ask for every rate against the euro, the key as a bearer token`() =
        runBlocking {
            AllRatesToday().getRates(null, null, SECRETS)
            assertEquals(listOf("https://allratestoday.com/api/v1/rates?source=EUR"), http.requests.map { it.toString() })
            assertEquals(listOf("Bearer $KEY"), http.authorizations)
        }

    @Test
    fun `a past day's rates are its rates at noon UTC`() =
        runBlocking {
            AllRatesToday().getRates(null, LocalDate.of(2024, 3, 15), SECRETS)
            assertEquals("2024-03-15T12:00:00Z", http.requests.single().queryParameter("time"))
        }

    @Test
    fun `the timeline asks for one pair by day, through the end date, FOK as DKK`() =
        runBlocking {
            AllRatesToday().getTimeline(null, Currency.USD, Currency.FOK, START, END, SECRETS)
            assertEquals(
                "https://allratestoday.com/api/v1/rates?group=day&source=USD&target=DKK&from=2024-01-01&to=2024-04-01",
                http.requests.single().toString(),
            )
            assertEquals("Bearer $KEY", http.authorizations.single())
        }

    @Test
    fun `rates decode every quote, add the base, and are dated by the newest quote`() =
        runBlocking {
            http.body =
                """[{"rate":1.0892,"source":"EUR","target":"USD","time":"2024-03-15T12:00:00+0000"},""" +
                """{"rate":7.4571,"source":"EUR","target":"DKK","time":"2024-03-15T12:01:00Z"},""" +
                """{"rate":1,"source":"EUR","target":"XXQ","time":"2024-03-15T12:01:00Z"}]"""
            val rates = AllRatesToday().getRates(null, null, SECRETS).getOrThrow()

            assertEquals(ApiProvider.ALL_RATES_TODAY, rates.provider)
            assertEquals(Currency.EUR, rates.base)
            assertEquals(BigDecimal("1.0892"), rates.rateFor(Currency.USD)?.value)
            assertEquals("the base is added at 1", BigDecimal.ONE, rates.rateFor(Currency.EUR)?.value)
            assertEquals("FOK rides on DKK", BigDecimal("7.4571"), rates.rateFor(Currency.FOK)?.value)
            assertEquals("an unknown currency is skipped", 4, rates.rates?.size)
            assertTrue(rates.date in LocalDate.of(2024, 3, 14)..LocalDate.of(2024, 3, 16))
        }

    @Test
    fun `the timeline keeps one rate per day, the day's last, labelled as asked`() =
        runBlocking {
            http.body =
                """[{"rate":7.40,"source":"USD","target":"DKK","time":"2024-01-02T09:00:00Z"},""" +
                """{"rate":7.41,"source":"USD","target":"DKK","time":"2024-01-02T17:00:00Z"},""" +
                """{"rate":7.45,"source":"USD","target":"DKK","time":"2024-01-03"}]"""
            val timeline = AllRatesToday().getTimeline(null, Currency.USD, Currency.FOK, START, END, SECRETS).getOrThrow()

            assertEquals(
                mapOf(
                    LocalDate.of(2024, 1, 2) to Rate(Currency.FOK, BigDecimal("7.41")),
                    LocalDate.of(2024, 1, 3) to Rate(Currency.FOK, BigDecimal("7.45")),
                ),
                timeline.rates,
            )
            assertEquals(LocalDate.of(2024, 1, 2), timeline.startDate)
            assertEquals(LocalDate.of(2024, 1, 3), timeline.endDate)
        }

    @Test
    fun `a wrong key, a plan without this, and a spent quota each say so`() {
        mapOf(
            401 to R.string.error_invalid_api_key,
            403 to R.string.error_api_plan_required,
            429 to R.string.error_api_quota_exceeded,
        ).forEach { (status, message) ->
            val result = Result.failure<Unit>(ApiHttpError(status)).explainKeyErrors(context)
            assertEquals("HTTP $status", context.getString(message), result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun `any other failure is left as it is`() {
        val error = ApiHttpError(503)
        assertEquals(error, Result.failure<Unit>(error).explainKeyErrors(context).exceptionOrNull())
    }

    @Test
    fun `the key's status reaches the user as words, not a bare HTTP code`() =
        runBlocking {
            http.status = 429
            val error = AllRatesToday().getRates(null, null, SECRETS).exceptionOrNull()
            assertTrue("$error", error != null && error !is ApiHttpError)
        }
}
