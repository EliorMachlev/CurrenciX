package com.eliormachlev.currencix.viewmodel.timeline

import android.app.Application
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.test.core.app.ApplicationProvider
import com.eliormachlev.currencix.model.Currency
import com.eliormachlev.currencix.model.Rate
import com.eliormachlev.currencix.model.Timeline
import com.eliormachlev.currencix.repository.Database
import com.eliormachlev.currencix.util.AppDispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.Executor

// A custom range holding none of the cached rates (a weekend, or dates before
// the provider's history) used to crash on `first()` of the empty set.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TimelineEmptyRangeTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val today = LocalDate.now()

    @Before
    fun seedCache() {
        val db = Database(app)
        val rates = (0L until CACHED_DAYS).associate { today.minusDays(it) to Rate(Currency.EUR, BigDecimal("0.9")) }
        db.rates.putCachedTimeline(
            Timeline(true, null, "USD", rates.keys.min(), rates.keys.max(), rates.toSortedMap(), db.providers.apiProvider.get()),
            Currency.USD,
            Currency.EUR,
        )
    }

    @Test
    fun `a range without rates leaves the readouts empty instead of crashing`() {
        // The readouts are worked out off the main thread in the app; here on
        // the calling thread, at once, so the assertions see them.
        val atOnce = Executor { it.run() }.asCoroutineDispatcher()
        val model = TimelineViewModel(app, Currency.USD, Currency.EUR, AppDispatchers(io = atOnce, default = atOnce) { atOnce })
        val past = model.getRatePast().observed()
        val current = model.getRateCurrent().observed()
        val diff = model.getRatesDifferencePercent().observed()
        idle()

        val emptyStart = today.minusYears(3)
        model.setCustomRange(emptyStart, emptyStart.plusDays(1))
        idle()

        assertEquals(TimelineViewModel.Period.CUSTOM, model.getPeriod().value)
        assertNull(past.value?.first)
        assertNull(current.value?.first)
        assertNull(diff.value)
    }

    private fun <T> LiveData<T>.observed(): LiveData<T> = also { it.observeForever {} }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private companion object {
        const val CACHED_DAYS = 30L
    }
}
