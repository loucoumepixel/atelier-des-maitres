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

    /** La petite musique de piano, en fond dans l'atelier. */
    var musicOn: Boolean
        get() = prefs.getBoolean(MUSIC, true)
        set(value) = prefs.edit().putBoolean(MUSIC, value).apply()

    /** Épaisseur de la peinture à l'huile choisie par le joueur. */
    var matter: Matter
        get() = Matter.entries.firstOrNull { it.name == prefs.getString(MATTER, null) } ?: Matter.LEGERE
        set(value) = prefs.edit().putString(MATTER, value.name).apply()

    /**
     * Combien de fois encore montrer la bulle qui explique le petit œil. Elle se tait dès que
     * le joueur s'en est servi une fois.
     */
    var peekTipsLeft: Int
        get() = prefs.getInt(PEEK_TIPS, 3)
        set(value) = prefs.edit().putInt(PEEK_TIPS, value.coerceAtLeast(0)).apply()

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

    /** Les toiles de l'exposition du mois sont prêtées à tous, même celles d'un rang à venir. */
    fun isUnlocked(painting: Painting): Boolean =
        painting.isImported || rank >= painting.unlockRank || painting.id in Exhibitions.current().paintings ||
            repository.stats(painting).any { it.faithful > 0 }

    // ------------------------------------------------------------ expositions et cadres

    /** Toiles de l'exposition du mois déjà terminées (à un niveau quelconque) ce mois-ci. */
    fun exhibitionDone(): Set<String> =
        prefs.getStringSet(EXPO_DONE + Exhibitions.monthKey(), emptySet())!!.toSet()

    /**
     * Note qu'une toile vient d'être terminée. Renvoie l'exposition du mois si c'est elle qui
     * la complète, la première fois : son cadre est alors acquis (les touches restent à donner).
     */
    fun recordFinish(painting: Painting): Exhibition? {
        val expo = Exhibitions.current()
        if (painting.id !in expo.paintings) return null
        val done = exhibitionDone() + painting.id
        prefs.edit().putStringSet(EXPO_DONE + Exhibitions.monthKey(), done).apply()
        if (!done.containsAll(expo.paintings) || expo.frame in framesWon()) return null
        prefs.edit().putStringSet(FRAMES_WON, framesWon().map { it.name }.toSet() + expo.frame.name).apply()
        return expo
    }

    private fun framesWon(): Set<Frame> =
        prefs.getStringSet(FRAMES_WON, emptySet())!!.mapNotNull { n -> Frame.entries.firstOrNull { it.name == n } }.toSet()

    /** Toiles du catalogue terminées au moins à un niveau. */
    fun finishedCount(paintings: List<Painting>): Int = paintings.count { p -> stars(p).any { it } }

    /** Les cadres que le joueur peut choisir, dans l'ordre de l'étagère. */
    fun frames(paintings: List<Painting>): List<Frame> {
        val won = framesWon()
        val baroque = finishedCount(paintings) >= Frame.BAROQUE_AT
        return Frame.entries.filter { it in Frame.FREE || (it == Frame.BAROQUE && baroque) || it in won }
    }

    /** Le cadre choisi pour une toile du musée, chêne par défaut. */
    fun frameOf(painting: Painting): Frame =
        Frame.entries.firstOrNull { it.name == prefs.getString(FRAME + painting.id, null) } ?: Frame.CHENE

    fun setFrame(painting: Painting, frame: Frame) = prefs.edit().putString(FRAME + painting.id, frame.name).apply()

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
        private const val MUSIC = "music"
        private const val PEEK_TIPS = "peek_tips_left"
        private const val MATTER = "matter"
        private const val EXPO_DONE = "expo_done_"
        private const val FRAMES_WON = "frames_won"
        private const val FRAME = "frame_"
    }
}

/** L'épaisseur de la peinture : plate, légère ou épaisse (force du relief d'huile, en %). */
enum class Matter(val percent: Int) {
    PLATE(0),
    LEGERE(35),
    EPAISSE(65),
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
