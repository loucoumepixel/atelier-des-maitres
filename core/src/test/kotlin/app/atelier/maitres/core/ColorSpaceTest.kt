package app.atelier.maitres.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorSpaceTest {
    @Test
    fun `sRGB vers Lab et retour sans perte visible`() {
        val lab = FloatArray(3)
        for (argb in intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF1B3A6B.toInt(), 0xFFE8B931.toInt())) {
            ColorSpace.argbToLab(argb, lab)
            assertEquals(argb, ColorSpace.labToArgb(lab[0], lab[1], lab[2]))
        }
    }

    @Test
    fun `blanc a une luminance de 100`() {
        val lab = FloatArray(3)
        ColorSpace.argbToLab(0xFFFFFFFF.toInt(), lab)
        assertEquals(100f, lab[0], 0.01f)
    }
}
