package app.atelier.maitres.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColourMigrationTest {

    private val w = 90
    private val h = 60

    /** Trois bandes mouchetées : leurs moyennes sont plus ternes que leurs touches. */
    private val pixels = Random(3).let { rnd ->
        IntArray(w * h) {
            val band = (it % w) / 30
            val base = intArrayOf(0xB5482E, 0x3A7D44, 0x2E5E9E)[band]
            val n = rnd.nextInt(-18, 18)
            fun ch(v: Int) = (v + n).coerceIn(0, 255)
            (0xFF shl 24) or (ch(base shr 16 and 0xFF) shl 16) or (ch(base shr 8 and 0xFF) shl 8) or ch(base and 0xFF)
        }
    }
    private val params = SegmentationParams(colorCount = 6, minRegionArea = 60)

    @Test
    fun `les touches d'avant les couleurs ravivees redeviennent justes`() {
        val legacy = Segmenter(ColourMigration.legacyParams(params)).segment(pixels, w, h)
        val vivid = Segmenter(params).segment(pixels, w, h)
        // Toute la toile peinte juste avec l'ancienne palette.
        val fills = IntArray(legacy.regionCount) { legacy.referenceColor(it) }
        assertTrue(ColourMigration.hasForeignColours(vivid, fills))

        val carried = ColourMigration.carryOver(legacy, vivid, fills)!!
        assertEquals(vivid.regionCount, carried.indices.count { carried[it] == vivid.referenceColor(it) })
        assertTrue(!ColourMigration.hasForeignColours(vivid, carried))
    }

    @Test
    fun `une zone vierge ou d'une couleur libre reste telle quelle`() {
        val legacy = Segmenter(ColourMigration.legacyParams(params)).segment(pixels, w, h)
        val vivid = Segmenter(params).segment(pixels, w, h)
        val custom = 0xFF123456.toInt()
        val fills = IntArray(legacy.regionCount) { if (it == 0) custom else PaintingSession.EMPTY }
        val carried = ColourMigration.carryOver(legacy, vivid, fills)!!
        assertEquals(custom, carried[0])
        assertTrue((1 until carried.size).all { carried[it] == PaintingSession.EMPTY })
    }
}
