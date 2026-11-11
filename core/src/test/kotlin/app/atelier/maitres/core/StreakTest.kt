package app.atelier.maitres.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreakTest {
    private fun paintDays(vararg days: Long) = days.fold(Streak()) { s, d -> s.paint(d) }

    @Test
    fun `peindre deux fois le même jour ne compte qu'une fois`() {
        assertEquals(1, paintDays(10, 10).days)
    }

    @Test
    fun `un jour oublié sans grâce remet la série à un`() {
        val s = paintDays(10, 11, 12, 14)
        assertEquals(1, s.days)
    }

    @Test
    fun `sept jours d'affilée donnent un jour de grâce`() {
        val s = paintDays(1, 2, 3, 4, 5, 6, 7)
        assertEquals(7, s.days)
        assertEquals(1, s.graces)
    }

    @Test
    fun `le jour de grâce pardonne un oubli puis se regagne`() {
        var s = paintDays(1, 2, 3, 4, 5, 6, 7)
        assertTrue(s.isAlive(9))
        assertTrue(s.needsGrace(9))
        s = s.paint(9)
        assertEquals(8, s.days)
        assertEquals(0, s.graces)
        assertFalse(s.isAlive(11))
        for (d in 10L..16L) s = s.paint(d)
        assertEquals(1, s.graces)
    }

    @Test
    fun `deux jours oubliés cassent la série même avec la grâce`() {
        val s = paintDays(1, 2, 3, 4, 5, 6, 7, 10)
        assertEquals(1, s.days)
        assertEquals(1, s.graces)
    }

    @Test
    fun `on ne garde qu'un jour de grâce`() {
        var s = Streak()
        for (d in 1L..21L) s = s.paint(d)
        assertEquals(1, s.graces)
    }

    @Test
    fun `affichage à zéro quand la série est perdue`() {
        val s = paintDays(1, 2)
        assertEquals(2, s.current(3))
        assertEquals(0, s.current(4))
    }
}
