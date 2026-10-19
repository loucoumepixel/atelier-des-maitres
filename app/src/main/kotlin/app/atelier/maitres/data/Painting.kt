package app.atelier.maitres.data

import android.content.res.Resources
import androidx.annotation.StringRes
import app.atelier.maitres.R

/** Une toile de la galerie : œuvre du domaine public fournie, ou image importée. */
data class Painting(
    val id: String,
    val title: String,
    val artist: String,
    val year: String,
    val note: String,
    val source: Source,
    /** Rang du peintre à atteindre pour déballer la toile (0 : disponible d'emblée). */
    val unlockRank: Int = 0,
    /** Petit nom du maître, pour le féliciter à la fin (« Bravo, Vincent ! »). */
    val nickname: String? = null,
) {
    sealed interface Source {
        /** Image embarquée dans l'APK, sous `assets/`. */
        data class Asset(val path: String) : Source

        /** Image importée par l'utilisateur, copiée dans le stockage privé de l'application. */
        data class File(val path: String) : Source
    }

    val isImported: Boolean get() = source is Source.File
}

/**
 * Les toiles fournies. Toutes sont dans le domaine public (auteurs morts depuis plus
 * de 70 ans) ; les reproductions proviennent de numérisations publiques.
 *
 * Titres, dates approximatives et cartels sont traduits (`res/values/strings.xml`, `res/values-fr/strings.xml`) :
 * le catalogue se construit donc à partir des ressources de l'appareil.
 */
object MasterCatalog {
    /**
     * Une ligne du catalogue. Les textes traduits sont des ressources ; le peintre, l'année et
     * le petit nom sont soit un texte, soit une ressource quand ils changent avec la langue.
     */
    private class Entry(
        val id: String,
        @StringRes val title: Int,
        @StringRes val note: Int,
        val artist: Any,
        val year: Any,
        val unlockRank: Int = 0,
        val nickname: Any,
    )

    private val entries = listOf(
        Entry("nuit_etoilee", R.string.nuit_etoilee_title, R.string.nuit_etoilee_note, artist = "Vincent van Gogh", year = "1889", nickname = "Vincent"),
        Entry("champ_de_ble_cypres", R.string.champ_de_ble_cypres_title, R.string.champ_de_ble_cypres_note, artist = "Vincent van Gogh", year = "1889", nickname = "Vincent"),
        Entry("cafe_de_nuit", R.string.cafe_de_nuit_title, R.string.cafe_de_nuit_note, artist = "Vincent van Gogh", year = "1888", nickname = "Vincent"),
        Entry("maisons_personnage", R.string.maisons_personnage_title, R.string.maisons_personnage_note, artist = "Vincent van Gogh", year = "1890", nickname = "Vincent"),
        Entry("grande_vague", R.string.grande_vague_title, R.string.grande_vague_note, artist = "Katsushika Hokusai", year = R.string.grande_vague_year, nickname = "Hokusai"),
        Entry("monet_impression", R.string.monet_impression_title, R.string.monet_impression_note, artist = "Claude Monet", year = "1872", nickname = "Claude"),
        Entry("monet_nympheas", R.string.monet_nympheas_title, R.string.monet_nympheas_note, artist = "Claude Monet", year = "1906", nickname = "Claude"),
        Entry("monet_prairie", R.string.monet_prairie_title, R.string.monet_prairie_note, artist = "Claude Monet", year = "1886", nickname = "Claude"),
        Entry("panier_pommes", R.string.panier_pommes_title, R.string.panier_pommes_note, artist = "Paul Cézanne", year = R.string.panier_pommes_year, nickname = "Paul"),
        Entry("voyageur_brume", R.string.voyageur_brume_title, R.string.voyageur_brume_note, artist = "Caspar David Friedrich", year = R.string.voyageur_brume_year, nickname = "Caspar"),
        Entry("pont_neuf", R.string.pont_neuf_title, R.string.pont_neuf_note, artist = "Pierre-Auguste Renoir", year = "1872", nickname = "Auguste"),
        Entry("grande_jatte", R.string.grande_jatte_title, R.string.grande_jatte_note, artist = "Georges Seurat", year = "1884-1886", nickname = "Georges"),
        Entry("noce_juive", R.string.noce_juive_title, R.string.noce_juive_note, artist = "Eugène Delacroix", year = R.string.noce_juive_year, nickname = "Eugène"),
        Entry("vermeer_laitiere", R.string.vermeer_laitiere_title, R.string.vermeer_laitiere_note, artist = "Johannes Vermeer", year = R.string.vermeer_laitiere_year, nickname = "Johannes"),
        Entry("american_gothic", R.string.american_gothic_title, R.string.american_gothic_note, artist = "Grant Wood", year = "1930", nickname = "Grant"),
        Entry("naufrage_minotaure", R.string.naufrage_minotaure_title, R.string.naufrage_minotaure_note, artist = "J. M. W. Turner", year = R.string.naufrage_minotaure_year, unlockRank = 1, nickname = "William"),
        Entry("le_cri", R.string.le_cri_title, R.string.le_cri_note, artist = "Edvard Munch", year = "1893", unlockRank = 2, nickname = "Edvard"),
        Entry("liberte_peuple", R.string.liberte_peuple_title, R.string.liberte_peuple_note, artist = "Eugène Delacroix", year = "1830", unlockRank = 3, nickname = "Eugène"),
        Entry("naissance_venus", R.string.naissance_venus_title, R.string.naissance_venus_note, artist = "Sandro Botticelli", year = R.string.naissance_venus_year, unlockRank = 4, nickname = "Sandro"),
        Entry("joconde", R.string.joconde_title, R.string.joconde_note, artist = R.string.joconde_artist, year = "1503-1519", unlockRank = 5, nickname = R.string.joconde_nickname),
    )

    fun paintings(resources: Resources): List<Painting> = entries.map { e ->
        fun text(value: Any) = (value as? Int)?.let { resources.getString(it) } ?: value as String
        Painting(
            id = e.id,
            title = resources.getString(e.title),
            artist = text(e.artist),
            year = text(e.year),
            note = resources.getString(e.note),
            source = Painting.Source.Asset("paintings/${e.id}.jpg"),
            unlockRank = e.unlockRank,
            nickname = text(e.nickname),
        )
    }
}
