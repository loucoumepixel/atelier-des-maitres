package app.atelier.maitres.ui.studio

import android.graphics.Bitmap
import app.atelier.maitres.core.OilRelief
import app.atelier.maitres.core.PaintByNumber
import app.atelier.maitres.core.PaintingSession
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Produit l'image affichée : papier, contours, zones peintes et surlignage.
 *
 * Le rendu vit dans un seul [Bitmap] mis à jour par rectangles : peindre une zone ne
 * réécrit que les pixels de son rectangle englobant, ce qui garde chaque touche
 * instantanée même sur une toile de près d'un million de pixels.
 *
 * Les zones peintes reprennent la matière de l'original : la couleur choisie est modulée
 * par la luminosité locale du tableau, si bien que les coups de pinceau du maître
 * réapparaissent sous la couleur posée, puis [relief] lui donne l'épaisseur d'une pâte
 * d'huile éclairée. Une couleur posée au pinceau s'étale depuis le point touché ([spread]).
 */
class CanvasRenderer(
    private val canvas: PaintByNumber,
    private val session: PaintingSession,
    original: Bitmap,
    private val relief: OilRelief? = null,
    reliefPercent: Int = 0,
) {
    val width = canvas.width
    val height = canvas.height
    val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    private val paper = IntArray(width * height)
    private val pixels = IntArray(width * height)

    /** Facteur de matière par pixel, en centièmes autour de 100 (de 60 à 140). */
    private val texture = ByteArray(width * height)

    /** Force du relief d'huile, en % (0 : peinture plate). Changer la redessine toute. */
    var reliefPercent: Int = reliefPercent
        set(value) {
            if (value == field) return
            field = value
            refresh(IntArray(canvas.regionCount) { it })
        }

    /** Couleur de palette dont les zones à peindre sont hachurées, ou -1. */
    var highlight: Int = -1
        private set

    init {
        buildPaper()
        buildTexture(original)
        for (r in 0 until canvas.regionCount) drawRegion(r)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /** Une couleur qui s'étale dans sa zone depuis le point touché. */
    private class Spread(val x: Float, val y: Float, val reach: Float, val before: Int, val start: Long, val duration: Long) {
        var radius = 0f
    }

    private val spreading = HashMap<Int, Spread>()

    /** Vrai tant qu'une couleur est encore en train de s'étaler. */
    val isSpreading: Boolean get() = spreading.isNotEmpty()

    /**
     * Fait s'étaler la couleur déjà posée sur [region] depuis le point ([x], [y]) de l'image,
     * par-dessus [before], son ancienne couleur. [tick] fait avancer l'animation.
     */
    fun spread(region: Int, x: Float, y: Float, before: Int, now: Long) {
        val o = 4 * region
        var reach = 0f
        for ((cx, cy) in listOf(o to o + 1, o + 2 to o + 1, o to o + 3, o + 2 to o + 3)) {
            val dx = canvas.bounds[cx] - x
            val dy = canvas.bounds[cy] - y
            reach = max(reach, sqrt(dx * dx + dy * dy))
        }
        // Une petite zone se couvre d'un coup de pinceau, une grande prend un peu plus de temps.
        val duration = (180f + reach * 1.6f).coerceAtMost(520f).toLong() * 1_000_000L
        spreading[region] = Spread(x, y, reach + 1f, before, now, duration)
        refresh(intArrayOf(region), keepSpreads = true)
    }

    /** Fait avancer les couleurs qui s'étalent ; renvoie vrai s'il en reste. */
    fun tick(now: Long): Boolean {
        if (spreading.isEmpty()) return false
        val regions = spreading.keys.toIntArray()
        val done = ArrayList<Int>()
        for ((region, s) in spreading) {
            val k = ((now - s.start).toFloat() / s.duration).coerceIn(0f, 1f)
            // Démarrage franc puis ralenti, comme la pâte qui s'écrase sous le pinceau.
            s.radius = s.reach * (1f - (1f - k) * (1f - k) * (1f - k))
            if (k >= 1f) done += region
        }
        for (r in done) spreading.remove(r)
        refresh(regions, keepSpreads = true)
        return spreading.isNotEmpty()
    }

    /** Redessine les zones modifiées. À appeler sur le thread principal. */
    fun refresh(regions: IntArray) = refresh(regions, keepSpreads = false)

    private fun refresh(regions: IntArray, keepSpreads: Boolean) {
        if (regions.isEmpty()) return
        // Annuler, gommer ou repeindre arrête net une couleur qui s'étalait.
        if (!keepSpreads) for (r in regions) spreading.remove(r)
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = -1; var maxY = -1
        for (r in regions) {
            drawRegion(r)
            val o = 4 * r
            minX = min(minX, canvas.bounds[o]); minY = min(minY, canvas.bounds[o + 1])
            maxX = max(maxX, canvas.bounds[o + 2]); maxY = max(maxY, canvas.bounds[o + 3])
        }
        bitmap.setPixels(pixels, minY * width + minX, width, minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    fun setHighlight(colorIndex: Int) {
        if (colorIndex == highlight) return
        val previous = highlight
        highlight = colorIndex
        val touched = (0 until canvas.regionCount).filter {
            val c = canvas.regionColor[it]
            (c == previous || c == colorIndex) && session.fills[it] == PaintingSession.EMPTY
        }.toIntArray()
        refresh(touched)
    }

    /**
     * Image à partager : la toile telle qu'elle est, sans le hachurage de la couleur active,
     * posée sur une marge de papier comme une œuvre encadrée. À appeler sur le thread principal.
     */
    fun exportBitmap(): Bitmap {
        val out = pixels.copyOf()
        if (highlight >= 0) {
            for (r in 0 until canvas.regionCount) {
                if (canvas.regionColor[r] != highlight || session.fills[r] != PaintingSession.EMPTY) continue
                for (k in canvas.regionStart[r] until canvas.regionStart[r + 1]) {
                    val p = canvas.regionPixels[k]
                    out[p] = paper[p]
                }
            }
        }
        val margin = max(width, height) / 24
        return Bitmap.createBitmap(width + 2 * margin, height + 2 * margin, Bitmap.Config.ARGB_8888).apply {
            eraseColor(PAPER)
            setPixels(out, 0, width, margin, margin, width, height)
        }
    }

    private fun drawRegion(region: Int) {
        val fill = session.fills[region]
        val start = canvas.regionStart[region]
        val end = canvas.regionStart[region + 1]
        val s = spreading[region]
        if (s != null) {
            val r2 = s.radius * s.radius
            for (k in start until end) {
                val p = canvas.regionPixels[k]
                val dx = p % width - s.x
                val dy = p / width - s.y
                pixels[p] = if (dx * dx + dy * dy <= r2) painted(p, fill) else bare(p, region, s.before)
            }
            return
        }
        for (k in start until end) {
            val p = canvas.regionPixels[k]
            pixels[p] = bare(p, region, fill)
        }
    }

    /** Le pixel [p] de [region] sous la couleur [fill] (toile vierge si [PaintingSession.EMPTY]). */
    private fun bare(p: Int, region: Int, fill: Int): Int = when {
        fill != PaintingSession.EMPTY -> painted(p, fill)
        canvas.regionColor[region] == highlight -> {
            val base = paper[p]
            if (base != PAPER) base else {
                val x = p % width
                val y = p / width
                if (((x + y) / HATCH) % 2 == 0) HATCH_DARK else HATCH_LIGHT
            }
        }
        else -> paper[p]
    }

    /** La couleur [fill] posée au pixel [p] : matière de l'original, puis relief d'huile. */
    private fun painted(p: Int, fill: Int): Int {
        val t = 100 + texture[p]
        val c = (0xFF shl 24) or
            (min(255, ((fill shr 16) and 0xFF) * t / 100) shl 16) or
            (min(255, ((fill shr 8) and 0xFF) * t / 100) shl 8) or
            min(255, (fill and 0xFF) * t / 100)
        return relief?.apply(p, c, reliefPercent) ?: c
    }

    /** Papier légèrement chaud, contours d'un pixel en gris graphite. */
    private fun buildPaper() {
        val labels = canvas.labels
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val l = labels[i]
                val edge = (x > 0 && labels[i - 1] != l) || (y > 0 && labels[i - width] != l)
                paper[i] = if (edge) OUTLINE else PAPER
            }
        }
    }

    private fun buildTexture(original: Bitmap) {
        val source = IntArray(width * height)
        original.getPixels(source, 0, width, 0, 0, width, height)
        val luminance = IntArray(source.size) {
            val c = source[it]
            (299 * ((c shr 16) and 0xFF) + 587 * ((c shr 8) and 0xFF) + 114 * (c and 0xFF)) / 1000
        }
        for (r in 0 until canvas.regionCount) {
            val start = canvas.regionStart[r]
            val end = canvas.regionStart[r + 1]
            var sum = 0L
            for (k in start until end) sum += luminance[canvas.regionPixels[k]]
            val mean = max(1f, sum.toFloat() / max(1, end - start))
            for (k in start until end) {
                val p = canvas.regionPixels[k]
                val t = 1f + TEXTURE_STRENGTH * (luminance[p] / mean - 1f)
                texture[p] = ((t.coerceIn(0.6f, 1.4f) - 1f) * 100f).toInt().toByte()
            }
        }
    }

    private companion object {
        const val PAPER = 0xFFFBF8F2.toInt()
        const val OUTLINE = 0xFFB4AC9F.toInt()
        const val HATCH_DARK = 0xFFDCD5C8.toInt()
        const val HATCH_LIGHT = 0xFFEDE8DF.toInt()
        const val HATCH = 5
        const val TEXTURE_STRENGTH = 0.85f
    }
}
