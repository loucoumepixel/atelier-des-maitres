package app.atelier.maitres.ui.studio

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/**
 * Cadrage de la toile à l'écran : `écran = origin + image × scale`.
 *
 * Le zoom suit le point entre les doigts, la toile ne peut pas sortir du cadre,
 * et les déplacements automatiques (« trouver une zone », vue d'ensemble) sont animés
 * en interpolant l'échelle de façon géométrique, ce qui donne un zoom régulier à l'œil.
 */
@Stable
class CanvasCamera(private val imageWidth: Int, private val imageHeight: Int) {

    var viewSize by mutableStateOf(IntSize.Zero)
        private set
    var scale by mutableFloatStateOf(1f)
        private set
    var origin by mutableStateOf(Offset.Zero)
        private set

    val fitScale: Float
        get() = if (viewSize == IntSize.Zero) 1f
        else min(viewSize.width.toFloat() / imageWidth, viewSize.height.toFloat() / imageHeight) * FIT_MARGIN

    val maxScale: Float get() = fitScale * MAX_ZOOM

    /** Zoom relatif à la vue d'ensemble (1 = toile entière). */
    val zoom: Float get() = scale / fitScale

    fun onViewSize(size: IntSize) {
        if (size == viewSize || size.width == 0 || size.height == 0) return
        val first = viewSize == IntSize.Zero
        val relative = zoom
        val center = if (first) null else toImage(Offset(viewSize.width / 2f, viewSize.height / 2f))
        viewSize = size
        if (center == null) fit() else place(fitScale * relative, center)
    }

    fun fit() = place(fitScale, Offset(imageWidth / 2f, imageHeight / 2f))

    fun toImage(screen: Offset): Offset = (screen - origin) / scale

    fun toScreen(image: Offset): Offset = origin + image * scale

    /** Pincement / glissement à deux doigts. */
    fun transform(centroid: Offset, pan: Offset, zoomChange: Float) {
        val newScale = (scale * zoomChange).coerceIn(fitScale, maxScale)
        origin = clampOrigin(centroid - (centroid - origin) * (newScale / scale) + pan, newScale)
        scale = newScale
    }

    /** Anime la caméra pour centrer [imagePoint] au zoom relatif [targetZoom]. */
    suspend fun animateTo(imagePoint: Offset, targetZoom: Float) {
        val startScale = scale
        val endScale = (fitScale * targetZoom).coerceIn(fitScale, maxScale)
        val viewCenter = Offset(viewSize.width / 2f, viewSize.height / 2f)
        val startCenter = toImage(viewCenter)
        // On vise le point demandé, mais corrigé pour que la toile reste dans le cadre.
        val endOrigin = clampOrigin(viewCenter - imagePoint * endScale, endScale)
        val endCenter = (viewCenter - endOrigin) / endScale
        val logStart = ln(startScale)
        val logEnd = ln(endScale)
        animate(0f, 1f, animationSpec = tween(520, easing = FastOutSlowInEasing)) { t, _ ->
            val s = exp(logStart + (logEnd - logStart) * t)
            val c = startCenter + (endCenter - startCenter) * t
            scale = s
            origin = viewCenter - c * s
        }
        scale = endScale
        origin = endOrigin
    }

    suspend fun animateToOverview() = animateTo(Offset(imageWidth / 2f, imageHeight / 2f), 1f)

    private fun place(newScale: Float, imageCenter: Offset) {
        scale = newScale
        origin = clampOrigin(Offset(viewSize.width / 2f, viewSize.height / 2f) - imageCenter * newScale, newScale)
    }

    private fun clampOrigin(o: Offset, s: Float): Offset {
        val w = imageWidth * s
        val h = imageHeight * s
        val vw = viewSize.width.toFloat()
        val vh = viewSize.height.toFloat()
        val x = if (w <= vw) (vw - w) / 2f else o.x.coerceIn(vw - w - EDGE_SLACK, EDGE_SLACK)
        val y = if (h <= vh) (vh - h) / 2f else o.y.coerceIn(vh - h - EDGE_SLACK, EDGE_SLACK)
        return Offset(x, y)
    }

    private companion object {
        const val FIT_MARGIN = 0.94f
        const val MAX_ZOOM = 14f
        const val EDGE_SLACK = 96f
    }
}
