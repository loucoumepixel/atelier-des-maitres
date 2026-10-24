package app.atelier.maitres.core

import kotlin.test.Test
import kotlin.test.assertTrue

class MouldingTest {

    @Test
    fun `la baguette du haut est plus claire que celle du bas`() {
        val gold = 0xFFC79942.toInt()
        val top = Moulding.colors(gold, 0, gilt = true)
        val bottom = Moulding.colors(gold, 3, gilt = true)
        val lum = { c: Int -> ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF) }
        assertTrue(top.sumOf(lum) > bottom.sumOf(lum))
    }

    @Test
    fun `le tore accroche la lumiere et la gorge reste dans l'ombre`() {
        assertTrue(Moulding.profile(0.22f) > Moulding.profile(0.6f))
        assertTrue(Moulding.profile(0.8f) > Moulding.profile(0.95f))
    }
}
