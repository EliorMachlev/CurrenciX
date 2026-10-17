package com.eliormachlev.currencix.view.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/**
 * A snapshot of whatever [captureInto] is applied to, taken on demand — the
 * image in "share" (the converter's hero card, the timeline chart). No
 * offscreen composition: the element's own drawing is recorded as it's shown.
 */
class LayerCapture {
    // Hold the layer itself (not a closure) so recomposition never clears
    // it — an old-but-valid layer beats null, which would force the caller
    // into a text-only fallback.
    @Volatile
    internal var graphicsLayer: GraphicsLayer? = null

    /** The last drawn frame; null before the element has been drawn. */
    suspend fun capture(): ImageBitmap? = graphicsLayer?.toImageBitmap()
}

/**
 * Routes this element's drawing through a layer [capture] can snapshot.
 * Recording on every draw means a capture reflects the frame on screen.
 * A no-op modifier when [capture] is null.
 */
@Composable
fun Modifier.captureInto(capture: LayerCapture?): Modifier {
    if (capture == null) return this
    val layer = rememberGraphicsLayer()
    // SideEffect (not DisposableEffect): republished on every commit and
    // never nulled, so a share tap while the element briefly leaves
    // composition still finds a usable layer.
    SideEffect { capture.graphicsLayer = layer }
    return drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }
}
