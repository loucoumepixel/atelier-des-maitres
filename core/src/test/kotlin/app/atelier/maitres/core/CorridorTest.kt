package app.atelier.maitres.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CorridorTest {

    private val corridor = Corridor(count = 6, width = 1080f, height = 2220f)

    private fun quad(i: Int, camera: Float): FloatArray? {
        val out = FloatArray(8)
        return out.takeIf { corridor.project(corridor.corners(i, 0.8f), camera, it) }
    }

    @Test
    fun `les toiles alternent de mur et s'eloignent`() {
        val left = quad(0, 0f)!!
        val right = quad(1, 0f)!!
        assertTrue(left.indices.filter { it % 2 == 0 }.all { left[it] < corridor.centerX }, "à gauche")
        assertTrue(right.indices.filter { it % 2 == 0 }.all { right[it] > corridor.centerX }, "à droite")
        // Plus loin, plus petite : la hauteur à l'écran de la toile 4 est moindre que celle de la toile 0.
        val far = quad(4, 0f)!!
        assertTrue(far[7] - far[1] < left[7] - left[1])
    }

    @Test
    fun `une toile derriere le visiteur n'est pas dessinee`() {
        assertTrue(quad(0, 0f) != null)
        assertTrue(quad(0, corridor.hangAt(0) + 1f) == null)
    }

    @Test
    fun `le toucher reconnait la toile`() {
        val q = quad(1, 0f)!!
        val cx = (q[0] + q[2] + q[4] + q[6]) / 4f
        val cy = (q[1] + q[3] + q[5] + q[7]) / 4f
        assertTrue(Corridor.contains(q, cx, cy))
        assertFalse(Corridor.contains(q, corridor.centerX, cy))
    }

    @Test
    fun `on peut avancer jusqu'a la derniere toile`() {
        assertTrue(corridor.maxCamera > 0f && corridor.maxCamera < corridor.hangAt(5))
        assertTrue(Corridor(0, 100f, 200f).maxCamera == 0f)
    }
}
