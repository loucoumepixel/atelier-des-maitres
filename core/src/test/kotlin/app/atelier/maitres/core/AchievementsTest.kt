package app.atelier.maitres.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AchievementsTest {
    @Test
    fun `vingt-quatre succès aux emojis présents`() {
        assertEquals(24, Achievement.entries.size)
        assertTrue(Achievement.entries.all { it.emoji.isNotBlank() })
    }

    @Test
    fun `rien n'est gagné au départ`() {
        assertEquals(emptyList(), Achievement.won(Progress(catalogueSize = 30)))
    }

    @Test
    fun `une première toile en Expert décroche deux succès`() {
        val before = Progress(catalogueSize = 30)
        val after = before.copy(finished = mapOf("iris" to setOf(Difficulty.EXPERT)))
        assertEquals(
            listOf(Achievement.PREMIERE_TOILE, Achievement.PREMIER_EXPERT),
            Achievement.newlyWon(before, after),
        )
    }

    @Test
    fun `les sept Vincent`() {
        val p = Progress(catalogueSize = 30, finished = Achievement.VAN_GOGH.associateWith { setOf(Difficulty.FACILE) })
        assertTrue(Achievement.VINCENT.isWon(p))
        assertTrue(Achievement.PEINTRE_DE_NUIT !in Achievement.won(p.copy(finished = p.finished - "cafe_de_nuit")))
    }

    @Test
    fun `le chef-d'oeuvre demande l'Expert partout`() {
        val all = (1..30).associate { "t$it" to setOf(Difficulty.EXPERT) }
        val p = Progress(catalogueSize = 30, finished = all)
        assertTrue(Achievement.CHEF_D_OEUVRE.isWon(p))
        assertEquals(30 to 30, masterpieceGoal(p))
        assertEquals(29 to 30, masterpieceGoal(p.copy(finished = all - "t1")))
    }
}
