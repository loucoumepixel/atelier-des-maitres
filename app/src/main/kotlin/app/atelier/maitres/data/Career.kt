package app.atelier.maitres.data

import android.content.Context
import app.atelier.maitres.core.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * La carrière du peintre : des touches gagnées en peignant, sept rangs à gravir, une série
 * de jours peints d'affilée et une toile du jour qui rapporte double.
 *
 * Seules les zones jamais encore réussies rapportent (un plus haut niveau atteint par toile
 * et par niveau), si bien que gommer puis repeindre ne gagne rien. Finir une couleur ou une
 * toile rapporte un bonus, une seule fois.
 */
class Career(context: Context, private val repository: PaintingRepository) {

    data class State(val touches: Int, val streak: Int, val lastDay: String?)

    private val prefs = context.getSharedPreferences("atelier", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<State> = _state.asStateFlow()

    val touches: Int get() = _state.value.touches
    val rank: Int get() = rankOf(touches)

    /** Jours peints d'affilée, remis à zéro si hier est resté sans peinture. */
    val streak: Int
        get() = _state.value.let { if (it.lastDay == today() || it.lastDay == today(-1)) it.streak else 0 }

    val paintedToday: Boolean get() = _state.value.lastDay == today()

    var soundOn: Boolean
        get() = prefs.getBoolean(SOUND, true)
        set(value) = prefs.edit().putBoolean(SOUND, value).apply()

    /** Ajoute [points] et renvoie le nouveau rang s'il vient d'être atteint, sinon -1. */
    fun earn(points: Int): Int {
        val before = rank
        val s = _state.value
        val day = today()
        val streak = when (s.lastDay) {
            day -> s.streak
            today(-1) -> s.streak + 1
            else -> 1
        }
        val next = State(s.touches + points.coerceAtLeast(0), streak, day)
        prefs.edit().putInt(TOUCHES, next.touches).putInt(STREAK, next.streak).putString(LAST_DAY, next.lastDay).apply()
        _state.value = next
        return rank.takeIf { it > before } ?: -1
    }

    fun isUnlocked(painting: Painting): Boolean =
        painting.isImported || rank >= painting.unlockRank || repository.stats(painting).any { it.faithful > 0 }

    /** Une étoile par niveau terminé, dans l'ordre Facile, Normal, Expert. */
    fun stars(painting: Painting): List<Boolean> {
        val stats = repository.stats(painting)
        return Difficulty.entries.map { d -> stats.any { it.difficulty == d && it.isComplete } }
    }

    /**
     * La toile du jour, tirée parmi les toiles disponibles d'emblée et pas encore finies à
     * tous les niveaux : elle ne change donc pas en cours de journée quand on monte de rang.
     */
    fun daily(paintings: List<Painting>): Painting? {
        val pool = paintings.filter { !it.isImported && it.unlockRank == 0 && stars(it).any { won -> !won } }
        if (pool.isEmpty()) return null
        var h = 0L
        for (ch in today()) h = (h * 31 + ch.code) and 0xFFFFFFFFL
        return pool[(h % pool.size).toInt()]
    }

    // ------------------------------------------------------------ mémoire par toile et par niveau

    /** Le plus grand nombre de zones fidèles jamais atteint : au-delà seulement, on gagne. */
    fun highWater(painting: Painting, difficulty: Difficulty): Int = prefs.getInt(key("hw", painting, difficulty), 0)

    fun setHighWater(painting: Painting, difficulty: Difficulty, value: Int) =
        prefs.edit().putInt(key("hw", painting, difficulty), value).apply()

    /** Couleurs déjà terminées une fois (leur bonus est acquis). */
    fun coloursDone(painting: Painting, difficulty: Difficulty): Set<Int> =
        prefs.getStringSet(key("cdone", painting, difficulty), emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()

    fun setColoursDone(painting: Painting, difficulty: Difficulty, colours: Set<Int>) =
        prefs.edit().putStringSet(key("cdone", painting, difficulty), colours.map { it.toString() }.toSet()).apply()

    /** Marque la toile gagnée à ce niveau ; vrai la première fois seulement. */
    fun markWon(painting: Painting, difficulty: Difficulty): Boolean {
        val k = key("won", painting, difficulty)
        if (prefs.getBoolean(k, false)) return false
        prefs.edit().putBoolean(k, true).apply()
        return true
    }

    private fun load(): State {
        if (prefs.contains(TOUCHES)) {
            return State(prefs.getInt(TOUCHES, 0), prefs.getInt(STREAK, 0), prefs.getString(LAST_DAY, null))
        }
        // Première ouverture après la mise à jour : la progression déjà faite compte d'emblée.
        var touches = 0
        for (painting in repository.paintings.value) for (s in repository.stats(painting)) {
            touches += s.faithful * s.difficulty.zonePoints + if (s.isComplete) s.difficulty.canvasBonus else 0
        }
        prefs.edit().putInt(TOUCHES, touches).apply()
        return State(touches, 0, null)
    }

    private fun key(kind: String, p: Painting, d: Difficulty) = "${kind}_${p.id}_${d.name}"

    private fun today(offset: Long = 0): String = LocalDate.now().plusDays(offset).toString()

    companion object {
        /** Touches nécessaires pour chaque rang, de Gribouilleur à Génie. */
        val RANK_AT = intArrayOf(0, 150, 500, 1200, 2500, 5000, 10000)

        fun rankOf(touches: Int): Int {
            var r = 0
            while (r + 1 < RANK_AT.size && touches >= RANK_AT[r + 1]) r++
            return r
        }

        /** Avancée vers le rang suivant (0 à 1), et le seuil de ce rang (null au rang maximal). */
        fun progress(touches: Int): Pair<Float, Int?> {
            val r = rankOf(touches)
            if (r + 1 >= RANK_AT.size) return 1f to null
            return (touches - RANK_AT[r]).toFloat() / (RANK_AT[r + 1] - RANK_AT[r]) to RANK_AT[r + 1]
        }

        private const val TOUCHES = "career_touches"
        private const val STREAK = "career_streak"
        private const val LAST_DAY = "career_last_day"
        private const val SOUND = "sound"
    }
}

/** Touches par zone bien peinte, selon le niveau. */
val Difficulty.zonePoints: Int get() = ordinal + 1

/** Bonus pour une couleur terminée. */
val Difficulty.colourBonus: Int
    get() = when (this) {
        Difficulty.FACILE -> 8
        Difficulty.NORMAL -> 12
        Difficulty.EXPERT -> 16
    }

/** Bonus pour une toile terminée, la première fois. */
val Difficulty.canvasBonus: Int
    get() = when (this) {
        Difficulty.FACILE -> 50
        Difficulty.NORMAL -> 150
        Difficulty.EXPERT -> 400
    }
