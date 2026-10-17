package com.eliormachlev.currencix.screenshots

import android.app.Application
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.eliormachlev.currencix.R
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// The mark in each place it's drawn, as the platform draws it (no Compose):
// the splash icon on its background, the launcher icon through a circle
// mask, and the themed icon in a wallpaper colour. Light, then dark mode.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class BrandMarkScreenshotTest {
    @Test fun light() = capture("brand_mark_light", night = false)

    @Test fun dark() = capture("brand_mark_dark", night = true)

    // The splash icon's turn must inflate and start: Android throws when
    // its target isn't a group of brand_mark. (Robolectric doesn't play
    // vector animations, so their frames aren't checked here.)
    @Test fun splashIconInflatesAndStarts() {
        val icon = RuntimeEnvironment.getApplication().getDrawable(R.drawable.brand_mark_animated)
        assertTrue(icon is AnimatedVectorDrawable)
        (icon as AnimatedVectorDrawable).start()
    }

    private fun capture(
        name: String,
        night: Boolean,
    ) {
        RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
        val context = RuntimeEnvironment.getApplication()
        val launcher = context.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val (themedBackground, themedMark) = if (night) THEMED_DARK else THEMED_LIGHT
        val themed =
            AdaptiveIconDrawable(
                ColorDrawable(themedBackground),
                launcher.monochrome?.mutate()?.apply { setTint(themedMark) },
            )
        val splashBackground = context.getColor(if (night) R.color.inkBg else R.color.paperBg)

        val bitmap = createBitmap(CELL * CELLS, CELL)
        val canvas = Canvas(bitmap)
        canvas.drawColor(splashBackground)
        listOf(context.getDrawable(R.drawable.brand_mark), launcher, themed).forEachIndexed { index, drawable ->
            canvas.withTranslation(x = (index * CELL).toFloat()) { drawable?.drawIn(this) }
        }
        bitmap.captureRoboImage("$SCREENSHOT_DIR/$name.png")
    }

    private fun Drawable.drawIn(canvas: Canvas) {
        setBounds(0, 0, CELL, CELL)
        draw(canvas)
    }

    private companion object {
        const val CELL = 432
        const val CELLS = 3

        // A blue wallpaper's themed-icon colours (background to mark).
        val THEMED_LIGHT = argb(0xFFD6E3FF) to argb(0xFF0E3A6E)
        val THEMED_DARK = argb(0xFF1F2F45) to argb(0xFFAFC8F2)

        @ColorInt fun argb(value: Long): Int = value.toInt()
    }
}
