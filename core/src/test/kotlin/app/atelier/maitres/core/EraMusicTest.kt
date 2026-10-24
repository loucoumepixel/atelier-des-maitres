package app.atelier.maitres.core

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class EraMusicTest {

    @Test
    fun `chaque epoque a sa boucle, au meme volume et sans saturer`() {
        for (era in Era.entries) {
            val pcm = EraMusic.render(era)
            assertTrue(pcm.size > 25 * EraMusic.RATE, "$era : au moins 25 secondes")
            var energy = 0.0
            var peak = 0
            for (s in pcm) { energy += s.toDouble() * s; peak = maxOf(peak, abs(s.toInt())) }
            val rms = sqrt(energy / pcm.size) / Short.MAX_VALUE
            assertTrue(rms in 0.02..0.035, "$era : volume moyen $rms")
            assertTrue(peak < 32000, "$era : crête $peak")
        }
    }

    @Test
    fun `deux epoques ne jouent pas la meme musique`() {
        val a = EraMusic.render(Era.JAPON)
        val b = EraMusic.render(Era.IMPRESSIONNISME)
        var diff = 0L
        for (i in 0 until minOf(a.size, b.size)) diff += abs(a[i] - b[i])
        assertTrue(diff > 0)
    }
}
