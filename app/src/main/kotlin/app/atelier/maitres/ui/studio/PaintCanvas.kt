package app.atelier.maitres.ui.studio

import android.graphics.Paint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.atelier.maitres.core.PaintingSession
import kotlin.math.max
import kotlin.math.min

/**
 * La toile interactive : zoom à deux doigts, déplacement, tap pour peindre,
 * appui long pour choisir la couleur d'une zone.
 *
 * Les numéros sont dessinés à l'échelle de l'écran (et non dans le bitmap) : ils
 * restent nets à tous les zooms, et n'apparaissent que lorsqu'ils sont assez grands
 * pour être lus, comme sur une vraie toile que l'on regarde de plus en plus près.
 */
@Composable
fun PaintCanvas(state: StudioState, peekOriginal: Boolean, look: AtelierLook, modifier: Modifier = Modifier) {
    val camera = state.camera
    val canvas = state.canvas
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current

    val painted = remember(state) { state.renderer.bitmap.asImageBitmap() }
    val original = remember(state) { state.loaded.original.asImageBitmap() }
    val imageSize = remember(state) { IntSize(canvas.width, canvas.height) }
    val numbers = remember(state) { Array(canvas.palette.size) { (it + 1).toString() } }

    val minTextPx = with(density) { 9.dp.toPx() }
    val maxTextPx = with(density) { 30.dp.toPx() }
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }
    val strongPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }

    // L'ombre de la toile sur le mur : le flou des ombres Android, plus doux qu'un dégradé.
    val shadowPaint = remember(look, density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = CANVAS_EDGE.last()
            with(density) { setShadowLayer(24.dp.toPx(), look.shadowDx.dp.toPx(), 16.dp.toPx(), look.canvasShadow.toArgb()) }
        }
    }
    val edgeStep = with(density) { 1.dp.toPx() }
    val mistakeDot = with(density) { 4.dp.toPx() }
    val mistakeRing = with(density) { 2.dp.toPx() }

    // Éclaboussure : un anneau de la couleur posée qui s'élargit puis s'efface.
    val splash = remember { Animatable(1f) }
    val stroke = state.lastStroke
    LaunchedEffect(stroke?.id) {
        if (stroke != null) {
            // Un « tic » léger sous le doigt à chaque touche de pinceau.
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            splash.snapTo(0f)
            splash.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
    }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged(camera::onViewSize)
            .pointerInput(state) {
                detectTransformGestures { centroid, pan, zoom, _ -> camera.transform(centroid, pan, zoom) }
            }
            .pointerInput(state) {
                // Pas de double tap : il retarderait chaque touche de pinceau de 300 ms.
                detectTapGestures(
                    onTap = { state.onTap(camera.toImage(it)) },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        state.onLongPress(camera.toImage(it))
                    },
                )
            },
    ) {
        // Lecture de l'état : toute nouvelle touche de couleur relance le dessin.
        state.version
        val zoomScale = camera.scale
        val origin = camera.origin

        // La toile accrochée au mur : son ombre, puis l'épaisseur du châssis, en tranches.
        val canvasSize = Size(canvas.width * zoomScale, canvas.height * zoomScale)
        val depth = CANVAS_EDGE.size * edgeStep
        drawContext.canvas.nativeCanvas.drawRect(origin.x + depth / 2, origin.y + depth / 2, origin.x + canvasSize.width, origin.y + canvasSize.height, shadowPaint)
        for (i in CANVAS_EDGE.indices.reversed()) {
            drawRect(Color(CANVAS_EDGE[i]), origin + Offset((i + 1) * edgeStep, (i + 1) * edgeStep), canvasSize)
        }

        withTransform({
            translate(origin.x, origin.y)
            scale(zoomScale, zoomScale, pivot = Offset.Zero)
        }) {
            drawImage(
                image = if (peekOriginal) original else painted,
                srcOffset = IntOffset.Zero,
                srcSize = imageSize,
                dstOffset = IntOffset.Zero,
                dstSize = imageSize,
                filterQuality = if (zoomScale > 3f) FilterQuality.None else FilterQuality.Low,
            )
        }
        // Le grain du lin, par-dessus la peinture comme sous un vrai pinceau.
        drawRect(Textures.linen, origin, canvasSize, blendMode = BlendMode.Multiply)

        if (!peekOriginal) {
            val highlight = (state.tool as? Tool.Palette)?.index ?: -1
            val left = -origin.x / zoomScale
            val top = -origin.y / zoomScale
            val right = left + size.width / zoomScale
            val bottom = top + size.height / zoomScale
            val native = drawContext.canvas.nativeCanvas
            for (r in 0 until canvas.regionCount) {
                val mistake = state.session.isMistake(r)
                if (state.session.fills[r] != PaintingSession.EMPTY && !mistake) continue
                val x = canvas.labelX[r]
                val y = canvas.labelY[r]
                if (x < left || x > right || y < top || y > bottom) continue
                val textSize = min(canvas.labelRadius[r] * 1.25f * zoomScale, maxTextPx)
                val at = Offset(origin.x + x * zoomScale, origin.y + y * zoomScale)
                if (mistake) {
                    // Une zone de la mauvaise couleur garde son numéro, dans une pastille cerclée de corail,
                    // et reste repérable même de loin, en simple point.
                    if (textSize < minTextPx) {
                        drawCircle(Color.White, mistakeDot + mistakeRing, at)
                        drawCircle(MISTAKE, mistakeDot, at)
                        continue
                    }
                    val radius = textSize * 0.78f
                    drawCircle(Color(0xF2FFFCF5), radius, at)
                    drawCircle(MISTAKE, radius, at, style = Stroke(mistakeRing))
                    strongPaint.textSize = textSize
                    strongPaint.color = STRONG_INK
                    native.drawText(numbers[canvas.regionColor[r]], at.x, at.y + textSize * 0.36f, strongPaint)
                    continue
                }
                if (textSize < minTextPx) continue
                val colorIndex = canvas.regionColor[r]
                val paint = if (colorIndex == highlight) strongPaint else textPaint
                paint.textSize = textSize
                paint.color = if (colorIndex == highlight) STRONG_INK else SOFT_INK
                native.drawText(numbers[colorIndex], origin.x + x * zoomScale, origin.y + y * zoomScale + textSize * 0.36f, paint)
            }

            val s = state.lastStroke
            val t = splash.value
            if (s != null && t < 1f) {
                val center = origin + s.imagePoint * zoomScale
                val radius = with(density) { (10.dp + 34.dp * t).toPx() }
                drawCircle(
                    color = Color(s.color).copy(alpha = (1f - t) * 0.85f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = max(1f, with(density) { 3.dp.toPx() } * (1f - t))),
                )
            }
        }
    }
}

private val CANVAS_EDGE = longArrayOf(0xFFE4DACA, 0xFFDDD2BF, 0xFFD7CBB6, 0xFFD1C4AE, 0xFFCBBDA6, 0xFFC4B69E, 0xFFBBAC93).map { it.toInt() }
private val MISTAKE = Color(0xFFE8553B)
private val SOFT_INK = 0xFF8C8478.toInt()
private val STRONG_INK = 0xFF2B2620.toInt()
