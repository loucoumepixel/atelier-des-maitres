package app.atelier.maitres.core

/**
 * La série de jours peints d'affilée, avec un jour de grâce : sept jours d'affilée le font
 * gagner, et il pardonne ensuite un seul jour oublié (on peint avant-hier, rien hier, et on
 * revient aujourd'hui : la série continue). On n'en garde qu'un à la fois.
 *
 * Les jours sont comptés en jours depuis l'époque (`LocalDate.toEpochDay()`).
 */
data class Streak(
    val days: Int = 0,
    val lastDay: Long? = null,
    val graces: Int = 0,
    /** Jours peints d'affilée depuis le dernier jour de grâce gagné ou utilisé. */
    val towardGrace: Int = 0,
) {
    /** La série telle qu'on l'affiche aujourd'hui : zéro si elle est déjà perdue. */
    fun current(today: Long): Int = if (isAlive(today)) days else 0

    /** Vrai si peindre aujourd'hui prolonge encore la série. */
    fun isAlive(today: Long): Boolean {
        val last = lastDay ?: return false
        return today - last <= 1 || (today - last == 2L && graces > 0)
    }

    /** Vrai si la série ne tient aujourd'hui que grâce au jour de grâce en réserve. */
    fun needsGrace(today: Long): Boolean = lastDay?.let { today - it == 2L && graces > 0 } ?: false

    /** La série après avoir peint ce jour-là. */
    fun paint(today: Long): Streak {
        val last = lastDay
        return when {
            last == today -> this
            last != null && today - last == 1L -> advance(today)
            last != null && today - last == 2L && graces > 0 -> copy(graces = graces - 1, towardGrace = 0).advance(today)
            else -> Streak(days = 1, lastDay = today, graces = graces, towardGrace = 1)
        }
    }

    private fun advance(today: Long): Streak {
        val toward = towardGrace + 1
        return if (toward >= GRACE_AFTER && graces < MAX_GRACES) {
            copy(days = days + 1, lastDay = today, graces = graces + 1, towardGrace = 0)
        } else {
            copy(days = days + 1, lastDay = today, towardGrace = toward)
        }
    }

    companion object {
        /** Jours d'affilée pour gagner un jour de grâce. */
        const val GRACE_AFTER = 7
        const val MAX_GRACES = 1
    }
}
