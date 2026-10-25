package app.atelier.maitres.core

/**
 * Quand une mise à jour change les couleurs de la palette sans toucher aux zones, les touches
 * déjà posées gardent l'ancienne teinte et ne comptent plus comme justes. On les fait suivre :
 * une zone peinte de son ancienne couleur du maître prend la nouvelle.
 */
object ColourMigration {
    /**
     * Les remplissages [fills], où chaque zone peinte de sa couleur de référence dans [old]
     * reçoit celle de [new]. Null si les deux découpages n'ont pas les mêmes zones.
     */
    fun carryOver(old: PaintByNumber, new: PaintByNumber, fills: IntArray): IntArray? {
        if (old.regionCount != new.regionCount || fills.size != new.regionCount) return null
        if (!old.labels.contentEquals(new.labels)) return null
        return IntArray(fills.size) { r ->
            if (fills[r] != PaintingSession.EMPTY && fills[r] == old.referenceColor(r)) new.referenceColor(r) else fills[r]
        }
    }

    /** Vrai si une zone porte une couleur qui n'est ni vierge, ni dans la palette : une teinte d'avant. */
    fun hasForeignColours(canvas: PaintByNumber, fills: IntArray): Boolean {
        val palette = canvas.palette.toHashSet()
        return fills.any { it != PaintingSession.EMPTY && it !in palette }
    }

    /** Les réglages qui redonnent la palette d'avant les couleurs ravivées (format de cache 1). */
    fun legacyParams(params: SegmentationParams) = params.copy(vividness = 0f, contrast = 1f)
}
