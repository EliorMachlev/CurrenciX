package com.eliormachlev.currencix.view.navigation

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoPaneRoomTest {
    @Test
    fun `a tablet or unfolded foldable has room for two panes`() {
        assertTrue(hasRoomForTwoPanes(width = 900.dp, height = 700.dp))
        assertTrue(hasRoomForTwoPanes(width = 840.dp, height = 480.dp))
    }

    @Test
    fun `a phone portrait or landscape keeps one pane`() {
        assertFalse(hasRoomForTwoPanes(width = 400.dp, height = 850.dp))
        assertFalse(hasRoomForTwoPanes(width = 880.dp, height = 400.dp))
    }
}
