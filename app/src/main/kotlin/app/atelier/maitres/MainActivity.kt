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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as AtelierApp).repository

        setContent {
            AtelierTheme {
                // Navigation volontairement simple : deux écrans, un état sauvegardable.
                // "id|NIVEAU" quand une toile est ouverte, null pour la galerie.
                var open by rememberSaveable { mutableStateOf<String?>(null) }

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
                    if (painting != null && difficulty != null) {
                        StudioScreen(
                            painting = painting,
                            difficulty = difficulty,
                            repository = repository,
                            onBack = { open = null },
                        )
                    } else {
                        GalleryScreen(
                            repository = repository,
                            onOpen = { p, d -> open = "${p.id}|${d.name}" },
                        )
                    }
                }
            }
        }
    }
}
