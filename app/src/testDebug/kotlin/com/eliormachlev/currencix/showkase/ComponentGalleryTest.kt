package com.eliormachlev.currencix.showkase

import android.app.Activity
import android.app.Application
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.airbnb.android.showkase.models.Showkase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// The debug-only component gallery opens and draws its browser: it once
// crashed on the first icon of its app bar, because material-icons-core
// wasn't on the classpath.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ComponentGalleryTest {
    @Test
    fun galleryOpensWithoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        ActivityScenario.launch<Activity>(Showkase.getBrowserIntent(context)).use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onActivity { activity -> assertEquals(false, activity.isFinishing) }
        }
    }
}
