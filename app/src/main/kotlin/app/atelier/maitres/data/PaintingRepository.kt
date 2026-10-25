package app.atelier.maitres.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.AtomicFile
import android.util.LruCache
import androidx.core.content.FileProvider
import app.atelier.maitres.R
import app.atelier.maitres.core.ColourMigration
import app.atelier.maitres.core.Difficulty
import app.atelier.maitres.core.OilRelief
import app.atelier.maitres.core.PaintByNumber
import app.atelier.maitres.core.PbnCodec
import app.atelier.maitres.core.Segmenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** Avancement d'une toile à un niveau donné. */
data class PaintingStats(val difficulty: Difficulty, val faithful: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else faithful.toFloat() / total
    val isComplete: Boolean get() = total > 0 && faithful == total
}

/** Toile prête à peindre : son découpage, l'image d'origine et la progression enregistrée. */
class LoadedCanvas(
    val painting: Painting,
    val difficulty: Difficulty,
    val canvas: PaintByNumber,
    val original: Bitmap,
    val savedFills: IntArray?,
    /** La matière de peinture à l'huile, ou null si elle n'a pas pu être préparée. */
    val relief: OilRelief?,
)

/**
 * Source unique de vérité pour les toiles : catalogue, imports, découpage (mis en cache
 * sur disque), progression et statistiques. Toutes les opérations lourdes sont `suspend`
 * et s'exécutent hors du thread principal.
 */
class PaintingRepository(private val context: Context) {

    private val canvasDir = File(context.filesDir, "canvas").apply { mkdirs() }
    private val progressDir = File(context.filesDir, "progress").apply { mkdirs() }
    private val importDir = File(context.filesDir, "imports").apply { mkdirs() }
    private val reliefDir = File(context.cacheDir, "relief").apply { mkdirs() }
    private val importIndex = AtomicFile(File(importDir, "index.json"))
    private val prefs = context.getSharedPreferences("atelier", Context.MODE_PRIVATE)

    /** Les sauvegardes survivent à l'écran qui les a demandées : elles tournent ici. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val saveLock = Mutex()

    private val thumbnails = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private val _paintings = MutableStateFlow(MasterCatalog.paintings(context.resources) + readImports())
    val paintings: StateFlow<List<Painting>> = _paintings.asStateFlow()

    /** Incrémenté à chaque sauvegarde, pour que la galerie rafraîchisse ses pourcentages. */
    private val _statsVersion = MutableStateFlow(0)
    val statsVersion: StateFlow<Int> = _statsVersion.asStateFlow()

    fun painting(id: String): Painting? = _paintings.value.firstOrNull { it.id == id }

    // ------------------------------------------------------------------ images

    suspend fun thumbnail(painting: Painting): Bitmap = withContext(Dispatchers.IO) {
        thumbnails.get(painting.id) ?: decode(painting, THUMBNAIL_SIZE).also { thumbnails.put(painting.id, it) }
    }

    private fun decode(painting: Painting, longSide: Int): Bitmap {
        val source = when (val s = painting.source) {
            is Painting.Source.Asset -> ImageDecoder.createSource(context.assets, s.path)
            is Painting.Source.File -> ImageDecoder.createSource(File(s.path))
        }
        return decodeScaled(source, longSide)
    }

    private fun decodeScaled(source: ImageDecoder.Source, longSide: Int): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = longSide.toFloat() / max(info.size.width, info.size.height)
            decoder.setTargetSize(
                max(1, (info.size.width * scale).roundToInt()),
                max(1, (info.size.height * scale).roundToInt()),
            )
            // Pixels lisibles par getPixels() : pas de bitmap « hardware ».
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }.let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false) }

    // ------------------------------------------------------------------ découpage

    /**
     * Charge (ou calcule la première fois) la toile découpée. Le découpage prend environ
     * une seconde sur un téléphone récent, puis il est relu depuis le cache.
     */
    suspend fun load(painting: Painting, difficulty: Difficulty, onProgress: (Float) -> Unit): LoadedCanvas =
        withContext(Dispatchers.Default) {
            val original = decode(painting, WORKING_SIZE)
            // La matière d'huile se prépare en même temps que le découpage (une fois par toile).
            val relief = async { relief(painting, original) }
            val cache = canvasFile(painting, difficulty)
            val cached = runCatching { cache.inputStream().use(PbnCodec::readCanvas) }
                .getOrNull()
                ?.takeIf { it.width == original.width && it.height == original.height }

            val canvas = cached ?: run {
                val pixels = IntArray(original.width * original.height)
                original.getPixels(pixels, 0, original.width, 0, 0, original.width, original.height)
                Segmenter(difficulty.params(original.width, original.height))
                    .segment(pixels, original.width, original.height, onProgress)
                    .also { result -> AtomicFile(cache).write { PbnCodec.writeCanvas(result, it) } }
            }
            val fills = runCatching { progressFile(painting, difficulty).readFully().inputStream().use(PbnCodec::readProgress) }
                .getOrNull()
                ?.takeIf { it.size == canvas.regionCount }
                ?.let { restoreColours(painting, difficulty, canvas, original, it) }
            LoadedCanvas(painting, difficulty, canvas, original, fills, relief.await())
        }

    /** Relief de peinture à l'huile de la toile, relu du cache ou calculé la première fois. */
    private fun relief(painting: Painting, original: Bitmap): OilRelief? = runCatching {
        val file = File(reliefDir, "${painting.id}_v$RELIEF_FORMAT.oil")
        runCatching { file.inputStream().use(OilRelief::read) }.getOrNull()
            ?.takeIf { it.width == original.width && it.height == original.height }
            ?: run {
                val pixels = IntArray(original.width * original.height)
                original.getPixels(pixels, 0, original.width, 0, 0, original.width, original.height)
                OilRelief.compute(pixels, original.width, original.height)
                    .also { result -> AtomicFile(file).write { result.write(it) } }
            }
    }.getOrNull()

    /**
     * Après une mise à jour qui ravive les couleurs, les zones restent les mêmes mais la palette
     * change : les touches déjà posées prennent la nouvelle teinte de leur couleur, pour que la
     * progression reste fidèle (sinon elles s'affichent comme des erreurs).
     *
     * L'ancien découpage sert de correspondance s'il est encore sur le disque ; sinon on le
     * recalcule une fois avec les réglages d'avant. La progression corrigée est enregistrée.
     */
    private fun restoreColours(painting: Painting, difficulty: Difficulty, canvas: PaintByNumber, original: Bitmap, fills: IntArray): IntArray {
        val legacyFiles = (1 until CANVAS_FORMAT).map { File(canvasDir, "${painting.id}_${difficulty.name}_v$it.pbn") }
        val repairedKey = "colours_v${CANVAS_FORMAT}_${painting.id}_${difficulty.name}"
        if (!ColourMigration.hasForeignColours(canvas, fills) || prefs.getBoolean(repairedKey, false)) {
            legacyFiles.forEach { it.delete() }
            return fills
        }
        val onDisk = legacyFiles.firstNotNullOfOrNull { f ->
            runCatching { f.inputStream().use(PbnCodec::readCanvas) }.getOrNull()
        }
        val legacy = onDisk?.takeIf { it.labels.contentEquals(canvas.labels) } ?: run {
            val pixels = IntArray(original.width * original.height)
            original.getPixels(pixels, 0, original.width, 0, 0, original.width, original.height)
            Segmenter(ColourMigration.legacyParams(difficulty.params(original.width, original.height)))
                .segment(pixels, original.width, original.height)
        }
        val result = ColourMigration.carryOver(legacy, canvas, fills) ?: fills
        legacyFiles.forEach { it.delete() }
        prefs.edit().putBoolean(repairedKey, true).apply()
        if (!result.contentEquals(fills)) {
            val faithful = result.indices.count { result[it] == canvas.referenceColor(it) }
            saveProgress(painting, difficulty, result.copyOf(), faithful)
        }
        return result
    }

    // ------------------------------------------------------------------ progression

    /**
     * Enregistre la progression sans bloquer l'appelant. [fills] doit être une copie :
     * l'écran continue de peindre pendant l'écriture.
     */
    fun saveProgress(painting: Painting, difficulty: Difficulty, fills: IntArray, faithful: Int) {
        appScope.launch {
            saveLock.withLock {
                val written = runCatching { progressFile(painting, difficulty).write { PbnCodec.writeProgress(fills, it) } }
                if (written.isFailure) return@withLock
                prefs.edit()
                    .putString(statsKey(painting, difficulty), "$faithful/${fills.size}")
                    .putString(lastKey(painting), difficulty.name)
                    .apply()
                _statsVersion.value++
            }
        }
    }

    fun resetProgress(painting: Painting, difficulty: Difficulty) {
        appScope.launch {
            saveLock.withLock {
                progressFile(painting, difficulty).delete()
                prefs.edit().remove(statsKey(painting, difficulty)).apply()
                _statsVersion.value++
            }
        }
    }

    fun stats(painting: Painting): List<PaintingStats> = Difficulty.entries.mapNotNull { difficulty ->
        val raw = prefs.getString(statsKey(painting, difficulty), null) ?: return@mapNotNull null
        val (faithful, total) = raw.split('/').map { it.toIntOrNull() ?: 0 }
        PaintingStats(difficulty, faithful, total)
    }

    /** Dernier niveau joué sur cette toile, pour la reprendre en un geste. */
    fun lastDifficulty(painting: Painting): Difficulty? =
        prefs.getString(lastKey(painting), null)?.let { name -> Difficulty.entries.firstOrNull { it.name == name } }

    private fun statsKey(p: Painting, d: Difficulty) = "stats_${p.id}_${d.name}"
    private fun lastKey(p: Painting) = "last_${p.id}"

    private fun canvasFile(p: Painting, d: Difficulty) = File(canvasDir, "${p.id}_${d.name}_v$CANVAS_FORMAT.pbn")
    private fun progressFile(p: Painting, d: Difficulty) = AtomicFile(File(progressDir, "${p.id}_${d.name}.prg"))

    // ------------------------------------------------------------------ imports

    /** Importe une image de la galerie du téléphone comme nouvelle toile. */
    suspend fun import(uri: Uri): Painting = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(ImageDecoder.createSource(context.contentResolver, uri), IMPORT_SIZE)
        val id = "import_" + UUID.randomUUID().toString().take(8)
        val file = File(importDir, "$id.jpg")
        AtomicFile(file).write { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        // Numéro suivant le plus grand déjà attribué : jamais deux « Ma toile n° 2 », même après une suppression.
        val count = _paintings.value.filter { it.isImported }
            .mapNotNull { IMPORT_NUMBER.find(it.title)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull().let { (it ?: 0) + 1 }
        val painting = Painting(
            id = id,
            title = context.getString(R.string.imported_title, count),
            artist = context.getString(R.string.imported_artist),
            year = "",
            note = context.getString(R.string.imported_note),
            source = Painting.Source.File(file.path),
        )
        _paintings.value = _paintings.value + painting
        writeImports()
        painting
    }

    suspend fun delete(painting: Painting) = withContext(Dispatchers.IO) {
        val source = painting.source as? Painting.Source.File ?: return@withContext
        File(source.path).delete()
        Difficulty.entries.forEach { d ->
            canvasFile(painting, d).delete()
            resetProgress(painting, d)
        }
        prefs.edit().remove(lastKey(painting)).apply()
        reliefDir.listFiles { f -> f.name.startsWith("${painting.id}_") }?.forEach { it.delete() }
        thumbnails.remove(painting.id)
        _paintings.value = _paintings.value.filterNot { it.id == painting.id }
        writeImports()
    }

    private fun readImports(): List<Painting> = runCatching {
        val array = JSONArray(String(importIndex.readFully()))
        List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Painting(
                id = o.getString("id"),
                title = o.getString("title"),
                artist = o.optString("artist", context.getString(R.string.imported_artist)),
                year = o.optString("year"),
                note = o.optString("note"),
                // Nom de fichier relatif : le chemin absolu change d'un appareil (ou d'un profil
                // utilisateur) à l'autre, ce qui casserait la restauration d'une sauvegarde Android.
                source = Painting.Source.File(File(importDir, o.optString("file").ifEmpty { File(o.getString("path")).name }).path),
            )
        }.filter { File((it.source as Painting.Source.File).path).exists() }
    }.getOrDefault(emptyList())

    private fun writeImports() {
        val array = JSONArray()
        _paintings.value.filter { it.isImported }.forEach { p ->
            array.put(
                JSONObject()
                    .put("id", p.id).put("title", p.title).put("artist", p.artist)
                    .put("year", p.year).put("note", p.note)
                    .put("file", File((p.source as Painting.Source.File).path).name),
            )
        }
        importIndex.write { it.write(array.toString().toByteArray()) }
    }

    // ------------------------------------------------------------------ partage

    /**
     * Enregistre [image] en PNG dans le cache et renvoie une adresse que les autres
     * applications peuvent lire le temps du partage (aucune autorisation de stockage requise).
     */
    suspend fun shareableImage(painting: Painting, image: Bitmap): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "partage").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val name = painting.title.replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifEmpty { "toile" }
        val file = File(dir, "$name.png")
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.partage", file)
    }

    private inline fun AtomicFile.write(block: (java.io.OutputStream) -> Unit) {
        val stream = startWrite()
        try {
            block(stream)
            finishWrite(stream)
        } catch (e: Exception) {
            failWrite(stream)
            throw if (e is IOException) e else IOException(e)
        }
    }

    companion object {
        /** Côté long de l'image de travail : assez fin pour des contours nets une fois zoomé. */
        const val WORKING_SIZE = 1200
        private const val THUMBNAIL_SIZE = 480
        private const val IMPORT_SIZE = 1600
        /** Numéro final d'un titre d'import, quelle que soit la langue (« Ma toile n° 3 », « My canvas no. 3 »). */
        private val IMPORT_NUMBER = Regex("(\\d+)$")

        /**
         * À incrémenter si l'algorithme de découpage change, pour invalider le cache.
         * 2 : couleurs de palette ravivées (mêmes zones qu'en 1).
         */
        private const val CANVAS_FORMAT = 2

        /** À incrémenter si le calcul de la matière d'huile change. */
        private const val RELIEF_FORMAT = 1
    }
}
