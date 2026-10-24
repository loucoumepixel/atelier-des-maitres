package app.atelier.maitres.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SegmenterTest {

    private val red = 0xFFC0392B.toInt()
    private val blue = 0xFF2E86C1.toInt()
    private val yellow = 0xFFF4D03F.toInt()

    private fun image(w: Int, h: Int, pixel: (Int, Int) -> Int) = IntArray(w * h) { pixel(it % w, it / w) }

    @Test
    fun `deux aplats donnent deux zones de deux couleurs`() {
        val pbn = Segmenter(SegmentationParams(colorCount = 4, minRegionArea = 50, vividness = 1f, contrast = 1f))
            .segment(image(80, 40) { x, _ -> if (x < 40) red else blue }, 80, 40)

        assertEquals(2, pbn.regionCount)
        assertEquals(2, pbn.palette.size)
        assertTrue(pbn.regionAt(5, 5) != pbn.regionAt(75, 5))
        assertTrue(ColorSpace.deltaE(pbn.referenceColor(pbn.regionAt(5, 5)), red) < 3f)
        assertTrue(ColorSpace.deltaE(pbn.referenceColor(pbn.regionAt(75, 5)), blue) < 3f)
    }

    @Test
    fun `la palette ravive les couleurs sans changer leur teinte`() {
        // Un rouge brique moucheté de deux teintes voisines : sa moyenne est plus terne que ses touches.
        val pixels = image(60, 60) { x, y -> if ((x + y) % 2 == 0) 0xFFB5482E.toInt() else 0xFFA83A4A.toInt() }
        val faithful = Segmenter(SegmentationParams(colorCount = 2, minRegionArea = 40, vividness = 1f, contrast = 1f)).segment(pixels, 60, 60)
        val vivid = Segmenter(SegmentationParams(colorCount = 2, minRegionArea = 40)).segment(pixels, 60, 60)

        val a = FloatArray(3).also { ColorSpace.argbToLab(faithful.palette[0], it) }
        val b = FloatArray(3).also { ColorSpace.argbToLab(vivid.palette[0], it) }
        val chroma = { lab: FloatArray -> kotlin.math.hypot(lab[1], lab[2]) }
        val hue = { lab: FloatArray -> kotlin.math.atan2(lab[2], lab[1]) }
        assertTrue(chroma(b) > chroma(a) * 1.1f, "plus saturée : ${chroma(a)} -> ${chroma(b)}")
        assertTrue(kotlin.math.abs(hue(b) - hue(a)) < 0.05f, "même teinte")
    }

    @Test
    fun `une tache trop petite est absorbee par sa voisine`() {
        val pixels = image(60, 60) { x, y -> if (x in 30..32 && y in 30..32) yellow else red }
        val pbn = Segmenter(SegmentationParams(colorCount = 3, minRegionArea = 40, modeFilterPasses = 0, smoothingPasses = 0))
            .segment(pixels, 60, 60)

        assertEquals(1, pbn.regionCount)
    }

    @Test
    fun `le bruit ne cree pas de micro zones`() {
        val random = Random(1)
        val pixels = image(120, 90) { x, _ ->
            val base = if (x < 60) red else blue
            if (random.nextFloat() < 0.04f) yellow else base
        }
        val pbn = Segmenter(SegmentationParams(colorCount = 3, minRegionArea = 60)).segment(pixels, 120, 90)

        for (r in 0 until pbn.regionCount) assertTrue(pbn.regionArea(r) >= 60, "zone $r trop petite")
        assertEquals(2, pbn.regionCount)
    }

    @Test
    fun `le numero est place au coeur de la zone`() {
        val pbn = Segmenter(SegmentationParams(colorCount = 2, minRegionArea = 10))
            .segment(image(100, 50) { x, _ -> if (x < 50) red else blue }, 100, 50)
        val left = pbn.regionAt(10, 10)

        assertEquals(left, pbn.regionAt(pbn.labelX[left].toInt(), pbn.labelY[left].toInt()))
        assertTrue(pbn.labelRadius[left] >= 20f)
    }

    @Test
    fun `le decoupage est deterministe`() {
        val random = Random(3)
        val pixels = IntArray(64 * 64) { 0xFF000000.toInt() or random.nextInt(0xFFFFFF) }
        val params = Difficulty.EXPERT.params(64, 64)

        val a = Segmenter(params).segment(pixels, 64, 64)
        val b = Segmenter(params).segment(pixels, 64, 64)

        assertContentEquals(a.labels, b.labels)
        assertContentEquals(a.palette, b.palette)
    }

    @Test
    fun `index des pixels coherent avec les etiquettes`() {
        val pbn = Segmenter(SegmentationParams(colorCount = 3, minRegionArea = 20))
            .segment(image(50, 50) { x, y -> if (x < 25) red else if (y < 25) blue else yellow }, 50, 50)

        var total = 0
        for (r in 0 until pbn.regionCount) {
            for (k in pbn.regionStart[r] until pbn.regionStart[r + 1]) assertEquals(r, pbn.labels[pbn.regionPixels[k]])
            total += pbn.regionArea(r)
        }
        assertEquals(50 * 50, total)
    }
}
