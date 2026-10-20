package app.atelier.maitres.ui.intro

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * L'intro du studio Loucoume Interactive, au lancement du jeu (un peu moins de 3 secondes) :
 * le badge rebondit, le L et le i se dessinent, le triangle « play » tombe sur le i avec un
 * petit carillon et des étincelles, LOUCOUME apparaît lettre par lettre, puis un reflet passe
 * sur le badge. Un toucher la passe à tout moment.
 *
 * [onReveal] est appelé quand l'intro commence à s'effacer (l'écran suivant peut alors entrer),
 * [onDone] quand elle a disparu.
 */
@Composable
fun StudioIntro(sound: Boolean, onReveal: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    // Animations coupées dans les réglages : le logo apparaît d'un coup, sans mouvement.
    val calm = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val time = remember { Animatable(if (calm) END else 0f) }
    val fade = remember { Animatable(0f) }
    val shapes = remember { LogoShapes() }
    var skipped by remember { mutableStateOf(false) }

    LaunchedEffect(skipped) {
        if (!skipped) {
            if (calm) {
                delay(1800)
            } else {
                if (sound) launch { delay(CHIME_AT); playChime() }
                time.animateTo(END, tween(END.toInt(), easing = LinearEasing))
            }
        }
        // Un toucher file directement à la fin, avec un fondu plus bref.
        onReveal()
        fade.animateTo(1f, tween(if (skipped) 220 else 420, easing = FastOutSlowInEasing))
        onDone()
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Loucoume Interactive" }
            .pointerInput(Unit) { detectTapGestures(onPress = { skipped = true }) },
    ) {
        val alpha = 1f - fade.value
        drawBackground(alpha)
        val width = min(size.width * 0.78f, 420.dp.toPx())
        val k = width / 340f
        val grow = 1f + 0.04f * fade.value
        withTransform({
            translate((size.width - width) / 2, (size.height - 256f * k) / 2)
            scale(k * grow, k * grow, pivot = Offset(0f, 0f))
        }) {
            drawLogo(shapes, time.value, alpha)
        }
    }
}

private const val END = 2650f
private const val CHIME_AT = 1110L

private val BACK = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
private val EASE_OUT = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EASE_IN = CubicBezierEasing(0.42f, 0f, 1f, 1f)
private val EASE_IN_OUT = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val DRAW = CubicBezierEasing(0.6f, 0f, 0.2f, 1f)

private val Ink = Color(0xFF3A2433)
private val Pink = Color(0xFFFF6F93)
private val Sun = Color(0xFFFFE27A)
private val Sparks = listOf(Color(0xFFFFE27A), Color(0xFFFF8FBF), Color(0xFFFFB98A))

/** Les tracés, analysés une fois. */
private class LogoShapes {
    val letters: List<Path> = LOUCOUME_LETTERS.map { PathParser().parsePathString(it).toPath() }
    val small: Path = PathParser().parsePathString(INTERACTIVE_TEXT).toPath()
    val l = Path().apply { moveTo(35f, 30f); lineTo(35f, 84f); lineTo(64f, 84f) }
    val i = Path().apply { moveTo(83f, 84f); lineTo(83f, 57f) }
    val triangle = Path().apply { moveTo(77.5f, 28.5f); lineTo(77.5f, 46.5f); lineTo(93f, 37.5f); close() }
    val badge = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(6f, 6f, 114f, 114f, CornerRadius(32f)))
    }
    val lMeasure = PathMeasure().apply { setPath(l, false) }
    val iMeasure = PathMeasure().apply { setPath(i, false) }
}

/** Avancement de 0 à 1 d'une étape qui commence à [delay] et dure [duration] millisecondes. */
private fun step(t: Float, delay: Float, duration: Float, easing: Easing = LinearEasing): Float =
    easing.transform(((t - delay) / duration).coerceIn(0f, 1f))

/** Valeur le long d'images clés (position de 0 à 1, valeur), interpolées linéairement. */
private fun keys(p: Float, vararg frames: Pair<Float, Float>): Float {
    for (n in 1 until frames.size) {
        val (p1, v1) = frames[n]
        val (p0, v0) = frames[n - 1]
        if (p <= p1) return v0 + (v1 - v0) * ((p - p0) / (p1 - p0)).coerceIn(0f, 1f)
    }
    return frames.last().second
}

private fun DrawScope.drawBackground(alpha: Float) {
    drawRect(Brush.linearGradient(listOf(Color(0xFFFFF6EF), Color(0xFFFFEFF3), Color(0xFFFCE9F4)), end = Offset(size.width, size.height)), alpha = alpha)
    drawRect(
        Brush.radialGradient(listOf(Color(0x8CFFC9A3), Color.Transparent), center = Offset(size.width * 0.15f, size.height * 0.1f), radius = size.maxDimension * 0.45f),
        alpha = alpha,
    )
    drawRect(
        Brush.radialGradient(listOf(Color(0x80FF9CC6), Color.Transparent), center = Offset(size.width * 0.9f, size.height * 0.95f), radius = size.maxDimension * 0.45f),
        alpha = alpha,
    )
}

private fun DrawScope.drawLogo(shapes: LogoShapes, t: Float, alpha: Float) {
    // Le badge rebondit en tournant un peu.
    val pb = step(t, 0f, 620f, EASE_OUT)
    val badgeScale = keys(pb, 0f to 0.2f, 0.6f to 1.1f, 0.82f to 0.97f, 1f to 1f)
    val badgeTurn = keys(pb, 0f to -14f, 0.6f to 4f, 0.82f to -1f, 1f to 0f)
    val badgeAlpha = keys(pb, 0f to 0f, 0.6f to 1f, 1f to 1f) * alpha
    withTransform({
        scale(badgeScale, badgeScale, pivot = Offset(170f, 70f))
        rotate(badgeTurn, pivot = Offset(170f, 70f))
        translate(110f, 10f)
    }) {
        drawPath(shapes.badge, Brush.linearGradient(listOf(Color(0xFFFFB98A), Color(0xFFFF6FAE)), start = Offset(6f, 6f), end = Offset(114f, 114f)), alpha = badgeAlpha)
        drawPath(shapes.badge, Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 0.5f to Color.Transparent, startY = 6f, endY = 114f), alpha = badgeAlpha)

        // Le reflet qui traverse le badge, à la fin.
        val shine = step(t, 1500f, 620f, EASE_IN_OUT) * 190f
        clipPath(shapes.badge) {
            withTransform({
                rotate(20f, pivot = Offset(60f, 60f))
                translate(shine, 0f)
            }) {
                drawRect(Color.White, Offset(-40f, -20f), Size(28f, 170f), alpha = 0.35f * badgeAlpha)
            }
        }

        val pen = Stroke(16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPartial(shapes.lMeasure, step(t, 360f, 420f, DRAW), pen, badgeAlpha)
        drawPartial(shapes.iMeasure, step(t, 640f, 240f, EASE_OUT), pen, badgeAlpha)

        // Le triangle « play » tombe sur le i, s'écrase un peu, puis se remet droit.
        val pt = step(t, 820f, 460f, EASE_IN)
        if (pt > 0f) {
            val dy = keys(pt, 0f to -46f, 0.62f to 3f, 0.82f to -4f, 1f to 0f)
            val sx = keys(pt, 0f to 0.6f, 0.62f to 1.15f, 0.82f to 0.95f, 1f to 1f)
            val sy = keys(pt, 0f to 0.6f, 0.62f to 0.85f, 0.82f to 1.06f, 1f to 1f)
            val turn = keys(pt, 0f to -30f, 0.62f to 0f, 1f to 0f)
            val a = keys(pt, 0f to 0f, 0.62f to 1f, 1f to 1f) * alpha
            val pivot = Offset(83f, 37.5f)
            withTransform({
                translate(0f, dy)
                rotate(turn, pivot)
                scale(sx, sy, pivot)
            }) {
                drawPath(shapes.triangle, Sun, alpha = a, style = Fill)
                drawPath(shapes.triangle, Sun, alpha = a, style = Stroke(6f, join = StrokeJoin.Round))
            }
        }
    }

    // Étincelles autour du triangle quand il se pose.
    for (n in 0 until 9) {
        val angle = n / 9.0 * PI * 2 - 0.3
        val p = step(t, 1100f + (n % 3) * 25f, 620f, EASE_OUT)
        if (p <= 0f || p >= 1f) continue
        val dist = keys(p, 0f to 0f, 0.25f to 12f, 1f to 34f)
        val s = keys(p, 0f to 0.4f, 0.25f to 1f, 1f to 0.2f)
        val a = keys(p, 0f to 0f, 0.25f to 1f, 1f to 0f) * alpha
        val r = if (n % 3 != 0) 2.6f else 3.6f
        drawCircle(Sparks[n % 3], r * s, Offset(193f + cos(angle).toFloat() * dist, 47.5f + sin(angle).toFloat() * dist), alpha = a)
    }

    // LOUCOUME, lettre par lettre, puis « interactive ».
    shapes.letters.forEachIndexed { n, path ->
        val p = step(t, 1150f + n * 55f, 420f, BACK)
        val a = step(t, 1150f + n * 55f, 420f).coerceIn(0f, 1f)
        if (a > 0f) withTransform({ translate(0f, 16f * (1f - p)) }) { drawPath(path, Ink, alpha = a * alpha) }
    }
    val ps = step(t, 1650f, 420f, EASE_OUT)
    if (ps > 0f) withTransform({ translate(0f, 8f * (1f - ps)) }) { drawPath(shapes.small, Pink, alpha = ps * alpha) }
}

/** Trace la première part [fraction] d'un trait de pinceau blanc. */
private fun DrawScope.drawPartial(measure: PathMeasure, fraction: Float, pen: Stroke, alpha: Float) {
    if (fraction <= 0f) return
    val part = Path()
    measure.getSegment(0f, measure.length * fraction, part, true)
    drawPath(part, Color.White, alpha = alpha, style = pen)
}

/** Le petit carillon : trois notes cristallines qui s'éteignent doucement. */
private suspend fun playChime() = withContext(Dispatchers.Default) {
    runCatching {
        val rate = 44100
        val n = (rate * 1.7f).roundToInt()
        val pcm = ShortArray(n)
        val notes = listOf(Triple(1318.5f, 0f, 0.11f), Triple(1975.5f, 0.09f, 0.08f), Triple(2637f, 0.18f, 0.04f))
        for (i in 0 until n) {
            val t = i.toFloat() / rate
            var s = 0f
            for ((f, start, peak) in notes) {
                val u = t - start
                if (u < 0f || u > 1.5f) continue
                val env = if (u < 0.012f) peak * u / 0.012f else peak * exp(ln(0.0001f / peak) * ((u - 0.012f) / 1.388f).coerceAtMost(1f))
                s += sin(2 * PI * f * u).toFloat() * env
            }
            pcm[i] = (s * 1.6f * Short.MAX_VALUE).roundToInt().coerceIn(-32767, 32767).toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(n * 2)
            .build()
        try {
            track.write(pcm, 0, n)
            track.play()
            // Le carillon va au bout même si l'intro est passée d'un toucher.
            withContext(NonCancellable) { delay(1800) }
        } finally {
            track.release()
        }
    }
}
