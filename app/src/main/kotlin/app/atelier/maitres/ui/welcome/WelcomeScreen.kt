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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.atelier.maitres.data.Career
import app.atelier.maitres.ui.play.FlameChip
import app.atelier.maitres.ui.play.RankBadge
import app.atelier.maitres.ui.play.dottedPaper
import app.atelier.maitres.ui.play.emphasized
import app.atelier.maitres.ui.play.flatShadow
import app.atelier.maitres.ui.play.formatCount
import app.atelier.maitres.ui.play.rankNames
import app.atelier.maitres.ui.theme.Fraunces
import app.atelier.maitres.ui.theme.HandStyle
import app.atelier.maitres.ui.theme.LocalPlayColors
import app.atelier.maitres.ui.theme.Play
import app.atelier.maitres.ui.theme.PlayTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Écran d'accueil : La Nuit étoilée, découpée par le vrai moteur au niveau Facile, se peint
 * d'elle-même zone par zone sous un pinceau dessiné, dans un polaroïd posé sur la table de
 * l'atelier. Le joueur voit le geste du jeu avant sa première toile ; un joueur qui revient
 * retrouve son rang et sa série de jours.
 */
@Composable
fun WelcomeScreen(career: Career, onEnter: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = LocalPlayColors.current
    var demo by remember { mutableStateOf<WelcomeDemo?>(null) }
    var shown by remember { mutableStateOf(false) }
    val animate = remember { !animationsDisabled(context) }
    val returning = remember { career.touches > 0 }

    LaunchedEffect(Unit) {
        shown = true
        demo = runCatching { withContext(Dispatchers.Default) { WelcomeDemo.load(context) } }.getOrNull()
    }

    // La toile se peint au rythme de l'écran ; sans animations système, elle apparaît peinte.
    val current = demo
    LaunchedEffect(current) {
        if (current == null) return@LaunchedEffect
        if (!animate) {
            current.paintUpTo(current.regionCount)
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        do {
            val going = withFrameNanos { now ->
                val ms = (now - start) / 1_000_000f
                val k = ((ms - START_DELAY_MS) / PAINT_DURATION_MS).coerceIn(0f, 1f)
                current.paintUpTo(if (k < 1f) (current.regionCount * k.pow(1.15f)).toInt() else current.regionCount)
                current.moveBrush(ms)
                current.painted < current.regionCount || current.brushAlpha > 0f
            }
        } while (going)
    }

    val minTextPx = with(density) { 7.dp.toPx() }
    val maxTextPx = with(density) { 13.dp.toPx() }
    val brushUnit = with(density) { 84.dp.toPx() } / 124f
    val dabPx = with(density) { 2.dp.toPx() }
    val numberPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            color = 0xFF8C8478.toInt()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .dottedPaper(colors)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Le polaroïd qui se peint tout seul, avec son autocollant de rang.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 34.dp, vertical = 18.dp)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.appear(shown, 0)) {
                Column(
                    Modifier
                        .rotate(-3f)
                        .flatShadow(Play.Ink.copy(alpha = 0.18f), 5.dp, 8.dp, 4.dp)
                        .background(colors.polaroid, RoundedCornerShape(4.dp))
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
                ) {
                    val d = current
                    Box(
                        Modifier
                            .aspectRatio(if (d != null) d.width.toFloat() / d.height else 1.6f)
                            .background(Color(0xFFFBF8F2)),
                    ) {
                        if (d != null) {
                            Canvas(Modifier.fillMaxSize()) {
                                d.version // relu à chaque zone peinte
                                val scale = min(size.width / d.width, size.height / d.height)
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
                                // Le pinceau suit les zones qui se peignent, la pointe trempée dans leur couleur.
                                val tip = Offset(dx + d.brushX * scale, dy + d.brushY * scale + sin(d.brushTime / 90f) * dabPx)
                                drawPaintbrush(tip, brushUnit, Color(d.brushColor), d.brushAlpha)
                            }
                        }
                    }
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        d?.version // relu à chaque zone peinte
                        val finished = d != null && d.painted >= d.regionCount
                        Text(
                            stringResource(if (finished) R.string.welcome_cap_done else R.string.welcome_cap),
                            style = HandStyle,
                            color = colors.ink,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.touches_n, formatCount((d?.painted ?: 0) * 3)),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Play.Coral,
                        )
                    }
                }
                RankBadge(
                    size = 58.dp,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 18.dp, y = (-20).dp),
                )
            }
        }

        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(start = 28.dp, end = 28.dp, top = 4.dp, bottom = 28.dp),
        ) {
            Text(
                emphasized(R.string.welcome_title, R.string.welcome_title_em),
                style = PlayTitle.copy(fontSize = 34.sp, lineHeight = 36.sp),
                color = colors.ink,
                modifier = Modifier.appear(shown, 1),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.appear(shown, 2), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (returning) stringResource(R.string.welcome_back, rankNames()[career.rank])
                    else stringResource(R.string.welcome_new),
                    style = HandStyle.copy(fontSize = 23.sp, lineHeight = 25.sp),
                    color = colors.kraft,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (returning && career.streak > 0) {
                    Spacer(Modifier.width(10.dp))
                    FlameChip(career.streak, career.paintedToday, colors.kraft)
                }
            }
            Spacer(Modifier.height(22.dp))
            BrushStrokeButton(
                label = stringResource(if (returning) R.string.welcome_cta_back else R.string.welcome_cta),
                onClick = onEnter,
                animate = animate,
                modifier = Modifier.appear(shown, 3),
                color = Play.Coral,
                labelStyle = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp),
                labelColor = Color.White,
            )
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

    /** Pointe du pinceau, en pixels de la toile, et sa peinture. */
    var brushX = width * 0.5f
        private set
    var brushY = height * 0.3f
        private set
    var brushColor = 0
        private set
    var brushAlpha = 0f
        private set
    var brushTime = 0f
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

    /** Rapproche le pinceau de la dernière zone peinte ; il s'efface une fois la toile finie. */
    fun moveBrush(timeMs: Float) {
        if (painted == 0) return
        val r = order[painted - 1]
        brushX += (canvas.labelX[r] - brushX) * 0.16f
        brushY += (canvas.labelY[r] - brushY) * 0.16f
        brushColor = canvas.referenceColor(r) or (0xFF shl 24)
        brushAlpha = if (painted < regionCount) min(1f, brushAlpha + 0.08f) else max(0f, brushAlpha - 0.03f)
        brushTime = timeMs
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
