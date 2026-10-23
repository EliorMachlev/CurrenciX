package com.eliormachlev.currencix.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

// A provider that failed is passed over for ten minutes, or until it answers.
class ProviderCooldownTest {
    private var now = 0L
    private val cooldown = ProviderCooldown { now }

    @Test
    fun `a failed provider cools down for ten minutes`() {
        cooldown.recordFailure(PROVIDER)
        now += TimeUnit.MINUTES.toMillis(9)
        assertTrue(cooldown.isCoolingDown(PROVIDER))
        now += TimeUnit.MINUTES.toMillis(1)
        assertFalse(cooldown.isCoolingDown(PROVIDER))
    }

    @Test
    fun `an answer ends the cooldown at once`() {
        cooldown.recordFailure(PROVIDER)
        cooldown.recordSuccess(PROVIDER)
        assertFalse(cooldown.isCoolingDown(PROVIDER))
    }

    @Test
    fun `a provider that never failed isn't cooling down`() = assertFalse(cooldown.isCoolingDown(PROVIDER))

    private companion object {
        const val PROVIDER = 7
    }
}
