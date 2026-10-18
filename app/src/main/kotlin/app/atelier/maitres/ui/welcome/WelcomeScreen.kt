package app.atelier.maitres.ui.welcome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Typeface
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.atelier.maitres.R
import app.atelier.maitres.core.Difficulty
import app.atelier.maitres.core.PaintByNumber
import app.atelier.maitres.core.Segmenter
import app.atelier.maitres.ui.theme.CartelStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Écran d'accueil : La Nuit étoilée, découpée par le vrai moteur au niveau Facile,
 * se peint d'elle-même zone par zone. Le joueur voit le geste du jeu avant sa première toile,
 * et trois étapes le résument en une phrase chacune.
 */
@Composable
fun WelcomeScreen(onEnter: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var demo by remember { mutableStateOf<WelcomeDemo?>(null) }
    var shown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        shown = true
        demo = runCatching { withContext(Dispatchers.Default) { WelcomeDemo.load(context) } }.getOrNull()
    }

    // La toile se peint au rythme de l'écran ; sans animations système, elle apparaît peinte.
    val current = demo
    LaunchedEffect(current) {
        if (current == null) return@LaunchedEffect
        if (animationsDisabled(context)) {
            current.paintUpTo(current.regionCount)
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (current.painted < current.regionCount) {
            withFrameNanos { now ->
                val t = ((now - start) / 1_000_000f - START_DELAY_MS) / PAINT_DURATION_MS
                val k = t.coerceIn(0f, 1f)
                current.paintUpTo(if (k < 1f) (current.regionCount * k.pow(1.15f)).toInt() else current.regionCount)
            }
        }
    }

    val minTextPx = with(density) { 8.dp.toPx() }
    val maxTextPx = with(density) { 15.dp.toPx() }
    val numberPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            color = 0xFF8C8478.toInt()
        }
    }
    val background = MaterialTheme.colorScheme.background

    Column(
        Modifier
            .fillMaxSize()
            .background(background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().clearAndSetSemantics {}) {
            val d = current
            if (d != null) {
                Canvas(Modifier.fillMaxSize()) {
                    d.version // relu à chaque zone peinte
                    val scale = max(size.width / d.width, size.height / d.height)
                    val dx = (size.width - d.width * scale) / 2f
                    val dy = (size.height - d.height * scale) / 2f
                    drawImage(
                        image = d.image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(d.width, d.height),
                        dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
                        dstSize = IntSize((d.width * scale).roundToInt(), (d.height * scale).roundToInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                    val canvas = d.canvas
                    val native = drawContext.canvas.nativeCanvas
                    for (r in 0 until canvas.regionCount) {
                        if (d.isPainted(r)) continue
                        val textSize = min(canvas.labelRadius[r] * 1.25f * scale, maxTextPx)
                        if (textSize < minTextPx) continue
                        numberPaint.textSize = textSize
                        native.drawText(
                            (canvas.regionColor[r] + 1).toString(),
                            dx + canvas.labelX[r] * scale,
                            dy + canvas.labelY[r] * scale + textSize * 0.36f,
                            numberPaint,
                        )
                    }
                }
            }
            // Fondu vers le fond de l'écran, sous lequel arrive le texte.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.32f)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, background))),
            )
        }

        Column(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 28.dp),
        ) {
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.appear(shown, 0),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.welcome_sub),
                style = CartelStyle.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.appear(shown, 1),
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth().appear(shown, 2), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(R.string.welcome_step1, R.string.welcome_step2, R.string.welcome_step3).forEachIndexed { index, step ->
                    Column(Modifier.weight(1f)) {
                        Text(
                            (index + 1).toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(step),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Button(
                onClick = onEnter,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).appear(shown, 3),
            ) {
                Text(stringResource(R.string.welcome_cta), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** Entrée en fondu, un élément après l'autre. */
@Composable
private fun Modifier.appear(shown: Boolean, order: Int): Modifier {
    val k by animateFloatAsState(if (shown) 1f else 0f, tween(900, delayMillis = 250 + order * 200), label = "appear")
    return graphicsLayer {
        alpha = k
        translationY = (1f - k) * 12.dp.toPx()
    }
}

private fun animationsDisabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private const val START_DELAY_MS = 700f
private const val PAINT_DURATION_MS = 6000f

/**
 * La toile de démonstration : petite image découpée, rendu papier et contours, puis
 * remplissage zone par zone avec la couleur de la palette modulée par la matière de l'original.
 */
private class WelcomeDemo(
    val canvas: PaintByNumber,
    private val original: IntArray,
) {
    val width = canvas.width
    val height = canvas.height
    val regionCount = canvas.regionCount
    private val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val image = bitmap.asImageBitmap()
    private val pixels = IntArray(width * height)
    private val texture = FloatArray(width * height)

    /** Du haut à gauche vers le bas à droite, comme un peintre qui avance sur la toile. */
    private val order = (0 until regionCount).sortedBy { canvas.labelY[it] + canvas.labelX[it] * 0.3f }
    private val done = BooleanArray(regionCount)

    var painted = 0
        private set
    var version by mutableIntStateOf(0)
        private set

    init {
        val labels = canvas.labels
        for (y in 0 until height) for (x in 0 until width) {
            val i = y * width + x
            val l = labels[i]
            val edge = (x > 0 && labels[i - 1] != l) || (y > 0 && labels[i - width] != l)
            pixels[i] = if (edge) OUTLINE else PAPER
        }
        for (r in 0 until regionCount) {
            val start = canvas.regionStart[r]
            val end = canvas.regionStart[r + 1]
            var sum = 0f
            for (k in start until end) sum += luminance(original[canvas.regionPixels[k]])
            val mean = max(1f, sum / max(1, end - start))
            for (k in start until end) {
                val p = canvas.regionPixels[k]
                texture[p] = (1f + TEXTURE_STRENGTH * (luminance(original[p]) / mean - 1f)).coerceIn(0.7f, 1.3f)
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    fun isPainted(region: Int) = done[region]

    fun paintUpTo(target: Int) {
        if (target <= painted) return
        while (painted < target) {
            val r = order[painted++]
            val c = canvas.referenceColor(r)
            val red = (c shr 16) and 0xFF
            val green = (c shr 8) and 0xFF
            val blue = c and 0xFF
            for (k in canvas.regionStart[r] until canvas.regionStart[r + 1]) {
                val p = canvas.regionPixels[k]
                val t = texture[p]
                pixels[p] = (0xFF shl 24) or
                    (min(255f, red * t).toInt() shl 16) or
                    (min(255f, green * t).toInt() shl 8) or
                    min(255f, blue * t).toInt()
            }
            done[r] = true
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        version++
    }

    companion object {
        private const val SIZE = 520
        private const val PAPER = 0xFFFBF8F2.toInt()
        private const val OUTLINE = 0xFFB4AC9F.toInt()
        private const val TEXTURE_STRENGTH = 0.55f

        private fun luminance(c: Int): Float =
            0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)

        fun load(context: Context): WelcomeDemo {
            val source = ImageDecoder.createSource(context.assets, "paintings/nuit_etoilee.jpg")
            val image = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val scale = SIZE.toFloat() / max(info.size.width, info.size.height)
                decoder.setTargetSize(
                    max(1, (info.size.width * scale).roundToInt()),
                    max(1, (info.size.height * scale).roundToInt()),
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val w = image.width
            val h = image.height
            val argb = IntArray(w * h)
            image.getPixels(argb, 0, w, 0, 0, w, h)
            val canvas = Segmenter(Difficulty.FACILE.params(w, h)).segment(argb, w, h)
            return WelcomeDemo(canvas, argb)
        }
    }
}
