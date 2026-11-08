package app.atelier.maitres.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MuseumAmbienceTest {
    @Test
    fun `boucle de 23 secondes, discrète et sans saut au raccord`() {
        val pcm = MuseumAmbience.render()
        assertEquals(EraMusic.RATE * (MuseumAmbience.SECONDS - 1), pcm.size)
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue(peak in 1000..16500, "crête $peak")
        val jump = abs(pcm.last() - pcm.first())
        assertTrue(jump < 2000, "saut au raccord $jump")
    }
}
