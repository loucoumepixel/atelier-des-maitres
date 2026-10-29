package app.atelier.maitres

import androidx.compose.ui.geometry.Offset
import androidx.test.platform.app.InstrumentationRegistry
import app.atelier.maitres.core.Difficulty
import app.atelier.maitres.data.Painting
import app.atelier.maitres.ui.studio.StudioState
import app.atelier.maitres.ui.studio.Tool
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue

/**
 * Le banc d'essai commun aux tests du parcours : l'application telle qu'elle tourne sur le
 * téléphone, avec son catalogue, sa carrière et ses sauvegardes.
 */
object Banc {
    val app: AtelierApp
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AtelierApp

    /** Les toiles du maître, sans les images importées par le joueur. */
    val toiles: List<Painting> get() = app.repository.paintings.value.filter { !it.isImported }

    /** Exécute [block] sur le fil principal, comme le fait l'atelier, et renvoie son résultat. */
    fun <T> surLeFilPrincipal(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    /**
     * Ouvre [toile] au [niveau] comme l'atelier (découpage, matière, sauvegarde relue), puis
     * passe l'état de l'atelier à [block]. Le contexte des sauvegardes est fermé ensuite.
     */
    fun <T> ouvrir(toile: Painting, niveau: Difficulty, block: (StudioState) -> T): T {
        val loaded = runBlocking { app.repository.load(toile, niveau) {} }
        val scope = MainScope()
        try {
            val state = surLeFilPrincipal { StudioState(loaded, app.repository, app.career, daily = false, scope = scope) }
            return block(state)
        } finally {
            scope.cancel()
        }
    }

    /** Peint [region] de sa bonne couleur en la touchant sur son numéro, comme le joueur. */
    fun peindreJuste(state: StudioState, region: Int) {
        val canvas = state.canvas
        state.select(Tool.Palette(canvas.regionColor[region]))
        state.onTap(Offset(canvas.labelX[region], canvas.labelY[region]))
    }

    /** Termine [toile] au [niveau] et attend que la sauvegarde soit écrite. */
    fun terminer(toile: Painting, niveau: Difficulty) {
        // Déjà terminée par un autre test : on repart d'une toile vierge pour revoir la fête.
        if (app.repository.stats(toile).any { it.difficulty == niveau }) {
            app.repository.resetProgress(toile, niveau)
            attendre("${toile.id} : la toile ne se remet pas à zéro") { app.repository.stats(toile).none { it.difficulty == niveau } }
        }
        ouvrir(toile, niveau) { state ->
            surLeFilPrincipal {
                // Le numéro d'une zone est toujours posé dans la zone : la toucher la peint.
                for (region in 0 until state.canvas.regionCount) peindreJuste(state, region)
                state.saveNow()
            }
            assertTrue("${toile.id} : la toile n'est pas terminée", state.session.isComplete)
            assertTrue("${toile.id} : l'écran de fin ne s'affiche pas", state.showCompletion)
        }
        attendre("${toile.id} : la sauvegarde de la toile terminée n'arrive pas") {
            app.repository.stats(toile).any { it.difficulty == niveau && it.isComplete }
        }
    }

    /** Attend jusqu'à 15 secondes que [condition] soit vraie, sinon échoue avec [message]. */
    private fun attendre(message: String, condition: () -> Boolean) {
        val limite = System.currentTimeMillis() + 15_000
        while (!condition()) {
            check(System.currentTimeMillis() < limite) { message }
            Thread.sleep(100)
        }
    }
}
