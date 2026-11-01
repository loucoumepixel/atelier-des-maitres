package app.atelier.maitres.ui.studio

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * L'atelier où l'on peint : un mur de plâtre, la toile accrochée dessus, la palette en bois.
 * Le jour, la lumière d'une fenêtre entre par la gauche ; le soir (thème sombre), une lampe
 * chaude éclaire le mur par le haut.
 */
@Immutable
data class AtelierLook(
    val evening: Boolean,
    val wall: Color,
    val ink: Color,
    val meta: Color,
    val button: Color,
    /** Liseré des boutons ronds : ils restent nets sur le mur comme sur une toile zoomée. */
    val buttonEdge: Color,
    val buttonShadow: Boolean,
    val progressTrack: Color,
    val progress: Color,
    val woodTop: Color,
    val woodBottom: Color,
    val mark: Color,
    val canvasShadow: Color,
    /** Décalage horizontal de l'ombre de la toile : la fenêtre est à gauche, la lampe à droite. */
    val shadowDx: Float,
)

private val Day = AtelierLook(
    evening = false,
    wall = Color(0xFFEFE3CF),
    ink = Color(0xFF2B1B12),
    meta = Color(0xFF8A6A4C),
    button = Color(0xFFFFFCF5),
    buttonEdge = Color(0x592B1B12),
    buttonShadow = true,
    progressTrack = Color(0x1F2B1B12),
    progress = Color(0xFFC8553D),
    woodTop = Color(0xFFD8AE78),
    woodBottom = Color(0xFFC4925A),
    mark = Color(0xFF2B1B12),
    canvasShadow = Color(0x4D50321A),
    shadowDx = 12f,
)

private val Evening = AtelierLook(
    evening = true,
    wall = Color(0xFF2E2119),
    ink = Color(0xFFF4E6D2),
    meta = Color(0xFFC9A982),
    // Opaque : à 10 % les boutons disparaissaient dès que la toile zoomée passait derrière.
    button = Color(0xF0241811),
    buttonEdge = Color(0x80F4E6D2),
    buttonShadow = false,
    progressTrack = Color(0x24F4E6D2),
    progress = Color(0xFFE9A94F),
    woodTop = Color(0xFF8E6238),
    woodBottom = Color(0xFF6E4826),
    mark = Color(0xFFE9A94F),
    canvasShadow = Color(0x80000000),
    shadowDx = -10f,
)

@Composable
fun atelierLook(): AtelierLook = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Evening else Day

/** Le mur : la couleur du plâtre, son grain, puis la lumière. */
fun Modifier.atelierWall(look: AtelierLook) = drawBehind {
    drawRect(look.wall)
    drawRect(Textures.plaster, alpha = 0.55f, blendMode = BlendMode.Multiply)
    if (look.evening) {
        ellipse(listOf(0f to Color(0x59FFC478), 0.7f to Color.Transparent), 0.7f, 0.18f, 0.75f, 0.5f)
        ellipse(listOf(0.4f to Color.Transparent, 1f to Color(0x73000000)), 0.5f, 0.45f, 1.2f, 0.8f)
    } else {
        // Les rais de la fenêtre, en biais, comme sur un vrai mur l'après-midi.
        val dx = 0.906f
        val dy = 0.423f
        val half = (size.width * dx + size.height * dy) / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawRect(
            Brush.linearGradient(
                0f to Color(0xD9FFF8E6), 0.18f to Color(0xD9FFF8E6), 0.18f to Color.Transparent,
                0.30f to Color.Transparent, 0.30f to Color(0x80FFF8E6), 0.44f to Color(0x80FFF8E6),
                0.60f to Color.Transparent,
                start = c - Offset(dx, dy) * half,
                end = c + Offset(dx, dy) * half,
            ),
        )
        ellipse(listOf(0f to Color(0xB3FFF4D6), 0.7f to Color.Transparent), 0.2f, 0.1f, 0.9f, 0.6f)
        drawRect(Brush.verticalGradient(0.7f to Color.Transparent, 1f to Color(0x1F785028)))
    }
}

/** Un dégradé radial en ellipse, centre et rayons en fractions de la surface (comme en CSS). */
private fun DrawScope.ellipse(stops: List<Pair<Float, Color>>, cx: Float, cy: Float, rx: Float, ry: Float) {
    val center = Offset(size.width * cx, size.height * cy)
    val radius = size.width * rx
    withTransform({ scale(1f, size.height * ry / radius, pivot = center) }) {
        drawCircle(Brush.radialGradient(*stops.toTypedArray(), center = center, radius = radius), radius * 3, center)
    }
}

/** Grains calculés une fois : plâtre du mur, lin de la toile, fil du bois. */
object Textures {
    val plaster: ShaderBrush by lazy { tile(noise(220, 220, 1f, 1f, seed = 4) { v -> argb(v * 0.22f, 140, 115, 84) }) }

    /** Fils fins horizontaux et verticaux, à peine visibles : opaque, à poser en « multiplier ». */
    val linen: ShaderBrush by lazy {
        val n = 96
        val random = Random(7)
        val rows = FloatArray(n) { random.nextFloat() }
        val cols = FloatArray(n) { random.nextFloat() }
        val pixels = IntArray(n * n) { k ->
            val v = 255 - (18 * (rows[k / n] * 0.6f + cols[k % n] * 0.4f) + 10 * random.nextFloat()).roundToInt()
            (0xFF shl 24) or (v shl 16) or ((v - 2) shl 8) or (v - 6)
        }
        tile(Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888))
    }

    /** Veines étirées dans la longueur de la planche. */
    val wood: ShaderBrush by lazy { tile(noise(600, 120, 80f, 3.5f, seed = 8) { v -> argb(v * 0.5f, 140, 92, 46) }) }

    private fun tile(bitmap: Bitmap) =
        ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))

    private fun argb(alpha: Float, r: Int, g: Int, b: Int) =
        ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b

    /**
     * Bruit de valeur sur deux octaves, qui se raccorde sur ses bords pour être répété.
     * [cellX] et [cellY] donnent la taille des grains en pixels.
     */
    private fun noise(w: Int, h: Int, cellX: Float, cellY: Float, seed: Int, color: (Float) -> Int): Bitmap {
        val random = Random(seed)
        fun lattice(cx: Int, cy: Int) = Array(cy) { FloatArray(cx) { random.nextFloat() } }
        val octaves = listOf(1f, 0.5f).map { scale ->
            val cx = maxOf(1, (w / (cellX * scale)).roundToInt())
            val cy = maxOf(1, (h / (cellY * scale)).roundToInt())
            Triple(cx, cy, lattice(cx, cy))
        }
        val pixels = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var v = 0f
            var weight = 0f
            octaves.forEachIndexed { i, (cx, cy, grid) ->
                val fx = x.toFloat() / w * cx
                val fy = y.toFloat() / h * cy
                val x0 = floor(fx).toInt()
                val y0 = floor(fy).toInt()
                val tx = smooth(fx - x0)
                val ty = smooth(fy - y0)
                val a = grid[y0 % cy][x0 % cx]
                val b = grid[y0 % cy][(x0 + 1) % cx]
                val c = grid[(y0 + 1) % cy][x0 % cx]
                val d = grid[(y0 + 1) % cy][(x0 + 1) % cx]
                val k = if (i == 0) 0.65f else 0.35f
                v += k * (a + (b - a) * tx + (c - a + (d - c - b + a) * tx) * ty)
                weight += k
            }
            pixels[y * w + x] = color(v / weight)
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)
}
