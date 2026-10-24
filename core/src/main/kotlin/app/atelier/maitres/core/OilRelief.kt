package app.atelier.maitres.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * La matière de la peinture à l'huile : un relief de coups de pinceau, éclairé d'en haut à
 * gauche, que l'on pose sur la couleur des zones peintes.
 *
 * 1. Le sens des coups de pinceau est lu dans l'original : le tenseur de structure (lissé)
 *    donne en chaque point la direction des contours, celle que suivait le pinceau du maître.
 * 2. Un bruit fin est étiré le long de ce sens (convolution intégrale de ligne) : on obtient
 *    des traînées de poils, comme une pâte tirée au pinceau.
 * 3. Ce relief, plus un peu des formes de l'original, est éclairé : chaque pixel reçoit un
 *    facteur d'ombre ([shade], en centièmes autour de 100) et un reflet ([gloss], 0 à 255).
 *
 * Le calcul ne dépend que de l'image : il se fait une fois à l'ouverture de la toile.
 */
class OilRelief(
    val width: Int,
    val height: Int,
    /** Ombre et lumière de la pâte, en centièmes autour de 100 (de 50 à 150). */
    val shade: ByteArray,
    /** Reflet brillant de l'huile sur les crêtes, de 0 à 255. */
    val gloss: ByteArray,
) {
    init {
        require(shade.size == width * height && gloss.size == width * height)
    }

    /**
     * Applique la matière au pixel [p] d'une couleur ARGB posée, avec la force [percent]
     * (0 : peinture plate, 100 : la pâte la plus épaisse).
     */
    fun apply(p: Int, argb: Int, percent: Int = 100): Int {
        if (percent <= 0) return argb
        val s = 100 + shade[p] * percent / 100
        val g = (gloss[p].toInt() and 0xFF) * percent / 100
        val r = min(255, ((argb shr 16) and 0xFF) * s / 100 + g)
        val gr = min(255, ((argb shr 8) and 0xFF) * s / 100 + g)
        val b = min(255, (argb and 0xFF) * s / 100 + g)
        return (0xFF shl 24) or (r shl 16) or (gr shl 8) or b
    }

    /** Écrit le relief (pour le cache sur disque). */
    fun write(out: java.io.OutputStream) {
        val data = java.io.DataOutputStream(out)
        data.writeInt(MAGIC); data.writeInt(width); data.writeInt(height)
        data.write(shade); data.write(gloss)
        data.flush()
    }

    companion object {
        private const val MAGIC = 0x4F494C31 // "OIL1"

        /** Relit un relief écrit par [write]. */
        fun read(input: java.io.InputStream): OilRelief {
            val data = java.io.DataInputStream(input.buffered())
            require(data.readInt() == MAGIC) { "relief illisible" }
            val w = data.readInt()
            val h = data.readInt()
            require(w in 1..8192 && h in 1..8192)
            val shade = ByteArray(w * h).also { data.readFully(it) }
            val gloss = ByteArray(w * h).also { data.readFully(it) }
            return OilRelief(w, h, shade, gloss)
        }

        private const val TENSOR_RADIUS = 7
        private const val STEPS = 14
        private const val STREAKS = 0.5f
        private const val FORMS = 0.5f
        private const val BUMP = 1.28f
        private const val SHADE = 0.7f
        private const val GLOSS = 0.16f

        /**
         * Calcule le relief à partir de l'image d'origine [argb] (ligne par ligne), à la force
         * [strength] (1 : la pâte la plus épaisse ; [apply] la dose ensuite au goût du joueur).
         */
        fun compute(argb: IntArray, w: Int, h: Int, strength: Float = 1f, seed: Long = 3L): OilRelief {
            val n = w * h
            require(argb.size == n && n > 0)
            val lum = FloatArray(n) { i ->
                val c = argb[i]
                (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f
            }

            // 1. Sens des coups de pinceau.
            val gx = FloatArray(n)
            val gy = FloatArray(n)
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                if (x in 1 until w - 1) gx[i] = lum[i + 1] - lum[i - 1]
                if (y in 1 until h - 1) gy[i] = lum[i + w] - lum[i - w]
            }
            val jxx = blur(FloatArray(n) { gx[it] * gx[it] }, w, h, TENSOR_RADIUS)
            val jyy = blur(FloatArray(n) { gy[it] * gy[it] }, w, h, TENSOR_RADIUS)
            val jxy = blur(FloatArray(n) { gx[it] * gy[it] }, w, h, TENSOR_RADIUS)
            val dx = FloatArray(n)
            val dy = FloatArray(n)
            for (i in 0 until n) {
                // Le long des contours : perpendiculaire au gradient dominant.
                val angle = 0.5f * atan2(2f * jxy[i], jxx[i] - jyy[i]) + (Math.PI / 2).toFloat()
                dx[i] = cos(angle)
                dy[i] = sin(angle)
            }

            // 2. Traînées de poils le long de ce sens.
            val random = Random(seed)
            val noise = blur(FloatArray(n) { random.nextFloat() }, w, h, 1)
            val lic = FloatArray(n)
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                var acc = noise[i]
                for (pass in 0..1) {
                    val sign = if (pass == 0) 1f else -1f
                    var px = x.toFloat()
                    var py = y.toFloat()
                    var cx = dx[i]
                    var cy = dy[i]
                    for (s in 0 until STEPS) {
                        px = (px + sign * cx).coerceIn(0f, (w - 1).toFloat())
                        py = (py + sign * cy).coerceIn(0f, (h - 1).toFloat())
                        val j = py.toInt() * w + px.toInt()
                        acc += noise[j]
                        // Les directions n'ont pas de sens : on garde celle qui prolonge le trait.
                        val nx = dx[j]
                        val ny = dy[j]
                        if (nx * cx + ny * cy < 0f) { cx = -nx; cy = -ny } else { cx = nx; cy = ny }
                    }
                }
                lic[i] = acc / (2 * STEPS + 1)
            }
            var mean = 0.0
            for (v in lic) mean += v
            mean /= n
            var variance = 0.0
            for (v in lic) variance += (v - mean) * (v - mean)
            val std = sqrt(variance / n).toFloat() + 1e-6f

            // 3. Relief éclairé.
            val forms = blur(lum, w, h, 1)
            val height = FloatArray(n) { STREAKS * (lic[it] - mean.toFloat()) / std + FORMS * forms[it] }
            var lx = -0.5f; var ly = -0.6f; var lz = 0.62f
            val ln = sqrt(lx * lx + ly * ly + lz * lz)
            lx /= ln; ly /= ln; lz /= ln
            val shade = ByteArray(n)
            val gloss = ByteArray(n)
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                val nx = if (x in 1 until w - 1) (height[i + 1] - height[i - 1]) * BUMP else 0f
                val ny = if (y in 1 until h - 1) (height[i + w] - height[i - w]) * BUMP else 0f
                val len = sqrt(nx * nx + ny * ny + 1f)
                val dot = (-nx * lx - ny * ly + lz) / len
                val s = (1f + (dot - lz) * SHADE * strength).coerceIn(0.5f, 1.5f)
                shade[i] = ((s - 1f) * 100f).toInt().toByte()
                val g = max(0f, dot - 0.93f) * 6f * GLOSS * strength
                gloss[i] = (min(1f, g) * 255f).toInt().toByte()
            }
            return OilRelief(w, h, shade, gloss)
        }

        /** Flou boîte séparable, trois passes (proche d'un flou gaussien). */
        private fun blur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
            var a = src
            val tmp = FloatArray(src.size)
            val k = 2 * r + 1
            repeat(3) {
                val out = FloatArray(src.size)
                for (y in 0 until h) {
                    val row = y * w
                    var sum = 0f
                    for (d in -r..r) sum += a[row + d.coerceIn(0, w - 1)]
                    for (x in 0 until w) {
                        tmp[row + x] = sum / k
                        sum += a[row + (x + r + 1).coerceAtMost(w - 1)] - a[row + (x - r).coerceAtLeast(0)]
                    }
                }
                for (x in 0 until w) {
                    var sum = 0f
                    for (d in -r..r) sum += tmp[d.coerceIn(0, h - 1) * w + x]
                    for (y in 0 until h) {
                        out[y * w + x] = sum / k
                        sum += tmp[(y + r + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - r).coerceAtLeast(0) * w + x]
                    }
                }
                a = out
            }
            return a
        }
    }
}
