package app.atelier.maitres.ui

import androidx.annotation.StringRes
import app.atelier.maitres.R
import app.atelier.maitres.core.Difficulty

/** Nom traduit du niveau (le moteur, sans Android, ne garde que des libellés techniques). */
@get:StringRes
val Difficulty.labelRes: Int
    get() = when (this) {
        Difficulty.FACILE -> R.string.level_easy
        Difficulty.NORMAL -> R.string.level_normal
        Difficulty.EXPERT -> R.string.level_expert
    }
