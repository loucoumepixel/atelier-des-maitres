package app.atelier.maitres.data

import android.content.Context
import app.atelier.maitres.core.Achievement
import app.atelier.maitres.core.Difficulty
import app.atelier.maitres.core.Progress
import app.atelier.maitres.core.Streak
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

    data class State(val touches: Int, val streak: Streak)

    private val prefs = context.getSharedPreferences("atelier", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<State> = _state.asStateFlow()

    val touches: Int get() = _state.value.touches
    val rank: Int get() = rankOf(touches)

    /**
     * Jours peints d'affilée, remis à zéro si un jour est resté sans peinture, sauf si le jour
     * de grâce (gagné tous les 7 jours d'affilée) le pardonne.
     */
    val streak: Int get() = _state.value.streak.current(epochDay())

    /** Jours de grâce en réserve (0 ou 1). */
    val graces: Int get() = _state.value.streak.graces

    /** Vrai quand la série ne tient aujourd'hui que par le jour de grâce : peindre l'utilisera. */
    val streakNeedsGrace: Boolean get() = _state.value.streak.needsGrace(epochDay())

    /** Jours d'affilée qu'il reste à peindre pour gagner un jour de grâce. */
    val daysToGrace: Int
        get() {
            val s = _state.value.streak
            return if (s.isAlive(epochDay())) (Streak.GRACE_AFTER - s.towardGrace).coerceAtLeast(1) else Streak.GRACE_AFTER
        }

    /** La plus longue série jamais tenue. */
    val bestStreak: Int get() = maxOf(prefs.getInt(BEST_STREAK, 0), streak)

    val paintedToday: Boolean get() = _state.value.streak.lastDay == epochDay()

    var soundOn: Boolean
        get() = prefs.getBoolean(SOUND, true)
        set(value) = prefs.edit().putBoolean(SOUND, value).apply()

    /** La petite musique de piano, en fond dans l'atelier. */
    var musicOn: Boolean
        get() = prefs.getBoolean(MUSIC, true)
        set(value) {
            prefs.edit().putBoolean(MUSIC, value).apply()
            _music.value = value
        }

    private val _music = MutableStateFlow(prefs.getBoolean(MUSIC, true))

    /** La musique (et l'ambiance de salle) est-elle voulue ? Suit [musicOn]. */
    val musicFlow: StateFlow<Boolean> = _music.asStateFlow()

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
        val next = State(s.touches + points.coerceAtLeast(0), s.streak.paint(epochDay()))
        prefs.edit()
            .putInt(TOUCHES, next.touches)
            .putInt(STREAK, next.streak.days)
            .putString(LAST_DAY, today())
            .putInt(GRACES, next.streak.graces)
            .putInt(TOWARD_GRACE, next.streak.towardGrace)
            .putInt(BEST_STREAK, maxOf(prefs.getInt(BEST_STREAK, 0), next.streak.days))
            .apply()
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

    /** Expositions complètes (un cadre gagné chacune). */
    val exhibitionsWon: Int get() = framesWon().size

    // ------------------------------------------------------------ vernis, succès, avis

    /** Vrai si la toile a déjà reçu son vernis final. */
    fun isVarnished(painting: Painting): Boolean = painting.id in prefs.getStringSet(VARNISHED, emptySet())!!

    fun varnish(painting: Painting) =
        prefs.edit().putStringSet(VARNISHED, prefs.getStringSet(VARNISHED, emptySet())!! + painting.id).apply()

    val varnishedCount: Int get() = prefs.getStringSet(VARNISHED, emptySet())!!.size

    /** Le résumé de ce que le joueur a accompli, pour les succès et le Chef-d'œuvre. */
    fun progress(paintings: List<Painting>): Progress {
        val catalogue = paintings.filter { !it.isImported }
        val finished = catalogue.associate { p ->
            p.id to Difficulty.entries.zip(stars(p)).filter { it.second }.map { it.first }.toSet()
        }
        return Progress(
            finished = finished,
            catalogueSize = catalogue.size,
            importsFinished = paintings.count { it.isImported && stars(it).any { won -> won } },
            bestStreak = bestStreak,
            exhibitionsWon = exhibitionsWon,
            varnished = varnishedCount,
            rank = rank,
        )
    }

    /** Succès déjà annoncés au joueur (les autres sont fêtés à la prochaine occasion). */
    private var announced: Set<String>
        get() = prefs.getStringSet(ANNOUNCED, emptySet())!!
        set(value) = prefs.edit().putStringSet(ANNOUNCED, value).apply()

    /**
     * À la première ouverture après la mise à jour, les succès déjà mérités sont rangés sans
     * fanfare : on ne fête que ceux décrochés ensuite.
     */
    fun seedAchievements(paintings: List<Painting>) {
        if (prefs.contains(ANNOUNCED)) return
        announced = Achievement.won(progress(paintings)).map { it.name }.toSet()
    }

    /** Les succès décrochés et pas encore annoncés ; ils sont marqués annoncés en retour. */
    fun takeNewAchievements(paintings: List<Painting>): List<Achievement> {
        val won = Achievement.won(progress(paintings))
        val fresh = won.filter { it.name !in announced }
        if (fresh.isNotEmpty()) announced = announced + fresh.map { it.name }
        return fresh
    }

    /**
     * Vrai une seule fois, quand le moment est bon pour demander un avis sur le Play Store :
     * après une toile terminée, et seulement à partir de la troisième.
     */
    fun shouldAskReview(paintings: List<Painting>): Boolean {
        if (prefs.getBoolean(REVIEW_ASKED, false) || finishedCount(paintings) < 3) return false
        prefs.edit().putBoolean(REVIEW_ASKED, true).apply()
        return true
    }

    /** La première toile guidée a-t-elle déjà été montrée ? */
    var guidedDone: Boolean
        get() = prefs.getBoolean(GUIDED_DONE, false)
        set(value) = prefs.edit().putBoolean(GUIDED_DONE, value).apply()

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
            val last = prefs.getString(LAST_DAY, null)?.let { runCatching { LocalDate.parse(it).toEpochDay() }.getOrNull() }
            val streak = Streak(
                days = prefs.getInt(STREAK, 0),
                lastDay = last,
                graces = prefs.getInt(GRACES, 0),
                towardGrace = prefs.getInt(TOWARD_GRACE, 0),
            )
            return State(prefs.getInt(TOUCHES, 0), streak)
        }
        // Première ouverture après la mise à jour : la progression déjà faite compte d'emblée.
        var touches = 0
        for (painting in repository.paintings.value) for (s in repository.stats(painting)) {
            touches += s.faithful * s.difficulty.zonePoints + if (s.isComplete) s.difficulty.canvasBonus else 0
        }
        prefs.edit().putInt(TOUCHES, touches).apply()
        return State(touches, Streak())
    }

    private fun key(kind: String, p: Painting, d: Difficulty) = "${kind}_${p.id}_${d.name}"

    private fun today(offset: Long = 0): String = LocalDate.now().plusDays(offset).toString()

    private fun epochDay(): Long = LocalDate.now().toEpochDay()

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
        private const val GRACES = "career_graces"
        private const val TOWARD_GRACE = "career_toward_grace"
        private const val BEST_STREAK = "career_best_streak"
        private const val VARNISHED = "varnished"
        private const val ANNOUNCED = "achievements_announced"
        private const val REVIEW_ASKED = "review_asked"
        private const val GUIDED_DONE = "guided_done"
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
