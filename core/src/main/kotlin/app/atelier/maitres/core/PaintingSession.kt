package app.atelier.maitres.core

/**
 * Ce que l'utilisateur a peint sur une toile, avec annuler / rétablir.
 *
 * `fills[r]` est la couleur ARGB posée sur la zone `r`, ou [EMPTY] si elle est vierge.
 * L'utilisateur est libre de choisir n'importe quelle couleur ; une zone est
 * « fidèle » quand sa couleur est celle de la palette du maître.
 */
class PaintingSession(
    val canvas: PaintByNumber,
    initialFills: IntArray? = null,
    private val historyLimit: Int = 400,
) {
    val fills: IntArray = initialFills?.copyOf()?.also {
        require(it.size == canvas.regionCount) { "progression incompatible avec la toile" }
    } ?: IntArray(canvas.regionCount)

    /** Une action annulable : les zones touchées, avec leur couleur avant et après. */
    private class Edit(val regions: IntArray, val before: IntArray, val after: IntArray)

    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()

    var paintedCount: Int = 0
        private set
    var faithfulCount: Int = 0
        private set

    /** Zones fidèles restantes par couleur de la palette (pour griser les couleurs terminées). */
    val remainingPerColor: IntArray = canvas.regionsPerColor.copyOf()

    init {
        for (r in fills.indices) account(r, EMPTY, fills[r])
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val isComplete: Boolean get() = faithfulCount == canvas.regionCount

    fun isFaithful(region: Int): Boolean = fills[region] == canvas.referenceColor(region)

    /** Peint une zone. Renvoie les zones modifiées (vide si rien n'a changé). */
    fun paint(region: Int, color: Int): IntArray = paintAll(intArrayOf(region), color)

    /** Peint plusieurs zones en une seule action annulable. */
    fun paintAll(regions: IntArray, color: Int): IntArray {
        val changed = regions.filter { it in fills.indices && fills[it] != color }.distinct().toIntArray()
        if (changed.isEmpty()) return changed
        val edit = Edit(changed, IntArray(changed.size) { fills[changed[it]] }, IntArray(changed.size) { color })
        apply(edit.regions, edit.after)
        undoStack.addLast(edit)
        if (undoStack.size > historyLimit) undoStack.removeFirst()
        redoStack.clear()
        return changed
    }

    /** Efface une zone (lui rend sa toile vierge). */
    fun erase(region: Int): IntArray = paint(region, EMPTY)

    fun undo(): IntArray {
        val edit = undoStack.removeLastOrNull() ?: return IntArray(0)
        apply(edit.regions, edit.before)
        redoStack.addLast(edit)
        return edit.regions
    }

    fun redo(): IntArray {
        val edit = redoStack.removeLastOrNull() ?: return IntArray(0)
        apply(edit.regions, edit.after)
        undoStack.addLast(edit)
        return edit.regions
    }

    /** Zones vierges ou fausses de la couleur de palette [colorIndex]. */
    fun unfinishedRegions(colorIndex: Int): IntArray =
        (0 until canvas.regionCount).filter { canvas.regionColor[it] == colorIndex && !isFaithful(it) }.toIntArray()

    private fun apply(regions: IntArray, colors: IntArray) {
        for (i in regions.indices) {
            val r = regions[i]
            val old = fills[r]
            fills[r] = colors[i]
            account(r, old, colors[i])
        }
    }

    private fun account(region: Int, old: Int, new: Int) {
        val ref = canvas.referenceColor(region)
        val colorIndex = canvas.regionColor[region]
        if (old != EMPTY) paintedCount--
        if (new != EMPTY) paintedCount++
        if (old == ref) { faithfulCount--; remainingPerColor[colorIndex]++ }
        if (new == ref) { faithfulCount++; remainingPerColor[colorIndex]-- }
    }

    companion object {
        /** Zone vierge. Une couleur peinte est toujours opaque, donc jamais égale à 0. */
        const val EMPTY = 0
    }
}
