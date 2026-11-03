package com.eliormachlev.currencix.view.lock

import org.junit.Assert.assertFalse
import org.junit.Test
import javax.crypto.Cipher

// A passed prompt unlocks only when its key then encrypts: a result without
// one, or with a cipher the Keystore never authorized, proves nothing.
class UnlockKeyTest {
    @Test
    fun `no cipher, or one that can't encrypt, doesn't unlock`() {
        assertFalse(UnlockKey.proves(null))
        assertFalse(UnlockKey.proves(Cipher.getInstance("AES/GCM/NoPadding")))
    }
}
