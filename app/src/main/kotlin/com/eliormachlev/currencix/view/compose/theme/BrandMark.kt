package com.eliormachlev.currencix.view.compose.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import com.eliormachlev.currencix.BuildConfig
import com.eliormachlev.currencix.R
import kotlinx.coroutines.flow.first

// The mark's paths are in the 108-unit adaptive-icon canvas, centred on
// CENTRE and reaching SAFE_RADIUS from it (art/brand/generate.py): drawn
// here, that circle fills the composable.
private const val CENTRE = 54f
private const val SAFE_RADIUS = 32f

// Darker than this, the background is ink and the arrows take the brand tones.
private const val INK_LUMINANCE = 0.5f

private const val HALF_TURN = 180f
private const val FULL_TURN = 360f

/**
 * The app's mark, as on the launcher icon and the splash screen: two arrows
 * swapping around a "×", drawn from the same paths (values/brand_mark.xml).
 * [rotation] turns the arrows, in degrees; the × stays still. The arrows
 * are green over brass, in the tones that read on the current background;
 * the × is [crossColor], or red in a debug build, as on its launcher icon.
 */
@Composable
fun BrandMark(
    rotation: () -> Float,
    modifier: Modifier = Modifier,
    crossColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    val paths = rememberMarkPaths()
    val onInk = MaterialTheme.colorScheme.background.luminance() < INK_LUMINANCE
    val green = if (onInk) BillGreen else BrandGreenOnPaper
    val brass = if (onInk) Brass else BrandBrassOnPaper
    val cross = if (BuildConfig.DEBUG) colorResource(R.color.debugMarkCross) else crossColor
    Canvas(modifier) {
        val scale = size.minDimension / (2 * SAFE_RADIUS)
        withTransform({
            scale(scale, scale, pivot = Offset.Zero)
            translate(SAFE_RADIUS - CENTRE, SAFE_RADIUS - CENTRE)
        }) {
            rotate(rotation(), pivot = Offset(CENTRE, CENTRE)) {
                drawArrow(paths.top, green, paths.rounding)
                drawArrow(paths.bottom, brass, paths.rounding)
            }
            drawPath(paths.cross, cross, style = Stroke(width = paths.crossWidth, cap = StrokeCap.Round))
        }
    }
}

// Filled, with a thin outline in the same colour that rounds the arrowheads' corners.
private fun DrawScope.drawArrow(
    path: Path,
    color: Color,
    rounding: Float,
) {
    drawPath(path, color)
    drawPath(path, color, style = Stroke(width = rounding, join = StrokeJoin.Round))
}

private class MarkPaths(
    val top: Path,
    val bottom: Path,
    val cross: Path,
    val rounding: Float,
    val crossWidth: Float,
)

@Composable
private fun rememberMarkPaths(): MarkPaths {
    val resources = LocalResources.current
    val top = stringResource(R.string.brand_mark_arrow_top)
    val bottom = stringResource(R.string.brand_mark_arrow_bottom)
    val cross = stringResource(R.string.brand_mark_cross)
    return remember(resources, top, bottom, cross) {
        MarkPaths(
            top = top.toPath(),
            bottom = bottom.toPath(),
            cross = cross.toPath(),
            rounding = resources.getFloat(R.dimen.brand_mark_arrow_round),
            crossWidth = resources.getFloat(R.dimen.brand_mark_cross_width),
        )
    }
}

private fun String.toPath(): Path = PathParser().parsePathString(this).toPath()

/**
 * Turns a [BrandMark]'s arrows half a turn at a time, so green and brass
 * swap places, and always brings them to rest the way the icon has them,
 * green on top. Starts a half turn back when [launch] is set, to turn into
 * place as the app opens.
 */
@Stable
class MarkTurns(
    launch: Boolean,
) {
    private val angle = Animatable(if (launch) -HALF_TURN else 0f)
    private var requested by mutableIntStateOf(0)

    /** The arrows' angle, in degrees, for [BrandMark]. */
    val rotation: Float get() = angle.value

    /** One more turn (it ends green on top, so a full turn from rest). */
    fun turnOnce() {
        requested++
    }

    /**
     * Runs the turns for as long as it's called from (a LaunchedEffect):
     * the launch turn, then half turns whenever [turning] is true or a turn
     * was asked for, until neither holds and the arrows are back at rest.
     */
    suspend fun run(turning: () -> Boolean) {
        if (angle.value != 0f) angle.animateTo(0f, Motion.turn())
        var handled = requested
        while (true) {
            snapshotFlow { turning() || requested != handled }.first { it }
            do {
                handled = requested
                angle.animateTo(angle.value + HALF_TURN, Motion.turn())
            } while (turning() || requested != handled || angle.value % FULL_TURN != 0f)
            angle.snapTo(0f)
        }
    }
}
