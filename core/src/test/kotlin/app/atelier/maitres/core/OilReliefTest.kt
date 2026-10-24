package app.atelier.maitres.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OilReliefTest {

    /** Des rayures verticales : les coups de pinceau doivent les suivre, de haut en bas. */
    private fun stripes(w: Int, h: Int) = IntArray(w * h) { i -> if ((i % w) / 6 % 2 == 0) 0xFF3050A0.toInt() else 0xFFE0C060.toInt() }

    @Test
    fun `le relief se relit a l'identique`() {
        val relief = OilRelief.compute(stripes(48, 32), 48, 32)
        val bytes = ByteArrayOutputStream().also { relief.write(it) }.toByteArray()
        val back = OilRelief.read(ByteArrayInputStream(bytes))
        assertEquals(48, back.width)
        assertEquals(32, back.height)
        assertContentEquals(relief.shade, back.shade)
        assertContentEquals(relief.gloss, back.gloss)
    }

    @Test
    fun `une meme image donne toujours le meme relief`() {
        val a = OilRelief.compute(stripes(40, 40), 40, 40)
        val b = OilRelief.compute(stripes(40, 40), 40, 40)
        assertContentEquals(a.shade, b.shade)
    }

    @Test
    fun `la matiere module la couleur sans la changer de teinte`() {
        val relief = OilRelief.compute(stripes(60, 60), 60, 60, strength = 1f)
        var lighter = 0
        var darker = 0
        for (p in 0 until 60 * 60) {
            val c = relief.apply(p, 0xFF806040.toInt())
            val r = (c shr 16) and 0xFF
            if (r > 0x80) lighter++ else if (r < 0x80) darker++
            assertEquals(0xFF, c ushr 24)
        }
        // Des crêtes éclairées et des creux dans l'ombre.
        assertTrue(lighter > 100 && darker > 100, "lumière $lighter, ombre $darker")
    }
}
