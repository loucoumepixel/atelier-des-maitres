package app.atelier.maitres.data

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
 */
object MasterCatalog {
    val paintings: List<Painting> = listOf(
        Painting(
            id = "nuit_etoilee",
            title = "La Nuit étoilée",
            artist = "Vincent van Gogh",
            year = "1889",
            note = "Peinte depuis sa chambre de l'asile de Saint-Rémy-de-Provence, " +
                "juste avant le lever du soleil. Le village, lui, est imaginé.",
            source = Painting.Source.Asset("paintings/nuit_etoilee.jpg"),
        ),
        Painting(
            id = "champ_de_ble_cypres",
            title = "Champ de blé avec cyprès",
            artist = "Vincent van Gogh",
            year = "1889",
            note = "Van Gogh voyait dans les cyprès « l'équivalent d'un obélisque égyptien ». " +
                "Il peignit trois versions de ce paysage.",
            source = Painting.Source.Asset("paintings/champ_de_ble_cypres.jpg"),
        ),
        Painting(
            id = "cafe_de_nuit",
            title = "Le Café de nuit",
            artist = "Vincent van Gogh",
            year = "1888",
            note = "« J'ai cherché à exprimer avec le rouge et le vert les terribles passions " +
                "humaines », écrit-il à son frère Théo.",
            source = Painting.Source.Asset("paintings/cafe_de_nuit.jpg"),
        ),
        Painting(
            id = "maisons_personnage",
            title = "Maisons et personnage",
            artist = "Vincent van Gogh",
            year = "1890",
            note = "Un souvenir du Nord peint à Saint-Rémy : chaumières, fumées en volutes " +
                "et soleil couchant.",
            source = Painting.Source.Asset("paintings/maisons_personnage.jpg"),
        ),
        Painting(
            id = "grande_vague",
            title = "La Grande Vague de Kanagawa",
            artist = "Katsushika Hokusai",
            year = "vers 1831",
            note = "Estampe des Trente-six vues du mont Fuji. Le bleu de Prusse, alors nouveau " +
                "au Japon, y fait une entrée fracassante.",
            source = Painting.Source.Asset("paintings/grande_vague.jpg"),
        ),
        Painting(
            id = "naufrage_minotaure",
            title = "Le Naufrage du Minotaure",
            artist = "J. M. W. Turner",
            year = "vers 1810",
            note = "Turner, peintre de la lumière et des tempêtes, annonce ici les " +
                "impressionnistes de plus d'un demi-siècle.",
            source = Painting.Source.Asset("paintings/naufrage_minotaure.jpg"),
        ),
        Painting(
            id = "le_cri",
            title = "Le Cri",
            artist = "Edvard Munch",
            year = "1893",
            note = "« Je sentis un cri infini qui traversait la nature. » Le ciel rouge " +
                "serait celui d'un crépuscule sur Oslo.",
            source = Painting.Source.Asset("paintings/le_cri.jpg"),
        ),
    )
}
