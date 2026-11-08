package app.atelier.maitres.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Propose de noter l'application avec la petite fenêtre du Play Store, sans quitter le jeu.
 * Google décide seul de l'afficher ou non (et jamais deux fois de suite) ; hors Play Store,
 * ou en cas d'erreur, il ne se passe rien.
 */
fun askForReview(context: Context) {
    val activity = context.findActivity() ?: return
    runCatching {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful) runCatching { manager.launchReviewFlow(activity, request.result) }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
