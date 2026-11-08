package app.atelier.maitres.core

/**
 * Ce que le joueur a accompli, résumé pour décider des succès. Les toiles sont désignées par
 * leur identifiant du catalogue ; les images importées ne comptent que dans [importsFinished].
 */
data class Progress(
    /** Niveaux terminés, par toile du catalogue. */
    val finished: Map<String, Set<Difficulty>> = emptyMap(),
    val catalogueSize: Int = 0,
    val importsFinished: Int = 0,
    val bestStreak: Int = 0,
    val exhibitionsWon: Int = 0,
    val varnished: Int = 0,
    val rank: Int = 0,
) {
    val finishedCount: Int get() = finished.count { it.value.isNotEmpty() }
    fun count(d: Difficulty): Int = finished.count { d in it.value }
    fun hasAll(ids: Collection<String>): Boolean = ids.all { finished[it].orEmpty().isNotEmpty() }
    val expertCount: Int get() = count(Difficulty.EXPERT)
}

/** Un succès : un emoji pour la médaille, et la règle qui le décroche. */
enum class Achievement(val emoji: String, private val rule: (Progress) -> Boolean) {
    PREMIERE_TOILE("🖌", { it.finishedCount >= 1 }),
    CINQ_TOILES("🖼", { it.finishedCount >= 5 }),
    DIX_TOILES("🏅", { it.finishedCount >= 10 }),
    VINGT_TOILES("🎖", { it.finishedCount >= 20 }),
    TOUTE_LA_GALERIE("🏛", { it.catalogueSize > 0 && it.finishedCount >= it.catalogueSize }),
    PREMIER_NORMAL("🥈", { it.count(Difficulty.NORMAL) >= 1 }),
    PREMIER_EXPERT("🥇", { it.expertCount >= 1 }),
    TROIS_ETOILES("⭐", { p -> p.finished.values.any { it.size == Difficulty.entries.size } }),
    DIX_EXPERT("🌟", { it.expertCount >= 10 }),
    VINCENT("🌻", { it.hasAll(VAN_GOGH) }),
    CLAUDE("🌸", { it.hasAll(MONET) }),
    JAPON("🗾", { it.hasAll(JAPAN) }),
    PEINTRE_DE_NUIT("🌙", { it.hasAll(listOf("nuit_etoilee", "cafe_de_nuit")) }),
    SERIE_3("🔥", { it.bestStreak >= 3 }),
    SERIE_7("📅", { it.bestStreak >= 7 }),
    SERIE_30("🗓", { it.bestStreak >= 30 }),
    EXPOSANT("🖼", { it.exhibitionsWon >= 1 }),
    COMMISSAIRE("🎟", { it.exhibitionsWon >= 3 }),
    VERNIS("✨", { it.varnished >= 1 }),
    VERNISSEUR("💎", { it.varnished >= 10 }),
    MA_TOILE("📷", { it.importsFinished >= 1 }),
    MAITRE("🎓", { it.rank >= 5 }),
    GENIE("👑", { it.rank >= 6 }),
    CHEF_D_OEUVRE("🏆", { it.catalogueSize > 0 && it.expertCount >= it.catalogueSize });

    fun isWon(p: Progress): Boolean = rule(p)

    companion object {
        val VAN_GOGH = listOf("nuit_etoilee", "champ_de_ble_cypres", "cafe_de_nuit", "maisons_personnage", "chambre_arles", "iris", "tournesols")
        val MONET = listOf("monet_impression", "monet_nympheas", "monet_prairie", "monet_coquelicots", "monet_ombrelle")
        val JAPAN = listOf("grande_vague", "fuji_rouge", "averse_ohashi")

        fun won(p: Progress): List<Achievement> = entries.filter { it.isWon(p) }

        /** Les succès décrochés entre deux états, pour les annoncer une seule fois. */
        fun newlyWon(before: Progress, after: Progress): List<Achievement> =
            entries.filter { !it.isWon(before) && it.isWon(after) }
    }
}

/**
 * L'objectif après le dernier rang : le Chef-d'œuvre, trois étoiles Expert sur toutes les
 * toiles du catalogue. Renvoie (toiles finies en Expert, total).
 */
fun masterpieceGoal(p: Progress): Pair<Int, Int> = p.expertCount.coerceAtMost(p.catalogueSize) to p.catalogueSize
