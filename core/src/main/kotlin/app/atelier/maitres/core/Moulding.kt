package app.atelier.maitres.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * La moulure d'un cadre de musée, vue en coupe : un grand tore bombé à l'extérieur, une gorge
 * creuse, une fine baguette, puis un filet qui descend vers la toile. Éclairée d'en haut à
 * gauche, chaque baguette du cadre a sa propre lumière.
 */
object Moulding {
    /** Lumière reçue par chaque baguette : haut, gauche, droite, bas. */
    val SIDE_LIGHT = floatArrayOf(1.18f, 1.05f, 0.80f, 0.68f)

    /** Clarté de la moulure à la position [t], de l'extérieur (0) vers la toile (1). */
    fun profile(t: Float): Float = when {
        t < 0.05f -> 0.35f
        t <= 0.45f -> 0.55f + 0.45f * sin(min(1f, t / 0.45f) * PI.toFloat())
        t <= 0.75f -> 0.45f + 0.25f * cos((t - 0.45f) / 0.3f * PI.toFloat())
        t < 0.86f -> 1.05f
        else -> 0.5f + 0.2f * (1f - (t - 0.86f) / 0.14f)
    }

    /**
     * Les couleurs ARGB d'une baguette ([side] : 0 haut, 1 gauche, 2 droite, 3 bas) en [count]
     * points de l'extérieur vers la toile. Une dorure ([gilt]) prend en plus un reflet sur les bosses.
     */
    fun colors(base: Int, side: Int, gilt: Boolean, count: Int = 25): IntArray = IntArray(count) { k ->
        val t = k / (count - 1f)
        val p = profile(t) * SIDE_LIGHT[side]
        val spec = if (gilt) ((p - 0.95f) * 3f).coerceIn(0f, 1f) else 0f
        val r = ((base shr 16) and 0xFF) / 255f * p + spec * 0.35f
        val g = ((base shr 8) and 0xFF) / 255f * p + spec * 0.30f
        val b = (base and 0xFF) / 255f * p + spec * 0.18f
        (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    private fun channel(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
}
