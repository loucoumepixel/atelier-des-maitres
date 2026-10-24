package app.atelier.maitres.core

import kotlin.math.max
import kotlin.math.min

/**
 * La galerie du musée, vue en perspective : un long couloir où les toiles sont accrochées
 * en quinconce, une à gauche, une à droite, de plus en plus loin.
 *
 * Unités du monde : le couloir fait 2 de large (x de -1 à 1), le sol est en y = [FLOOR] et le
 * plafond en y = [CEILING] ; on regarde vers les z croissants, depuis [camera].
 * [project] ramène un point du monde à l'écran ([width] × [height] pixels).
 */
class Corridor(val count: Int, val width: Float, val height: Float) {
    /** Distance focale en pixels : un angle de vue proche de celui de l'œil. */
    val focal = width * 0.92f
    val centerX = width / 2f
    val centerY = height * 0.41f

    /** Profondeur du couloir, mur du fond compris. */
    val length: Float = (if (count == 0) START else hangAt(count - 1)) + END_ROOM

    /** Le plus loin où l'on peut avancer : devant la dernière toile. */
    val maxCamera: Float = max(0f, (if (count == 0) 0f else hangAt(count - 1)) - 1.6f)

    /** Mur où pend la toile [i] : -1 à gauche, 1 à droite. */
    fun side(i: Int): Int = if (i % 2 == 0) -1 else 1

    /** Profondeur du centre de la toile [i] : une travée par paire, la droite décalée d'une demi-travée. */
    fun hangAt(i: Int): Float = START + (i / 2) * SPACING + if (i % 2 == 1) SPACING / 2f else 0f

    /**
     * Les quatre coins de l'encadrement d'une toile de proportions [aspect] (largeur / hauteur),
     * dans l'ordre haut-gauche, haut-droite, bas-droite, bas-gauche tels que les voit quelqu'un
     * face au mur ; chaque coin est (x, y, z). [frame] ajoute la bordure, en fraction de la hauteur.
     */
    fun corners(i: Int, aspect: Float, frame: Float = 0f): FloatArray {
        val h = PAINTING_HEIGHT * min(1f, MAX_WIDTH / (PAINTING_HEIGHT * aspect))
        val w = h * aspect
        val half = w / 2f + h * frame
        val top = PAINTING_CENTER + h / 2f + h * frame
        val bottom = PAINTING_CENTER - h / 2f - h * frame
        val x = side(i) * WALL
        val z = hangAt(i)
        // Face au mur de gauche, la droite du visiteur est vers le fond ; face au mur de droite, vers l'entrée.
        val left = if (side(i) < 0) z - half else z + half
        val right = if (side(i) < 0) z + half else z - half
        return floatArrayOf(
            x, top, left,
            x, top, right,
            x, bottom, right,
            x, bottom, left,
        )
    }

    /**
     * Projette les points (x, y, z) de [world] à l'écran depuis [camera], dans [out] (x, y par
     * point). Faux si l'un d'eux est derrière l'œil ou trop près pour être dessiné.
     */
    fun project(world: FloatArray, camera: Float, out: FloatArray): Boolean {
        var visible = true
        for (k in 0 until world.size / 3) {
            val depth = world[3 * k + 2] - camera
            if (depth < NEAR) visible = false
            val d = max(depth, NEAR)
            out[2 * k] = centerX + world[3 * k] * focal / d
            out[2 * k + 1] = centerY - world[3 * k + 1] * focal / d
        }
        return visible
    }

    companion object {
        const val WALL = 1f
        const val FLOOR = -1.12f
        const val CEILING = 1.42f
        const val START = 2.1f
        const val SPACING = 2.9f
        const val END_ROOM = 2.4f
        const val NEAR = 0.3f
        const val PAINTING_HEIGHT = 0.92f
        const val MAX_WIDTH = 1.45f
        const val PAINTING_CENTER = 0.34f

        /** Vrai si (x, y) est dans le quadrilatère convexe [quad] (quatre points x, y). */
        fun contains(quad: FloatArray, x: Float, y: Float): Boolean {
            var sign = 0
            for (k in 0 until 4) {
                val ax = quad[2 * k]; val ay = quad[2 * k + 1]
                val bx = quad[(2 * k + 2) % 8]; val by = quad[(2 * k + 3) % 8]
                val cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
                val s = if (cross > 0f) 1 else if (cross < 0f) -1 else 0
                if (s == 0) continue
                if (sign == 0) sign = s else if (s != sign) return false
            }
            return true
        }
    }
}
