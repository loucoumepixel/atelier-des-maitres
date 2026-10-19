package app.atelier.maitres

import android.app.Application
import app.atelier.maitres.data.Career
import app.atelier.maitres.data.PaintingRepository

/** Point d'entrée de l'application : porte les dépendances partagées (injection manuelle). */
class AtelierApp : Application() {
    val repository: PaintingRepository by lazy { PaintingRepository(this) }
    val career: Career by lazy { Career(this, repository) }
}
