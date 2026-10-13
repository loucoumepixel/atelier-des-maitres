package app.atelier.maitres.core

import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** Niveau de détail du découpage. */
enum class Difficulty(val label: String, val colorCount: Int, private val areaDivisor: Int, val minLabelRadius: Float) {
    FACILE("Facile", 14, 900, 4f),
    NORMAL("Normal", 22, 2200, 3f),
    EXPERT("Expert", 32, 5200, 2.5f);

    fun params(width: Int, height: Int) = SegmentationParams(
        colorCount = colorCount,
        minRegionArea = max(24, width * height / areaDivisor),
        minLabelRadius = minLabelRadius,
    )
}

data class SegmentationParams(
    /** Nombre de couleurs de la palette (avant suppression des couleurs inutilisées). */
    val colorCount: Int,
    /** Surface minimale d'une zone, en pixels. */
    val minRegionArea: Int,
    /** Épaisseur minimale d'une zone : on doit pouvoir la toucher et y écrire un numéro. */
    val minLabelRadius: Float = 3f,
    /** Passes de flou avant quantification, pour gommer la texture des coups de pinceau. */
    val smoothingPasses: Int = 2,
    /** Passes de filtre majoritaire après quantification, pour supprimer les grains isolés. */
    val modeFilterPasses: Int = 2,
    /** Graine du k-means : un même tableau donne toujours le même découpage. */
    val seed: Long = 7L,
)

/**
 * Transforme une image en toile « peinture par numéros ».
 *
 * 1. Lissage léger (flou boîte) pour ne pas découper chaque coup de pinceau.
 * 2. Quantification des couleurs par k-means++ dans l'espace Lab.
 * 3. Filtre majoritaire 3x3 pour nettoyer les pixels isolés.
 * 4. Composantes connexes (4-voisinage) : chaque tache de couleur devient une zone.
 * 5. Fusion itérative des zones trop petites ou trop fines dans leur voisine la plus
 *    proche (frontière commune longue et couleur semblable).
 * 6. Transformée de distance pour placer chaque numéro au cœur de sa zone.
 */
class Segmenter(private val params: SegmentationParams) {

    fun segment(argb: IntArray, width: Int, height: Int, onProgress: (Float) -> Unit = {}): PaintByNumber {
        val n = width * height
        require(argb.size == n) { "argb: ${argb.size} != $width x $height" }
        require(n > 0) { "image vide" }

        val smooth = boxBlur(argb, width, height, params.smoothingPasses)
        onProgress(0.08f)

        val lab = FloatArray(n * 3)
        for (i in 0 until n) ColorSpace.argbToLab(smooth[i], lab, i * 3)
        onProgress(0.15f)

        val centers = kMeans(lab, n, params.colorCount.coerceIn(2, 64))
        val k = centers.size / 3
        onProgress(0.35f)

        var quantized = assign(lab, n, centers, k)
        repeat(params.modeFilterPasses) { quantized = modeFilter(quantized, width, height, k) }
        onProgress(0.45f)

        val components = connectedComponents(quantized, width, height)
        onProgress(0.55f)

        val merged = mergeSmallRegions(components, quantized, width, height, centers, k, onProgress)
        onProgress(0.9f)

        return finish(merged, width, height, centers, k).also { onProgress(1f) }
    }

    // ---------------------------------------------------------------- 1. lissage

    private fun boxBlur(src: IntArray, w: Int, h: Int, passes: Int): IntArray {
        if (passes <= 0) return src
        val cur = src.copyOf()
        val tmp = IntArray(src.size)
        repeat(passes) {
            blurLine(cur, tmp, w, h, horizontal = true)
            blurLine(tmp, cur, w, h, horizontal = false)
        }
        return cur
    }

    private fun blurLine(src: IntArray, dst: IntArray, w: Int, h: Int, horizontal: Boolean) {
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0
                for (d in -1..1) {
                    val c = if (horizontal) src[y * w + (x + d).coerceIn(0, w - 1)]
                    else src[(y + d).coerceIn(0, h - 1) * w + x]
                    r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
                }
                dst[y * w + x] = (0xFF shl 24) or ((r / 3) shl 16) or ((g / 3) shl 8) or (b / 3)
            }
        }
    }

    // ---------------------------------------------------------------- 2. k-means++

    private fun kMeans(lab: FloatArray, n: Int, k: Int): FloatArray {
        val random = Random(params.seed)
        val m = min(n, SAMPLE_SIZE)
        val sample = IntArray(m) { if (m == n) it else random.nextInt(n) }

        // Initialisation k-means++ : chaque centre est tiré proportionnellement à D².
        val centers = FloatArray(k * 3)
        copyPoint(lab, sample[random.nextInt(m)], centers, 0)
        val dist = FloatArray(m) { Float.MAX_VALUE }
        for (c in 1 until k) {
            var total = 0.0
            for (s in 0 until m) {
                val d = sqDist(lab, sample[s] * 3, centers, (c - 1) * 3)
                if (d < dist[s]) dist[s] = d
                total += dist[s]
            }
            var target = random.nextDouble() * total
            var chosen = m - 1
            for (s in 0 until m) {
                target -= dist[s]
                if (target <= 0) { chosen = s; break }
            }
            copyPoint(lab, sample[chosen], centers, c * 3)
        }

        // Itérations de Lloyd sur l'échantillon.
        val owner = IntArray(m)
        val sums = DoubleArray(k * 3)
        val counts = IntArray(k)
        repeat(KMEANS_ITERATIONS) {
            sums.fill(0.0); counts.fill(0)
            for (s in 0 until m) {
                val p = sample[s] * 3
                val c = nearest(lab, p, centers, k)
                owner[s] = c
                sums[c * 3] += lab[p].toDouble()
                sums[c * 3 + 1] += lab[p + 1].toDouble()
                sums[c * 3 + 2] += lab[p + 2].toDouble()
                counts[c]++
            }
            var moved = 0f
            for (c in 0 until k) {
                if (counts[c] == 0) {
                    // Centre vide : on le replace sur le point le plus mal représenté.
                    var worst = 0; var worstD = -1f
                    for (s in 0 until m) {
                        val d = sqDist(lab, sample[s] * 3, centers, owner[s] * 3)
                        if (d > worstD) { worstD = d; worst = s }
                    }
                    copyPoint(lab, sample[worst], centers, c * 3)
                    moved = Float.MAX_VALUE
                    continue
                }
                for (j in 0..2) {
                    val v = (sums[c * 3 + j] / counts[c]).toFloat()
                    moved = max(moved, kotlin.math.abs(v - centers[c * 3 + j]))
                    centers[c * 3 + j] = v
                }
            }
            if (moved < 0.05f) return centers
        }
        return centers
    }

    private fun assign(lab: FloatArray, n: Int, centers: FloatArray, k: Int): IntArray =
        IntArray(n) { nearest(lab, it * 3, centers, k) }

    private fun nearest(lab: FloatArray, p: Int, centers: FloatArray, k: Int): Int {
        var best = 0
        var bestD = Float.MAX_VALUE
        for (c in 0 until k) {
            val d = sqDist(lab, p, centers, c * 3)
            if (d < bestD) { bestD = d; best = c }
        }
        return best
    }

    // ---------------------------------------------------------------- 3. filtre majoritaire

    private fun modeFilter(src: IntArray, w: Int, h: Int, k: Int): IntArray {
        val dst = IntArray(src.size)
        val counts = IntArray(k)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val own = src[y * w + x]
                var best = own
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy < 0 || yy >= h) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx < 0 || xx >= w) continue
                        counts[src[yy * w + xx]]++
                    }
                }
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy < 0 || yy >= h) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx < 0 || xx >= w) continue
                        val l = src[yy * w + xx]
                        if (counts[l] > counts[best]) best = l
                    }
                }
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy < 0 || yy >= h) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx < 0 || xx >= w) continue
                        counts[src[yy * w + xx]] = 0
                    }
                }
                dst[y * w + x] = best
            }
        }
        return dst
    }

    // ---------------------------------------------------------------- 4. composantes connexes

    private class Components(val ids: IntArray, val count: Int)

    private fun connectedComponents(colors: IntArray, w: Int, h: Int): Components {
        val n = colors.size
        val ids = IntArray(n) { -1 }
        val stack = IntArray(n)
        var count = 0
        for (seed in 0 until n) {
            if (ids[seed] != -1) continue
            val color = colors[seed]
            var top = 0
            stack[top++] = seed
            ids[seed] = count
            while (top > 0) {
                val p = stack[--top]
                val x = p % w
                if (x > 0) { val q = p - 1; if (ids[q] == -1 && colors[q] == color) { ids[q] = count; stack[top++] = q } }
                if (x < w - 1) { val q = p + 1; if (ids[q] == -1 && colors[q] == color) { ids[q] = count; stack[top++] = q } }
                if (p >= w) { val q = p - w; if (ids[q] == -1 && colors[q] == color) { ids[q] = count; stack[top++] = q } }
                if (p < n - w) { val q = p + w; if (ids[q] == -1 && colors[q] == color) { ids[q] = count; stack[top++] = q } }
            }
            count++
        }
        return Components(ids, count)
    }

    // ---------------------------------------------------------------- 5. fusion des petites zones

    private class Merged(val labels: IntArray, val color: IntArray, val count: Int)

    private fun mergeSmallRegions(
        components: Components,
        quantized: IntArray,
        w: Int,
        h: Int,
        centers: FloatArray,
        k: Int,
        onProgress: (Float) -> Unit,
    ): Merged {
        val n = quantized.size
        val c = components.count
        val parent = IntArray(c) { it }
        val area = IntArray(c)
        val color = IntArray(c)
        for (i in 0 until n) {
            val id = components.ids[i]
            area[id]++
            color[id] = quantized[i]
        }
        // Similarité entre couleurs de la palette : on préfère fusionner dans une voisine proche.
        val similarity = FloatArray(k * k)
        for (a in 0 until k) for (b in 0 until k) {
            val d = sqrt(sqDist(centers, a * 3, centers, b * 3))
            similarity[a * k + b] = 1f / (1f + d / 20f)
        }

        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var y = x
            while (parent[y] != r) { val next = parent[y]; parent[y] = r; y = next }
            return r
        }

        val current = IntArray(n)
        val mustMerge = BooleanArray(c)
        for (pass in 0 until MAX_MERGE_PASSES) {
            for (i in 0 until n) current[i] = find(components.ids[i])
            val radius = maxInscribedRadius(current, w, h, c)

            var any = false
            for (r in 0 until c) {
                mustMerge[r] = parent[r] == r && (area[r] < params.minRegionArea || radius[r] < params.minLabelRadius)
                any = any or mustMerge[r]
            }
            if (!any) break

            // Longueur de frontière commune entre chaque zone à fusionner et ses voisines.
            val borders = HashMap<Long, Int>()
            fun touch(a: Int, b: Int) {
                if (a == b) return
                if (mustMerge[a]) borders.merge((a.toLong() shl 32) or b.toLong(), 1, Int::plus)
                if (mustMerge[b]) borders.merge((b.toLong() shl 32) or a.toLong(), 1, Int::plus)
            }
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val a = current[row + x]
                    if (x < w - 1) touch(a, current[row + x + 1])
                    if (y < h - 1) touch(a, current[row + w + x])
                }
            }
            val candidates = HashMap<Int, MutableList<Long>>()
            for ((key, length) in borders) {
                val small = (key ushr 32).toInt()
                val neighbor = (key and 0xFFFFFFFFL).toInt()
                candidates.getOrPut(small) { ArrayList(4) }.add((neighbor.toLong() shl 32) or length.toLong())
            }

            var merges = 0
            for (small in candidates.keys.sortedBy { area[it] }) {
                if (find(small) != small) continue
                var best = -1
                var bestScore = -1f
                for (entry in candidates.getValue(small)) {
                    val neighbor = find((entry ushr 32).toInt())
                    if (neighbor == small) continue
                    val length = (entry and 0xFFFFFFFFL).toInt()
                    val score = length * similarity[color[small] * k + color[neighbor]]
                    if (score > bestScore) { bestScore = score; best = neighbor }
                }
                if (best < 0) continue
                parent[small] = best
                area[best] += area[small]
                merges++
            }
            onProgress(0.55f + 0.35f * (pass + 1) / MAX_MERGE_PASSES)
            if (merges == 0) break
        }

        // Renumérotation compacte des zones survivantes.
        val compact = IntArray(c) { -1 }
        var count = 0
        val labels = IntArray(n)
        val regionColor = ArrayList<Int>()
        for (i in 0 until n) {
            val root = find(components.ids[i])
            if (compact[root] == -1) {
                compact[root] = count++
                regionColor.add(color[root])
            }
            labels[i] = compact[root]
        }
        return Merged(labels, regionColor.toIntArray(), count)
    }

    // ---------------------------------------------------------------- 6. finalisation

    private fun finish(merged: Merged, w: Int, h: Int, centers: FloatArray, k: Int): PaintByNumber {
        // Palette : couleurs réellement utilisées, triées (gris d'abord, puis par teinte).
        val used = BooleanArray(k)
        for (col in merged.color) used[col] = true
        val order = (0 until k).filter { used[it] }.sortedWith(compareBy({ hueKey(centers, it) }, { -centers[it * 3] }))
        val remap = IntArray(k) { -1 }
        order.forEachIndexed { index, col -> remap[col] = index }
        val palette = IntArray(order.size) { i ->
            val col = order[i]
            ColorSpace.labToArgb(centers[col * 3], centers[col * 3 + 1], centers[col * 3 + 2])
        }
        val regionColor = IntArray(merged.count) { remap[merged.color[it]] }

        val dist = distanceToBorder(merged.labels, w, h)
        val labelX = FloatArray(merged.count)
        val labelY = FloatArray(merged.count)
        val labelRadius = FloatArray(merged.count) { -1f }
        for (i in merged.labels.indices) {
            val r = merged.labels[i]
            if (dist[i] > labelRadius[r]) {
                labelRadius[r] = dist[i]
                labelX[r] = (i % w) + 0.5f
                labelY[r] = (i / w) + 0.5f
            }
        }
        return PaintByNumber(w, h, merged.labels, palette, regionColor, labelX, labelY, labelRadius)
    }

    /** Clé de tri : les couleurs peu saturées d'abord, puis par angle de teinte. */
    private fun hueKey(centers: FloatArray, c: Int): Float {
        val a = centers[c * 3 + 1]
        val b = centers[c * 3 + 2]
        val chroma = sqrt(a * a + b * b)
        if (chroma < 12f) return -1f
        val angle = Math.toDegrees(atan2(b, a).toDouble()).toFloat()
        return (angle + 360f + 30f) % 360f // on commence par les rouges
    }

    private fun maxInscribedRadius(labels: IntArray, w: Int, h: Int, size: Int): FloatArray {
        val dist = distanceToBorder(labels, w, h)
        val radius = FloatArray(size)
        for (i in labels.indices) if (dist[i] > radius[labels[i]]) radius[labels[i]] = dist[i]
        return radius
    }

    companion object {
        private const val SAMPLE_SIZE = 24_000
        private const val KMEANS_ITERATIONS = 24
        private const val MAX_MERGE_PASSES = 24

        /**
         * Distance (chanfrein 1 / √2) de chaque pixel au bord de sa zone. Les pixels du bord
         * valent 1, ceux du bord de l'image aussi : un numéro n'est jamais collé au cadre.
         */
        fun distanceToBorder(labels: IntArray, w: Int, h: Int): FloatArray {
            val n = labels.size
            val inf = Float.MAX_VALUE / 4
            val d = FloatArray(n)
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                val l = labels[i]
                val edge = x == 0 || y == 0 || x == w - 1 || y == h - 1 ||
                    labels[i - 1] != l || labels[i + 1] != l || labels[i - w] != l || labels[i + w] != l
                d[i] = if (edge) 1f else inf
            }
            val diag = 1.4142135f
            for (y in 1 until h) for (x in 1 until w - 1) {
                val i = y * w + x
                var v = d[i]
                v = min(v, d[i - 1] + 1f)
                v = min(v, d[i - w] + 1f)
                v = min(v, d[i - w - 1] + diag)
                v = min(v, d[i - w + 1] + diag)
                d[i] = v
            }
            for (y in h - 2 downTo 0) for (x in w - 2 downTo 1) {
                val i = y * w + x
                var v = d[i]
                v = min(v, d[i + 1] + 1f)
                v = min(v, d[i + w] + 1f)
                v = min(v, d[i + w + 1] + diag)
                v = min(v, d[i + w - 1] + diag)
                d[i] = v
            }
            return d
        }

        private fun sqDist(a: FloatArray, i: Int, b: FloatArray, j: Int): Float {
            val d0 = a[i] - b[j]
            val d1 = a[i + 1] - b[j + 1]
            val d2 = a[i + 2] - b[j + 2]
            return d0 * d0 + d1 * d1 + d2 * d2
        }

        private fun copyPoint(src: FloatArray, index: Int, dst: FloatArray, offset: Int) {
            dst[offset] = src[index * 3]
            dst[offset + 1] = src[index * 3 + 1]
            dst[offset + 2] = src[index * 3 + 2]
        }
    }
}
