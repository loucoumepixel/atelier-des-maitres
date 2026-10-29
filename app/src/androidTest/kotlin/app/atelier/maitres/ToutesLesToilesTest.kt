package app.atelier.maitres

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.atelier.maitres.core.Difficulty
import app.atelier.maitres.data.Matter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Chaque toile du catalogue s'ouvre et se peint sur un vrai Android : c'est le test qui aurait
 * arrêté la 1.0.29, dont aucune toile ne s'ouvrait.
 */
@RunWith(AndroidJUnit4::class)
class ToutesLesToilesTest {

    @Test
    fun chaqueToileSOuvreEtSePeint() {
        val toiles = Banc.toiles
        assertTrue("le catalogue est vide", toiles.isNotEmpty())
        for (toile in toiles) {
            Banc.ouvrir(toile, Difficulty.FACILE) { state ->
                Banc.surLeFilPrincipal {
                    val avant = state.session.faithfulCount
                    val zones = (0 until state.canvas.regionCount).filter { !state.session.isFaithful(it) }.take(12)
                    // Une toile déjà terminée par un autre test s'ouvre, cela suffit.
                    if (zones.isEmpty()) return@surLeFilPrincipal
                    for (region in zones) Banc.peindreJuste(state, region)
                    assertEquals("${toile.id} : les zones touchées ne se peignent pas", avant + zones.size, state.session.faithfulCount)

                    // Annuler puis rétablir la dernière touche.
                    state.undo()
                    assertEquals("${toile.id} : annuler ne fonctionne pas", avant + zones.size - 1, state.session.faithfulCount)
                    state.redo()
                    assertEquals("${toile.id} : rétablir ne fonctionne pas", avant + zones.size, state.session.faithfulCount)

                    // Les trois matières se rendent, et l'image à partager se prépare.
                    for (m in Matter.entries) state.changeMatter(m)
                    state.changeMatter(Matter.LEGERE)
                    state.renderer.exportBitmap()
                    state.saveNow()
                }
            }
        }
    }

    @Test
    fun lesNiveauxNormalEtExpertSOuvrent() {
        val toile = Banc.toiles.first()
        for (niveau in listOf(Difficulty.NORMAL, Difficulty.EXPERT)) {
            Banc.ouvrir(toile, niveau) { state ->
                assertTrue("${toile.id} ${niveau.name} : aucune zone", state.canvas.regionCount > 0)
            }
        }
    }

    @Test
    fun uneToileSeTermineJusquALaFete() {
        // La dernière du catalogue : les autres tests repeignent les premières.
        Banc.terminer(Banc.toiles.last(), Difficulty.FACILE)
    }
}
