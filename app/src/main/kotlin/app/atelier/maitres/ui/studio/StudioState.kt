package app.atelier.maitres.ui.studio

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import app.atelier.maitres.core.PaintingSession
import app.atelier.maitres.data.LoadedCanvas
import app.atelier.maitres.data.PaintingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** L'outil tenu en main. */
sealed interface Tool {
    /** Une couleur de la palette du maître (zones numérotées). */
    data class Palette(val index: Int) : Tool

    /** Une couleur libre choisie au nuancier. */
    data class Custom(val color: Int) : Tool

    data object Eraser : Tool
}

/**
 * État de l'atelier pour une toile ouverte : session de peinture, rendu, outil courant,
 * sauvegarde automatique. Toutes les méthodes s'appellent depuis le thread principal.
 */
@Stable
class StudioState(
    val loaded: LoadedCanvas,
    private val repository: PaintingRepository,
    private val scope: CoroutineScope,
) {
    val canvas = loaded.canvas
    val session = PaintingSession(canvas, loaded.savedFills)
    val renderer = CanvasRenderer(canvas, session, loaded.original)
    val camera = CanvasCamera(canvas.width, canvas.height)

    /** Incrémenté à chaque modification visible : le dessin le lit pour se rafraîchir. */
    var version by mutableIntStateOf(0)
        private set

    var tool by mutableStateOf<Tool>(Tool.Palette(nextUnfinishedColor(after = canvas.palette.size - 1) ?: 0))
        private set

    /** Couleurs libres utilisées pendant la séance, proposées à côté de la palette. */
    val customColors = mutableStateListOf<Int>()

    /** Zone dont l'utilisateur veut choisir la couleur (appui long), ou null. */
    var pickerRegion by mutableStateOf<Int?>(null)
        private set
    var pickerOpen by mutableStateOf(false)
        private set

    /** Dernière touche de pinceau, pour l'animation d'éclaboussure. */
    var lastStroke by mutableStateOf<Stroke?>(null)
        private set

    var showCompletion by mutableStateOf(false)

    /** La fin n'est célébrée qu'une fois par séance, même si l'on annule puis rétablit. */
    private var celebrated = session.isComplete

    /** Incrémenté quand toutes les zones d'une couleur sont peintes, pour un retour haptique. */
    var colorsFinished by mutableIntStateOf(0)
        private set

    private var saveJob: Job? = null
    private var dirty = false

    data class Stroke(val imagePoint: Offset, val color: Int, val id: Long)

    init {
        renderer.setHighlight((tool as? Tool.Palette)?.index ?: -1)
    }

    // ---------------------------------------------------------------- gestes

    fun onTap(imagePoint: Offset) {
        val region = canvas.regionAt(imagePoint.x.toInt(), imagePoint.y.toInt())
        if (region < 0) return
        when (val t = tool) {
            is Tool.Palette -> paint(region, canvas.palette[t.index], imagePoint)
            is Tool.Custom -> paint(region, t.color, imagePoint)
            Tool.Eraser -> commit(session.erase(region))
        }
    }

    fun onLongPress(imagePoint: Offset) {
        val region = canvas.regionAt(imagePoint.x.toInt(), imagePoint.y.toInt())
        if (region < 0) return
        pickerRegion = region
        pickerOpen = true
    }

    fun openPicker() {
        pickerRegion = null
        pickerOpen = true
    }

    fun closePicker() {
        pickerOpen = false
        pickerRegion = null
    }

    /** Couleur choisie au nuancier : devient l'outil courant, et peint la zone visée s'il y en a une. */
    fun onPicked(color: Int) {
        val region = pickerRegion
        val paletteIndex = canvas.palette.indexOf(color)
        if (paletteIndex >= 0) select(Tool.Palette(paletteIndex)) else {
            if (color !in customColors) customColors.add(0, color)
            select(Tool.Custom(color))
        }
        if (region != null) {
            val point = Offset(canvas.labelX[region], canvas.labelY[region])
            paint(region, color, point)
        }
        closePicker()
    }

    fun select(newTool: Tool) {
        tool = newTool
        renderer.setHighlight((newTool as? Tool.Palette)?.index ?: -1)
        version++
    }

    // ---------------------------------------------------------------- édition

    fun undo() = commit(session.undo())

    fun redo() = commit(session.redo())

    /** Efface toute la toile, en une action que l'on peut annuler. */
    fun restart() = commit(session.paintAll(IntArray(canvas.regionCount) { it }, PaintingSession.EMPTY))

    private fun paint(region: Int, color: Int, point: Offset) {
        val changed = session.paint(region, color)
        if (changed.isEmpty()) return
        lastStroke = Stroke(point, color, System.nanoTime())
        commit(changed)
        advanceIfColorDone(region)
        if (session.isComplete && !celebrated) {
            celebrated = true
            showCompletion = true
        }
    }

    private fun commit(changed: IntArray) {
        if (changed.isEmpty()) return
        renderer.refresh(changed)
        version++
        dirty = true
        scheduleSave()
    }

    /** Quand une couleur est terminée, on passe d'elle-même à la suivante. */
    private fun advanceIfColorDone(painted: Int) {
        val current = (tool as? Tool.Palette)?.index ?: return
        if (session.remainingPerColor[current] > 0) return
        // Seule la touche qui achève la couleur compte, pas celles posées ensuite avec elle.
        if (canvas.regionColor[painted] == current && session.isFaithful(painted)) colorsFinished++
        nextUnfinishedColor(after = current)?.let { select(Tool.Palette(it)) }
    }

    /** Première couleur inachevée après [after], en faisant le tour de la palette. */
    private fun nextUnfinishedColor(after: Int): Int? {
        val n = canvas.palette.size
        return (1..n).map { (after + it) % n }.firstOrNull { session.remainingPerColor[it] > 0 }
    }

    // ---------------------------------------------------------------- aide

    /** Centre la caméra sur la plus grande zone restante de la couleur courante. */
    suspend fun findNextRegion() {
        val index = (tool as? Tool.Palette)?.index ?: nextUnfinishedColor(after = canvas.palette.size - 1) ?: return
        if (tool !is Tool.Palette) select(Tool.Palette(index))
        val target = session.unfinishedRegions(index).maxByOrNull { canvas.labelRadius[it] } ?: return
        // Zoom choisi pour que le numéro soit confortable à lire et à toucher.
        val zoom = (14f / canvas.labelRadius[target]).coerceIn(1.5f, 8f)
        camera.animateTo(Offset(canvas.labelX[target], canvas.labelY[target]), zoom)
    }

    // ---------------------------------------------------------------- partage

    /** Image de la toile prête à être partagée (le rendu est copié avant de quitter le thread principal). */
    suspend fun shareableImage(): Uri = repository.shareableImage(loaded.painting, renderer.exportBitmap())

    // ---------------------------------------------------------------- sauvegarde

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            persist()
        }
    }

    /** Écrit immédiatement (sortie d'écran, mise en arrière-plan). */
    fun saveNow() {
        saveJob?.cancel()
        saveJob = null
        persist()
    }

    private fun persist() {
        if (!dirty) return
        dirty = false
        repository.saveProgress(loaded.painting, loaded.difficulty, session.fills.copyOf(), session.faithfulCount)
    }

    private companion object {
        const val SAVE_DELAY_MS = 800L
    }
}
