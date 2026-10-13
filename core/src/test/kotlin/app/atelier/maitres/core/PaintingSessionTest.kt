package app.atelier.maitres.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaintingSessionTest {

    private val red = 0xFFC0392B.toInt()
    private val blue = 0xFF2E86C1.toInt()
    private val green = 0xFF27AE60.toInt()

    /** Trois bandes verticales : rouge, bleu, rouge. */
    private val canvas = PaintByNumber(
        width = 3, height = 1,
        labels = intArrayOf(0, 1, 2),
        palette = intArrayOf(red, blue),
        regionColor = intArrayOf(0, 1, 0),
        labelX = floatArrayOf(0.5f, 1.5f, 2.5f),
        labelY = floatArrayOf(0.5f, 0.5f, 0.5f),
        labelRadius = floatArrayOf(1f, 1f, 1f),
    )

    @Test
    fun `peindre compte les zones peintes et fideles`() {
        val session = PaintingSession(canvas)
        session.paint(0, red)
        session.paint(1, green)

        assertEquals(2, session.paintedCount)
        assertEquals(1, session.faithfulCount)
        assertEquals(1, session.remainingPerColor[0])
        assertEquals(1, session.remainingPerColor[1])
        assertFalse(session.isComplete)
    }

    @Test
    fun `annuler puis retablir restaure exactement l etat`() {
        val session = PaintingSession(canvas)
        session.paint(0, red)
        session.paintAll(intArrayOf(1, 2), blue)

        assertTrue(session.undo().contentEquals(intArrayOf(1, 2)))
        assertEquals(1, session.paintedCount)
        assertEquals(PaintingSession.EMPTY, session.fills[2])

        session.redo()
        assertEquals(3, session.paintedCount)
        assertEquals(2, session.faithfulCount)
    }

    @Test
    fun `une nouvelle action vide la pile retablir`() {
        val session = PaintingSession(canvas)
        session.paint(0, red)
        session.undo()
        session.paint(1, blue)

        assertFalse(session.canRedo)
    }

    @Test
    fun `repeindre la meme couleur n est pas une action`() {
        val session = PaintingSession(canvas)
        session.paint(0, red)

        assertEquals(0, session.paint(0, red).size)
        session.undo()
        assertFalse(session.canUndo)
    }

    @Test
    fun `toile complete quand tout est fidele`() {
        val session = PaintingSession(canvas)
        session.paintAll(session.unfinishedRegions(0), red)
        session.paintAll(session.unfinishedRegions(1), blue)

        assertTrue(session.isComplete)
    }

    @Test
    fun `progression rechargee recalcule les compteurs`() {
        val session = PaintingSession(canvas, intArrayOf(red, PaintingSession.EMPTY, green))

        assertEquals(2, session.paintedCount)
        assertEquals(1, session.faithfulCount)
        session.erase(0)
        assertEquals(0, session.faithfulCount)
    }
}
