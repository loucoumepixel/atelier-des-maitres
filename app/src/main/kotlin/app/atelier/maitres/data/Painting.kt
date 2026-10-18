package app.atelier.maitres.data

import android.content.res.Resources
import app.atelier.maitres.R

/** Une toile de la galerie : œuvre du domaine public fournie, ou image importée. */
data class Painting(
    val id: String,
    val title: String,
    val artist: String,
    val year: String,
    val note: String,
    val source: Source,
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
    fun paintings(resources: Resources): List<Painting> = listOf(
        Painting(
            id = "nuit_etoilee",
            title = resources.getString(R.string.nuit_etoilee_title),
            artist = "Vincent van Gogh",
            year = "1889",
            note = resources.getString(R.string.nuit_etoilee_note),
            source = Painting.Source.Asset("paintings/nuit_etoilee.jpg"),
        ),
        Painting(
            id = "champ_de_ble_cypres",
            title = resources.getString(R.string.champ_de_ble_cypres_title),
            artist = "Vincent van Gogh",
            year = "1889",
            note = resources.getString(R.string.champ_de_ble_cypres_note),
            source = Painting.Source.Asset("paintings/champ_de_ble_cypres.jpg"),
        ),
        Painting(
            id = "cafe_de_nuit",
            title = resources.getString(R.string.cafe_de_nuit_title),
            artist = "Vincent van Gogh",
            year = "1888",
            note = resources.getString(R.string.cafe_de_nuit_note),
            source = Painting.Source.Asset("paintings/cafe_de_nuit.jpg"),
        ),
        Painting(
            id = "maisons_personnage",
            title = resources.getString(R.string.maisons_personnage_title),
            artist = "Vincent van Gogh",
            year = "1890",
            note = resources.getString(R.string.maisons_personnage_note),
            source = Painting.Source.Asset("paintings/maisons_personnage.jpg"),
        ),
        Painting(
            id = "grande_vague",
            title = resources.getString(R.string.grande_vague_title),
            artist = "Katsushika Hokusai",
            year = resources.getString(R.string.grande_vague_year),
            note = resources.getString(R.string.grande_vague_note),
            source = Painting.Source.Asset("paintings/grande_vague.jpg"),
        ),
        Painting(
            id = "naufrage_minotaure",
            title = resources.getString(R.string.naufrage_minotaure_title),
            artist = "J. M. W. Turner",
            year = resources.getString(R.string.naufrage_minotaure_year),
            note = resources.getString(R.string.naufrage_minotaure_note),
            source = Painting.Source.Asset("paintings/naufrage_minotaure.jpg"),
        ),
        Painting(
            id = "le_cri",
            title = resources.getString(R.string.le_cri_title),
            artist = "Edvard Munch",
            year = "1893",
            note = resources.getString(R.string.le_cri_note),
            source = Painting.Source.Asset("paintings/le_cri.jpg"),
        ),
    )
}
