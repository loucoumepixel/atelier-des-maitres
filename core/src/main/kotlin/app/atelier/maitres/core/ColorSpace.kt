package app.atelier.maitres.core

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Conversions sRGB <-> CIE L*a*b* (illuminant D65).
 *
 * Le découpage travaille en Lab plutôt qu'en RGB : les distances y suivent la
 * perception humaine, ce qui donne des zones qui « ressemblent » à la toile au
 * lieu de zones qui suivent les artefacts de compression.
 */
object ColorSpace {

    private val srgbToLinear = FloatArray(256) { i ->
        val c = i / 255f
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }

    private const val XN = 0.95047f
    private const val YN = 1.00000f
    private const val ZN = 1.08883f

    /** Écrit L, a, b de la couleur [argb] dans [out] à partir de [offset]. */
    fun argbToLab(argb: Int, out: FloatArray, offset: Int = 0) {
        val r = srgbToLinear[(argb shr 16) and 0xFF]
        val g = srgbToLinear[(argb shr 8) and 0xFF]
        val b = srgbToLinear[argb and 0xFF]
        val x = (0.4124564f * r + 0.3575761f * g + 0.1804375f * b) / XN
        val y = (0.2126729f * r + 0.7151522f * g + 0.0721750f * b) / YN
        val z = (0.0193339f * r + 0.1191920f * g + 0.9503041f * b) / ZN
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        out[offset] = 116f * fy - 16f
        out[offset + 1] = 500f * (fx - fy)
        out[offset + 2] = 200f * (fy - fz)
    }

    fun labToArgb(l: Float, a: Float, b: Float): Int {
        val fy = (l + 16f) / 116f
        val fx = fy + a / 500f
        val fz = fy - b / 200f
        val x = fInv(fx) * XN
        val y = fInv(fy) * YN
        val z = fInv(fz) * ZN
        val rl = 3.2404542f * x - 1.5371385f * y - 0.4985314f * z
        val gl = -0.9692660f * x + 1.8760108f * y + 0.0415560f * z
        val bl = 0.0556434f * x - 0.2040259f * y + 1.0572252f * z
        return (0xFF shl 24) or (encode(rl) shl 16) or (encode(gl) shl 8) or encode(bl)
    }

    /** Distance perceptuelle ΔE76 entre deux couleurs ARGB. */
    fun deltaE(argb1: Int, argb2: Int): Float {
        val p = FloatArray(6)
        argbToLab(argb1, p, 0)
        argbToLab(argb2, p, 3)
        val dl = p[0] - p[3]
        val da = p[1] - p[4]
        val db = p[2] - p[5]
        return sqrt(dl * dl + da * da + db * db)
    }

    /** Luminance relative (0..1), utile pour choisir un texte clair ou foncé. */
    fun luminance(argb: Int): Float =
        0.2126f * srgbToLinear[(argb shr 16) and 0xFF] +
            0.7152f * srgbToLinear[(argb shr 8) and 0xFF] +
            0.0722f * srgbToLinear[argb and 0xFF]

    private fun f(t: Float): Float = if (t > 0.008856f) cbrt(t) else 7.787f * t + 16f / 116f

    private fun fInv(t: Float): Float {
        val t3 = t * t * t
        return if (t3 > 0.008856f) t3 else (t - 16f / 116f) / 7.787f
    }

    private fun encode(linear: Float): Int {
        val c = linear.coerceIn(0f, 1f)
        val s = if (c <= 0.0031308f) 12.92f * c else 1.055f * c.pow(1f / 2.4f) - 0.055f
        return (s * 255f).roundToInt().coerceIn(0, 255)
    }
}
