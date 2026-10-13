package app.atelier.maitres.core

/**
 * Une toile découpée en zones à peindre.
 *
 * Tout est stocké dans des tableaux primitifs (pas d'objet par zone ni par pixel) :
 * une toile de 1200 px compte ~900 000 pixels et plusieurs centaines de zones,
 * et ce format reste compact en mémoire comme sur disque.
 *
 * @property labels pour chaque pixel (ligne par ligne), l'indice de sa zone.
 * @property palette couleurs de référence de la toile ; le numéro affiché est `indice + 1`.
 * @property regionColor pour chaque zone, l'indice de sa couleur dans [palette].
 * @property labelX abscisse du point le plus « intérieur » de la zone, où l'on écrit son numéro.
 * @property labelY ordonnée de ce point.
 * @property labelRadius distance de ce point au bord de la zone : taille maximale du numéro.
 */
class PaintByNumber(
    val width: Int,
    val height: Int,
    val labels: IntArray,
    val palette: IntArray,
    val regionColor: IntArray,
    val labelX: FloatArray,
    val labelY: FloatArray,
    val labelRadius: FloatArray,
) {
    init {
        require(labels.size == width * height) { "labels: ${labels.size} != $width x $height" }
        require(labelX.size == regionCount && labelY.size == regionCount && labelRadius.size == regionCount)
    }

    val regionCount: Int get() = regionColor.size

    /** Zone sous le pixel (x, y), ou -1 hors de la toile. */
    fun regionAt(x: Int, y: Int): Int =
        if (x < 0 || y < 0 || x >= width || y >= height) -1 else labels[y * width + x]

    /** Couleur de référence (ARGB) de la zone [region]. */
    fun referenceColor(region: Int): Int = palette[regionColor[region]]

    /**
     * Index inversé zone -> pixels au format CSR : les pixels de la zone `r` sont
     * `regionPixels[regionStart[r] until regionStart[r + 1]]`. Permet de remplir
     * une zone sans parcourir toute l'image.
     */
    val regionStart: IntArray
    val regionPixels: IntArray

    /** Rectangle englobant de chaque zone : [minX, minY, maxX, maxY] à l'indice `4 * r`. */
    val bounds: IntArray

    /** Nombre de zones par couleur de la palette. */
    val regionsPerColor: IntArray

    init {
        val n = regionCount
        val start = IntArray(n + 1)
        for (l in labels) start[l + 1]++
        for (r in 0 until n) start[r + 1] += start[r]
        val cursor = start.copyOf(n)
        val pixels = IntArray(labels.size)
        val box = IntArray(n * 4)
        for (r in 0 until n) {
            box[4 * r] = Int.MAX_VALUE; box[4 * r + 1] = Int.MAX_VALUE
            box[4 * r + 2] = -1; box[4 * r + 3] = -1
        }
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = labels[i]
                pixels[cursor[r]++] = i
                val o = 4 * r
                if (x < box[o]) box[o] = x
                if (y < box[o + 1]) box[o + 1] = y
                if (x > box[o + 2]) box[o + 2] = x
                if (y > box[o + 3]) box[o + 3] = y
                i++
            }
        }
        regionStart = start
        regionPixels = pixels
        bounds = box
        regionsPerColor = IntArray(palette.size).also { counts -> for (c in regionColor) counts[c]++ }
    }

    fun regionArea(region: Int): Int = regionStart[region + 1] - regionStart[region]
}
