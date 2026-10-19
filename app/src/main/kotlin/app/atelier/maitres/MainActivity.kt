package app.atelier.maitres

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.atelier.maitres.core.Difficulty
import app.atelier.maitres.ui.gallery.GalleryScreen
import app.atelier.maitres.ui.studio.StudioScreen
import app.atelier.maitres.ui.theme.AtelierTheme
import app.atelier.maitres.ui.welcome.WelcomeScreen

/** Écran d'accueil, montré à chaque ouverture de l'application. */
private const val WELCOME = "accueil"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as AtelierApp).repository
        val career = (application as AtelierApp).career

        setContent {
            AtelierTheme {
                // Navigation volontairement simple : un état sauvegardable.
                // WELCOME à l'ouverture de l'application, "id|NIVEAU" quand une toile est ouverte,
                // null pour la galerie.
                var open by rememberSaveable { mutableStateOf<String?>(WELCOME) }

                AnimatedContent(
                    targetState = open,
                    transitionSpec = {
                        (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.96f)) togetherWith fadeOut(tween(200))
                    },
                    label = "navigation",
                ) { target ->
                    val parts = target?.split('|')
                    val painting = parts?.let { repository.painting(it[0]) }
                    val difficulty = parts?.getOrNull(1)?.let { name -> Difficulty.entries.firstOrNull { it.name == name } }
                    if (target == WELCOME) {
                        WelcomeScreen(career = career, onEnter = { open = null })
                    } else if (painting != null && difficulty != null) {
                        StudioScreen(
                            painting = painting,
                            difficulty = difficulty,
                            repository = repository,
                            career = career,
                            onBack = { open = null },
                        )
                    } else {
                        GalleryScreen(
                            repository = repository,
                            career = career,
                            onOpen = { p, d -> open = "${p.id}|${d.name}" },
                        )
                    }
                }
            }
        }
    }
}
