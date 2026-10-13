package app.atelier.maitres.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PbnCodecTest {

    @Test
    fun `aller retour d une toile`() {
        val random = Random(5)
        val pixels = IntArray(90 * 70) { 0xFF000000.toInt() or random.nextInt(0xFFFFFF) }
        val canvas = Segmenter(Difficulty.FACILE.params(90, 70)).segment(pixels, 90, 70)

        val bytes = ByteArrayOutputStream().also { PbnCodec.writeCanvas(canvas, it) }.toByteArray()
        val copy = PbnCodec.readCanvas(ByteArrayInputStream(bytes))

        assertEquals(canvas.width, copy.width)
        assertContentEquals(canvas.labels, copy.labels)
        assertContentEquals(canvas.palette, copy.palette)
        assertContentEquals(canvas.regionColor, copy.regionColor)
        assertContentEquals(canvas.labelRadius, copy.labelRadius)
    }

    @Test
    fun `aller retour d une progression`() {
        val fills = intArrayOf(0, 0xFF112233.toInt(), -1, 42)
        val bytes = ByteArrayOutputStream().also { PbnCodec.writeProgress(fills, it) }.toByteArray()

        assertContentEquals(fills, PbnCodec.readProgress(ByteArrayInputStream(bytes)))
    }
}
