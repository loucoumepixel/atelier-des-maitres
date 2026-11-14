package app.atelier.maitres.ui.intro

import android.graphics.Bitmap
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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atelier.maitres.R
import app.atelier.maitres.ui.theme.Fraunces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * L'intro du studio Loucoume Interactive, repeinte à l'Atelier (un peu moins de 3 secondes),
 * comme les studios de cinéma adaptent leur logo à chaque film : sur le mur rouge du musée,
 * une toile de lin s'allume dans son cadre doré ; le badge y est peint en trois coups de brosse
 * (ocre puis vermillon), le pinceau trace le L et le i en blanc, une goutte jaune de Naples tombe
 * et devient le triangle « play » (carillon et éclaboussures), LOUCOUME apparaît en outremer d'un
 * seul geste, puis « interactive », un reflet de vernis passe et le cartel du musée s'affiche.
 * Un toucher la passe à tout moment.
 *
 * Tout est dessiné par le code : aucune image ni aucun son enregistré.
 *
 * [onReveal] est appelé quand l'intro commence à s'effacer (l'écran suivant peut alors entrer),
 * [onDone] quand elle a disparu.
 */
@Composable
fun StudioIntro(sound: Boolean, onReveal: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    // Animations coupées dans les réglages : le tableau fini apparaît d'un coup, sans mouvement.
    val calm = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val time = remember { Animatable(if (calm) END else 0f) }
    val fade = remember { Animatable(0f) }
    val art = remember { IntroArt() }
    val measurer = rememberTextMeasurer()
    val medium = stringResource(R.string.intro_medium)
    var skipped by remember { mutableStateOf(false) }

    LaunchedEffect(skipped) {
        if (!skipped) {
            if (calm) {
                delay(1800)
            } else {
                if (sound) launch { playSoundtrack() }
                time.animateTo(END, tween(END.toInt(), easing = LinearEasing))
            }
        }
        // Un toucher file directement au tableau fini, avec un fondu plus bref.
        if (skipped) time.snapTo(END)
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
        val t = time.value
        drawWall(art, t, alpha)
        // La scène est dessinée dans un repère de 400 × 820, centré sur la toile.
        val k = min(min(size.width * 0.86f, 460.dp.toPx()) / 344f, size.height * 0.68f / 380f)
        withTransform({
            translate(size.width / 2 - 200f * k, size.height * 0.46f - 400f * k)
            scale(k, k, pivot = Offset.Zero)
        }) {
            drawScene(art, t, alpha, measurer, medium, k)
        }
    }
}

private const val END = 2900f

private val EASE_OUT = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
private val EASE_IN = CubicBezierEasing(0.55f, 0.055f, 0.675f, 0.19f)
private val EASE_IN_OUT = CubicBezierEasing(0.645f, 0.045f, 0.355f, 1f)
private val DRAW = CubicBezierEasing(0.25f, 0.6f, 0.4f, 1f)

// La palette de l'Atelier.
private val Ochre = Color(0xFFE2A646)
private val Orange = Color(0xFFD4673A)
private val Vermilion = Color(0xFFA8352C)
private val LeadWhite = Color(0xFFF8EFDB)
private val Naples = Color(0xFFF3D27A)
private val Ultramarine = Color(0xFF1F3A68)
private val Cinnabar = Color(0xFFB8432F)
private val Umber = Color(0xFF2A1A10)

/** Les tracés et les textures, préparés une fois. */
private class IntroArt {
    val letters: List<Path> = LOUCOUME_LETTERS.map { PathParser().parsePathString(it).toPath() }
    val small: Path = PathParser().parsePathString(INTERACTIVE_TEXT).toPath()
    val l = Path().apply { moveTo(35f, 30f); lineTo(35f, 84f); lineTo(64f, 84f) }
    val i = Path().apply { moveTo(83f, 84f); lineTo(83f, 57f) }
    val triangle = Path().apply { moveTo(77.5f, 28.5f); lineTo(77.5f, 46.5f); lineTo(93f, 37.5f); close() }
    val badge = Path().apply { addRoundRect(RoundRect(6f, 6f, 114f, 114f, CornerRadius(32f))) }
    val lMeasure = PathMeasure().apply { setPath(l, false) }
    val iMeasure = PathMeasure().apply { setPath(i, false) }
    val damask: Path = PathParser().parsePathString("M20 4c6 8 14 10 14 20s-8 14-14 28C14 38 6 34 6 24S14 12 20 4z").toPath()
    val leafTop: Path = PathParser().parsePathString("M188 262q12 -6 24 0q-12 8 -24 0z").toPath()
    val leafBottom: Path = PathParser().parsePathString("M188 538q12 6 24 0q-12 -8 -24 0z").toPath()
    val handle: Path = PathParser().parsePathString("M-4 -26L-2.2 -100Q0 -104 2.2 -100L4 -26Z").toPath()
    val tip: Path = PathParser().parsePathString("M-6 -15L6 -15L5 -4Q0 4 -5 -4Z").toPath()
    val beam = Path().apply { moveTo(150f, -400f); lineTo(250f, -400f); lineTo(380f, 560f); lineTo(20f, 560f); close() }
    val bristles: ShaderBrush = ShaderBrush(ImageShader(bristleTexture(), TileMode.Repeated, TileMode.Repeated))
    val linen: ShaderBrush = ShaderBrush(ImageShader(linenTexture(), TileMode.Repeated, TileMode.Repeated))
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

/** Un petit tremblement stable de -1 à 1, pour les bords irréguliers des coups de pinceau. */
private fun jitter(seed: Int): Float {
    val x = sin(seed * 12.9898f + 78.233f) * 43758.547f
    return (x - floor(x)) * 2f - 1f
}

// ————— Le mur du musée —————

private fun DrawScope.drawWall(art: IntroArt, t: Float, alpha: Float) {
    drawRect(Color(0xFF170A0C), alpha = alpha)
    val light = 0.35f + 0.65f * step(t, 0f, 500f, EASE_OUT)
    drawRect(
        Brush.radialGradient(
            0f to Color(0xFF7A3A33), 0.45f to Color(0xFF40191B), 1f to Color(0xFF170A0C),
            center = Offset(size.width / 2, size.height * 0.38f),
            radius = size.maxDimension * 0.65f,
        ),
        alpha = alpha * light,
    )
    // Le damas du papier peint.
    val tile = 40.dp.toPx() * 0.9f
    val unit = tile / 40f
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else -tile / 2
        while (x < size.width) {
            withTransform({ translate(x, y); scale(unit, unit, pivot = Offset.Zero) }) {
                drawPath(art.damask, Color.Black, alpha = 0.16f * alpha * light)
            }
            x += tile
        }
        y += 56f * unit
        row++
    }
}

// ————— La toile, son cadre et le logo —————

private fun DrawScope.drawScene(art: IntroArt, t: Float, alpha: Float, measurer: TextMeasurer, medium: String, k: Float) {
    // Le faisceau du projecteur.
    val beam = 0.07f * step(t, 0f, 500f, EASE_OUT) + 0.05f * step(t, 2250f, 400f, EASE_OUT)
    drawPath(art.beam, Color(0xFFFFE9C4), alpha = beam * alpha)

    val pf = step(t, 0f, 450f, EASE_OUT)
    val a = pf * alpha
    val grow = 0.94f + 0.06f * pf
    withTransform({ scale(grow, grow, pivot = Offset(200f, 400f)) }) {
        drawFrame(art, a)
        clipRect(58f, 288f, 342f, 512f) {
            withTransform({
                translate(60.6f, 293.4f)
                scale(0.82f, 0.82f, pivot = Offset.Zero)
            }) {
                drawLogo(art, t, a)
            }
            drawVarnish(t, a)
        }
        // Le pinceau déborde de la toile : il est dessiné hors du cadre de découpe.
        withTransform({
            translate(60.6f, 293.4f)
            scale(0.82f, 0.82f, pivot = Offset.Zero)
        }) {
            drawBrush(art, t, a)
        }
    }
    drawCartel(t, alpha, measurer, medium, k)
}

private val GoldStops = arrayOf(
    0f to Color(0xFFF3DC97), 0.25f to Color(0xFFB8913D), 0.5f to Color(0xFFECD08A),
    0.75f to Color(0xFF8A6421), 1f to Color(0xFFD8B45E),
)

private fun DrawScope.drawFrame(art: IntroArt, a: Float) {
    if (a <= 0f) return
    // Ombre portée sur le mur.
    drawRect(Color.Black, Offset(34f, 270f), Size(344f, 284f), alpha = 0.22f * a)
    drawRect(Color.Black, Offset(31f, 264f), Size(344f, 284f), alpha = 0.22f * a)
    // Le cadre doré.
    drawRect(Brush.linearGradient(*GoldStops, start = Offset(28f, 258f), end = Offset(372f, 542f)), Offset(28f, 258f), Size(344f, 284f), alpha = a)
    drawRect(Color(0xFF6B4C17), Offset(36f, 266f), Size(328f, 268f), alpha = a, style = Stroke(2f))
    drawRect(
        Brush.linearGradient(0f to Color(0xFFC9A24C), 0.5f to Color(0xFF7A561A), 1f to Color(0xFFF3DC97), start = Offset(40f, 270f), end = Offset(360f, 530f)),
        Offset(40f, 270f), Size(320f, 260f), alpha = a,
    )
    val ornament = Color(0xFFF6E3A8)
    for (c in listOf(Offset(38f, 268f), Offset(362f, 268f), Offset(38f, 532f), Offset(362f, 532f))) drawCircle(ornament, 3f, c, alpha = 0.75f * a)
    drawPath(art.leafTop, ornament, alpha = 0.75f * a)
    drawPath(art.leafBottom, ornament, alpha = 0.75f * a)
    // La feuillure, puis la toile de lin.
    drawRect(Color(0xFF5A3D14), Offset(52f, 282f), Size(296f, 236f), alpha = a)
    drawRect(Color(0xFFEFE6D4), Offset(58f, 288f), Size(284f, 224f), alpha = a)
    drawRect(art.linen, Offset(58f, 288f), Size(284f, 224f), alpha = a)
    drawRect(Color.Black, Offset(60f, 290f), Size(280f, 220f), alpha = 0.18f * a, style = Stroke(4f))
}

/** Les trois coups de brosse du badge : départ, arrivée (repère du badge) et moment. */
private class BadgeStroke(val from: Offset, val to: Offset, val start: Float, val color: Color)

private val BadgeStrokes = listOf(
    BadgeStroke(Offset(-8f, 30f), Offset(128f, 18f), 250f, Ochre),
    BadgeStroke(Offset(128f, 62f), Offset(-8f, 58f), 400f, Orange),
    BadgeStroke(Offset(-8f, 100f), Offset(128f, 92f), 550f, Vermilion),
)
private const val BADGE_STROKE = 260f

private fun DrawScope.drawLogo(art: IntroArt, t: Float, a: Float) {
    if (a <= 0f) return
    val badgeFill = Brush.linearGradient(0f to Ochre, 0.5f to Orange, 1f to Vermilion, start = Offset(6f, 6f), end = Offset(114f, 114f))
    withTransform({ translate(110f, 10f) }) {
        // Le badge, peint en trois coups de brosse aux bords irréguliers.
        BadgeStrokes.forEachIndexed { n, s ->
            val p = step(t, s.start, BADGE_STROKE, DRAW)
            if (p <= 0f) return@forEachIndexed
            val region = brushRegion(s.from, s.to, p, 21f, n * 101)
            clipPath(art.badge) {
                clipPath(region) {
                    drawRect(Umber, Offset(1f, 2f), Size(120f, 120f), alpha = 0.25f * a)
                    drawRect(badgeFill, Offset.Zero, Size(120f, 120f), alpha = a)
                    drawRect(art.bristles, Offset.Zero, Size(120f, 120f), alpha = a)
                    drawRect(
                        Brush.linearGradient(0f to Color.White.copy(alpha = 0.22f), 0.45f to Color.Transparent, start = Offset(6f, 6f), end = Offset(114f, 114f)),
                        Offset.Zero, Size(120f, 120f), alpha = a,
                    )
                }
            }
        }

        // Le L et le i en blanc de plomb, avec leur relief.
        val pen = Stroke(16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPainted(art, art.lMeasure, step(t, 800f, 340f, DRAW), pen, a)
        drawPainted(art, art.iMeasure, step(t, 1120f, 170f, EASE_OUT), pen, a)

        // La goutte de jaune de Naples tombe, s'écrase, puis se remet droite : le triangle « play ».
        val pt = step(t, 1250f, 420f, EASE_IN)
        if (pt > 0f) {
            val dy = keys(pt, 0f to -120f, 0.62f to 3f, 0.82f to -4f, 1f to 0f)
            val sx = keys(pt, 0f to 0.55f, 0.62f to 1.2f, 0.82f to 0.95f, 1f to 1f)
            val sy = keys(pt, 0f to 1.25f, 0.62f to 0.8f, 0.82f to 1.06f, 1f to 1f)
            val da = keys(pt, 0f to 0f, 0.2f to 1f, 1f to 1f) * a
            val pivot = Offset(83f, 37.5f)
            withTransform({
                translate(0f, dy)
                scale(sx, sy, pivot)
            }) {
                drawPath(art.triangle, Umber, alpha = 0.2f * da, style = Stroke(7f, join = StrokeJoin.Round))
                drawPath(art.triangle, Naples, alpha = da, style = Fill)
                drawPath(art.triangle, Naples, alpha = da, style = Stroke(6f, join = StrokeJoin.Round))
                drawPath(art.triangle, art.bristles, alpha = 0.6f * da, style = Fill)
            }
        }
    }

    // Les éclaboussures restent sur la toile.
    val ps = step(t, 1510f, 380f, EASE_OUT)
    if (ps > 0f) {
        for (n in 0 until 10) {
            val angle = (n / 10.0 * PI * 2 + if (n % 2 == 1) 0.25 else -0.1).toFloat()
            val dist = (16 + (n * 37 % 17)) * ps
            val r = if (n % 3 != 0) 2.2f else 3.4f
            val center = Offset(193f + cos(angle) * dist, 47.5f + sin(angle) * dist * 0.8f)
            val stretch = if (ps < 1f) 1f + 2.5f * (1f - ps) else 1f
            withTransform({ rotate(angle * 180f / PI.toFloat(), center) }) {
                drawOval(Naples, Offset(center.x - r * stretch, center.y - r * 0.85f), Size(2 * r * stretch, 1.7f * r), alpha = keys(ps, 0f to 0f, 0.15f to 1f, 1f to 0.9f) * a)
            }
        }
    }

    // LOUCOUME en outremer, révélé d'un seul geste de pinceau de gauche à droite.
    val pw = step(t, 1450f, 520f, EASE_IN_OUT)
    if (pw > 0f) {
        clipPath(brushRegion(Offset(6f, 188f), Offset(334f, 186f), pw, 29f, 7)) {
            for (path in art.letters) {
                withTransform({ translate(1f, 1.4f) }) { drawPath(path, Umber, alpha = 0.3f * a) }
                drawPath(path, Ultramarine, alpha = a)
                drawPath(path, art.bristles, alpha = a)
            }
        }
    }

    val pm = step(t, 1950f, 360f, EASE_OUT)
    if (pm > 0f) withTransform({ translate(0f, 8f * (1f - pm)) }) { drawPath(art.small, Cinnabar, alpha = pm * a) }
}

/** Trace la première part [fraction] d'un trait de peinture blanche, avec son ombre et ses poils. */
private fun DrawScope.drawPainted(art: IntroArt, measure: PathMeasure, fraction: Float, pen: Stroke, a: Float) {
    if (fraction <= 0f) return
    val part = Path()
    measure.getSegment(0f, measure.length * fraction, part, true)
    withTransform({ translate(1f, 1.5f) }) { drawPath(part, Umber, alpha = 0.28f * a, style = pen) }
    drawPath(part, LeadWhite, alpha = a, style = pen)
    drawPath(part, art.bristles, alpha = 0.8f * a, style = pen)
}

/**
 * La zone couverte par un coup de brosse droit de [from] à [to], avancé de [p] (0 à 1), de
 * demi-largeur [half] : un ruban aux bords qui tremblent un peu et aux bouts arrondis.
 */
private fun brushRegion(from: Offset, to: Offset, p: Float, half: Float, seed: Int): Path {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = hypot(dx, dy)
    val ux = dx / length
    val uy = dy / length
    val nx = -uy
    val ny = ux
    val reach = length * p
    fun at(u: Float, v: Float) = Offset(from.x + ux * u + nx * v, from.y + uy * u + ny * v)
    val path = Path()
    val gap = 5f
    // Bord du haut, d'arrière en avant.
    var u = 0f
    var n = 0
    val first = at(0f, -half - 2.5f * jitter(seed + n))
    path.moveTo(first.x, first.y)
    while (u < reach) {
        val pt = at(u, -half - 2.5f * jitter(seed + n))
        path.lineTo(pt.x, pt.y)
        u += gap
        n++
    }
    // Le bout avant, arrondi et ébouriffé.
    for (s in 0..8) {
        val angle = -PI.toFloat() / 2 + PI.toFloat() * s / 8
        val r = half * (0.75f + 0.12f * jitter(seed + 500 + s))
        val pt = at(reach + cos(angle) * r, sin(angle) * (half + 1.5f * jitter(seed + 600 + s)))
        path.lineTo(pt.x, pt.y)
    }
    // Bord du bas, d'avant en arrière.
    var back = n
    u = gap * (n - 1)
    while (u >= 0f) {
        val pt = at(u, half + 2.5f * jitter(seed + 300 + back))
        path.lineTo(pt.x, pt.y)
        u -= gap
        back--
    }
    // Le bout arrière, arrondi.
    for (s in 0..8) {
        val angle = PI.toFloat() / 2 + PI.toFloat() * s / 8
        val pt = at(cos(angle) * half * 0.7f, sin(angle) * half)
        path.lineTo(pt.x, pt.y)
    }
    path.close()
    return path
}

/** Le pinceau suit le coup de brosse en cours, avec la couleur qu'il dépose. */
private fun DrawScope.drawBrush(art: IntroArt, t: Float, a: Float) {
    var tip: Offset? = null
    var turn = 0f
    var color = LeadWhite
    BadgeStrokes.forEach { s ->
        val p = step(t, s.start, BADGE_STROKE, DRAW)
        if (p > 0f && p < 1f) {
            tip = Offset(110f + s.from.x + (s.to.x - s.from.x) * p, 10f + s.from.y + (s.to.y - s.from.y) * p)
            turn = if (s.to.x > s.from.x) 28f else -28f
            color = s.color
        }
    }
    val pl = step(t, 800f, 340f, DRAW)
    if (pl > 0f && pl < 1f) {
        tip = art.lMeasure.getPosition(art.lMeasure.length * pl) + Offset(110f, 10f)
        turn = 20f
        color = LeadWhite
    }
    val pi = step(t, 1120f, 170f, EASE_OUT)
    if (pi > 0f && pi < 1f) {
        tip = art.iMeasure.getPosition(art.iMeasure.length * pi) + Offset(110f, 10f)
        turn = 20f
        color = LeadWhite
    }
    val pw = step(t, 1450f, 520f, EASE_IN_OUT)
    if (pw > 0f && pw < 1f) {
        tip = Offset(6f + 328f * pw, 196f)
        turn = 30f
        color = Ultramarine
    }
    val at = tip ?: return
    withTransform({
        translate(at.x, at.y)
        rotate(turn, Offset.Zero)
    }) {
        drawPath(art.handle, Brush.horizontalGradient(0f to Color(0xFF5C2F18), 0.45f to Color(0xFFA5603A), 1f to Color(0xFF4A2512), startX = -4f, endX = 4f), alpha = a)
        drawRect(
            Brush.horizontalGradient(0f to Color(0xFF8D8A85), 0.4f to Color(0xFFF1EFE9), 1f to Color(0xFF6D6A66), startX = -6f, endX = 6f),
            Offset(-6f, -28f), Size(12f, 13f), alpha = a,
        )
        drawPath(art.tip, color, alpha = a)
    }
}

/** Le reflet de vernis qui traverse la toile, à la fin. */
private fun DrawScope.drawVarnish(t: Float, a: Float) {
    val p = step(t, 2250f, 600f, EASE_IN_OUT)
    if (p <= 0f || p >= 1f) return
    val x = -120f + 520f * p
    withTransform({ rotate(18f, Offset(200f, 400f)) }) {
        drawRect(
            Brush.horizontalGradient(0f to Color.Transparent, 0.5f to Color(0x8CFFF8E6), 1f to Color.Transparent, startX = x, endX = x + 110f),
            Offset(x, 250f), Size(110f, 320f), alpha = a,
        )
    }
}

/** Le cartel du musée, sous le tableau. */
private fun DrawScope.drawCartel(t: Float, alpha: Float, measurer: TextMeasurer, medium: String, k: Float) {
    val a = step(t, 2350f, 350f, EASE_OUT) * alpha
    if (a <= 0f) return
    drawRect(Color(0xFFEFE3C6), Offset(110f, 586f), Size(180f, 46f), alpha = a)
    drawRect(Color(0xFFB8913D), Offset(110f, 586f), Size(180f, 46f), alpha = a, style = Stroke(1f))
    // Le texte est mesuré en pixels réels puis replacé dans le repère de la scène.
    val title = measurer.measure(
        "LOUCOUME INTERACTIVE",
        TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.ExtraBold, fontSize = (10.5f * k).toSp(), letterSpacing = (1f * k).toSp(), color = Color(0xFF2A1D14)),
    )
    val line = measurer.measure(
        medium,
        TextStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = (9.5f * k).toSp(), color = Color(0xFF5C4A38)),
    )
    withTransform({ scale(1f / k, 1f / k, pivot = Offset.Zero) }) {
        drawText(title, topLeft = Offset(200f * k - title.size.width / 2f, 596f * k - title.size.height / 2f), alpha = a)
        drawText(line, topLeft = Offset(200f * k - line.size.width / 2f, 617f * k - line.size.height / 2f), alpha = a)
    }
}

// ————— Les textures, fabriquées une fois —————

/** Les stries laissées par les poils de la brosse : traits sombres et clairs, presque horizontaux. */
private fun bristleTexture(): ImageBitmap {
    val w = 256
    val h = 128
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        // Chaque rangée de poils a sa propre teinte, qui ondule doucement le long du trait.
        val base = jitter(y * 7 + 3)
        val phase = jitter(y * 13 + 5) * 6f
        val freq = 0.02f + 0.02f * (jitter(y * 17 + 11) + 1f)
        for (x in 0 until w) {
            val wave = sin(x * freq * 2f * PI.toFloat() / 2f + phase) * 0.35f
            val v = (base * 0.75f + wave).coerceIn(-1f, 1f)
            val alpha = (v.let { it * it } * 120f).roundToInt().coerceIn(0, 110)
            pixels[y * w + x] = if (v < 0f) (alpha shl 24) or 0x1E140C else (alpha shl 24) or 0xFFF8E6
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Le grain de la toile de lin : fils horizontaux et verticaux, un peu irréguliers. */
private fun linenTexture(): ImageBitmap {
    val size = 96
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val warp = jitter(x * 31 + 7) * 0.5f + jitter((x / 3) * 53 + y / 9) * 0.5f
            val weft = jitter(y * 37 + 11) * 0.5f + jitter((y / 3) * 59 + x / 9) * 0.5f
            val v = max(0f, (warp + weft) * 0.5f)
            val alpha = (v.pow(1.5f) * 70f).roundToInt().coerceIn(0, 60)
            pixels[y * size + x] = (alpha shl 24) or 0x735C42
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

// ————— Le son : frottements de brosse et carillon, fabriqués par le code —————

private suspend fun playSoundtrack() = withContext(Dispatchers.Default) {
    runCatching {
        val rate = 44100
        val n = (rate * 3.1f).roundToInt()
        val mix = FloatArray(n)
        // Les frottements : un souffle filtré qui monte puis s'éteint.
        val swishes = listOf(0.25f to 0.26f, 0.40f to 0.26f, 0.55f to 0.26f, 0.80f to 0.34f, 1.12f to 0.17f, 1.45f to 0.52f)
        val gains = listOf(0.09f, 0.09f, 0.09f, 0.06f, 0.05f, 0.07f)
        swishes.forEachIndexed { s, (start, dur) ->
            var low = 0f
            var soft = 0f
            val random = java.util.Random(s.toLong())
            val first = (start * rate).roundToInt()
            val count = (dur * rate).roundToInt()
            for (i in 0 until count) {
                val u = i.toFloat() / count
                val env = if (u < 0.3f) u / 0.3f else (1f - u) / 0.7f
                val noise = random.nextFloat() * 2f - 1f
                // Passe-bande simple : le grave est retiré, puis l'aigu adouci.
                low += 0.25f * (noise - low)
                soft += 0.55f * ((noise - low) - soft)
                if (first + i < n) mix[first + i] += soft * env * gains[s] * 2.2f
            }
        }
        // Le carillon, quand la goutte se pose : trois notes cristallines.
        val chime = 1.51f
        val notes = listOf(Triple(1318.5f, 0f, 0.11f), Triple(1975.5f, 0.09f, 0.08f), Triple(2637f, 0.18f, 0.04f))
        for (i in 0 until n) {
            val time = i.toFloat() / rate - chime
            for ((f, start, peak) in notes) {
                val u = time - start
                if (u < 0f || u > 1.5f) continue
                val env = if (u < 0.012f) peak * u / 0.012f else peak * exp(ln(0.0001f / peak) * ((u - 0.012f) / 1.388f).coerceAtMost(1f))
                mix[i] += sin(2 * PI * f * u).toFloat() * env * 1.6f
            }
        }
        val pcm = ShortArray(n) { (mix[it] * Short.MAX_VALUE).roundToInt().coerceIn(-32767, 32767).toShort() }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(n * 2)
            .build()
        try {
            track.write(pcm, 0, n)
            track.play()
            delay(3200)
        } finally {
            // Un toucher qui passe l'intro coupe aussi le son.
            track.release()
        }
    }
}
