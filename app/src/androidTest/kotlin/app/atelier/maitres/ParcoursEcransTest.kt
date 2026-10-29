package app.atelier.maitres

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.atelier.maitres.core.Difficulty
import app.atelier.maitres.ui.gallery.GalleryScreen
import app.atelier.maitres.ui.museum.MuseumScreen
import app.atelier.maitres.ui.studio.StudioScreen
import app.atelier.maitres.ui.studio.TAG_TOILE
import app.atelier.maitres.ui.studio.TAG_TROUVER
import app.atelier.maitres.ui.theme.AtelierTheme
import app.atelier.maitres.ui.welcome.WelcomeScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Le parcours du joueur, écran par écran : chacun s'affiche et réagit au doigt sans planter.
 * Les écrans sont posés un par un, sans passer par l'intro du studio.
 */
@RunWith(AndroidJUnit4::class)
class ParcoursEcransTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val app get() = Banc.app

    /** Laisse vivre l'écran (animations, chargements) pendant [ms] millisecondes. */
    private fun laisserVivre(ms: Long) {
        val fin = System.currentTimeMillis() + ms
        rule.waitUntil(ms + 5_000) { System.currentTimeMillis() >= fin }
    }

    @Test
    fun accueilPuisGalerie() {
        var entre by mutableStateOf(false)
        rule.setContent {
            AtelierTheme {
                if (!entre) {
                    WelcomeScreen(career = app.career, onEnter = { entre = true })
                } else {
                    GalleryScreen(app.repository, app.career, onOpen = { _, _ -> }, onMuseum = {})
                }
            }
        }
        // Le bouton d'entrée dit « À vos pinceaux ! » la première fois, « On continue ! » ensuite.
        val boutons = listOf(R.string.welcome_cta, R.string.welcome_cta_back).map { rule.activity.getString(it) }
        val entrer = boutons.map { hasText(it) }.reduce { a, b -> a or b }
        rule.waitUntil(30_000) { rule.onAllNodes(entrer).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(entrer)[0].performClick()
        rule.waitUntil(10_000) { entre }
        laisserVivre(4_000)
        rule.onRoot().performTouchInput { swipeUp() }
        laisserVivre(2_000)
    }

    @Test
    fun atelierPeintAvecLeBoutonTrouver() {
        val toile = Banc.toiles.first()
        var ouvert by mutableStateOf(true)
        rule.setContent {
            AtelierTheme {
                if (ouvert) StudioScreen(toile, Difficulty.FACILE, app.repository, app.career, onBack = {})
            }
        }
        // Le découpage de la toile peut prendre un moment sur un téléphone virtuel.
        rule.waitUntil(120_000) { rule.onAllNodesWithTag(TAG_TROUVER).fetchSemanticsNodes().isNotEmpty() }
        val avant = app.repository.stats(toile).firstOrNull { it.difficulty == Difficulty.FACILE }?.faithful ?: 0
        repeat(4) {
            // Le bouton amène au centre de la toile la prochaine zone (ou une zone fausse), couleur choisie.
            rule.onNodeWithTag(TAG_TROUVER).performClick()
            laisserVivre(1_500)
            rule.onNodeWithTag(TAG_TOILE).performTouchInput { click(center) }
            laisserVivre(800)
        }
        // Quitter l'atelier : la sauvegarde part en quittant l'écran.
        rule.runOnUiThread { ouvert = false }
        val limite = System.currentTimeMillis() + 10_000
        fun apres() = app.repository.stats(toile).firstOrNull { it.difficulty == Difficulty.FACILE }?.faithful ?: 0
        while (apres() <= avant && System.currentTimeMillis() < limite) Thread.sleep(100)
        assertTrue("aucune zone peinte avec le bouton trouver : ${apres()} zones justes, $avant avant", apres() > avant)
    }

    @Test
    fun museeAvecUneToileAccrochee() {
        Banc.terminer(Banc.toiles.last(), Difficulty.FACILE)
        rule.setContent {
            AtelierTheme { MuseumScreen(app.repository, app.career, onBack = {}) }
        }
        laisserVivre(4_000)
        // Toucher le centre ouvre la vue face à face si un tableau s'y trouve.
        rule.onRoot().performTouchInput { click(center) }
        laisserVivre(4_000)
        rule.onRoot().performTouchInput { swipeLeft() }
        laisserVivre(2_000)
    }
}
