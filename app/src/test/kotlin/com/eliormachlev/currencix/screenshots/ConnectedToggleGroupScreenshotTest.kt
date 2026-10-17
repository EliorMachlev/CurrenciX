package com.eliormachlev.currencix.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.eliormachlev.currencix.view.compose.ConnectedToggleGroup
import com.eliormachlev.currencix.view.compose.theme.Spacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The connected buttons with each end picked: a picked button keeps its
// shape, the outer side round on the ends — (=| on the left, |=) on the right.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.PIXEL_5, application = Application::class)
class ConnectedToggleGroupScreenshotTest {
    @get:Rule val shots = ScreenshotRule()

    @Test fun endsPicked() =
        shots.captureMatrix("connected_toggle_group_ends") {
            Column(Modifier.padding(Spacing.Large), verticalArrangement = Arrangement.spacedBy(Spacing.Large)) {
                listOf(OPTIONS.first(), OPTIONS[1], OPTIONS.last()).forEach { picked ->
                    ConnectedToggleGroup(
                        options = OPTIONS,
                        selected = picked,
                        label = { it },
                        onToggle = { _, _ -> },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

    private companion object {
        val OPTIONS = listOf("Week", "Month", "Year", "5 years")
    }
}
