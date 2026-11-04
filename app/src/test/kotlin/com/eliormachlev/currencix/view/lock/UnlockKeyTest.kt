package com.eliormachlev.currencix.view.lock

import org.junit.Assert.assertFalse
import org.junit.Test
import javax.crypto.Mac

// A passed prompt unlocks only when its key then signs: a result without
// one, or with a MAC the Keystore never authorized, proves nothing.
class UnlockKeyTest {
    @Test
    fun `no MAC, or one that can't sign, doesn't unlock`() {
        assertFalse(UnlockKey.proves(null))
        assertFalse(UnlockKey.proves(Mac.getInstance("HmacSHA256")))
    }
}
