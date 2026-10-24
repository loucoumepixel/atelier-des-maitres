package app.atelier.maitres.data

import androidx.annotation.StringRes
import app.atelier.maitres.R
import java.time.LocalDate

/**
 * Les cadres du musée : trois offerts d'emblée, un baroque pour les grands collectionneurs,
 * et un cadre propre à chaque exposition, gagné en la terminant.
 *
 * [wood] est la teinte du bois (ou de la dorure), avant l'éclairage de la moulure.
 */
enum class Frame(@StringRes val labelRes: Int, val wood: Long, val style: Style = Style.DORURE) {
    CHENE(R.string.frame_chene, 0xFFA87847, Style.BOIS),
    NOYER(R.string.frame_noyer, 0xFF54331F, Style.BOIS),
    DORE(R.string.frame_dore, 0xFFC79942),
    BAROQUE(R.string.frame_baroque, 0xFFCC993D, Style.BAROQUE),
    TOURNESOL(R.string.frame_tournesol, 0xFFE0A62A),
    ECUME(R.string.frame_ecume, 0xFF40859E),
    NENUPHAR(R.string.frame_nenuphar, 0xFF6E9A6A),
    NOCTURNE(R.string.frame_nocturne, 0xFF3A3F78),
    VELOURS(R.string.frame_velours, 0xFF8E2A3B),
    ZINC(R.string.frame_zinc, 0xFF9AA2A7),
    LAQUE(R.string.frame_laque, 0xFFB5432F),
    VIGNE(R.string.frame_vigne, 0xFF7E4766);

    /** La matière de la moulure : bois veiné, dorure qui accroche la lumière, ou dorure sculptée. */
    enum class Style { BOIS, DORURE, BAROQUE }

    companion object {
        /** Les cadres offerts dès la première toile accrochée. */
        val FREE = listOf(CHENE, NOYER, DORE)

        /** Toiles terminées qu'il faut pour le cadre baroque. */
        const val BAROQUE_AT = 10
    }
}

/** Une exposition : quelques toiles réunies autour d'un thème, le temps d'un mois. */
class Exhibition(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val textRes: Int,
    val paintings: List<String>,
    val frame: Frame,
)

/**
 * Le programme des expositions : une par mois, toujours la même pour tout le monde ce mois-là,
 * et le programme recommence quand il est épuisé.
 */
object Exhibitions {
    /** Touches offertes en plus du cadre quand on termine l'exposition. */
    const val BONUS = 300

    val ALL = listOf(
        Exhibition("vincent", R.string.expo_vincent, R.string.expo_vincent_text,
            listOf("nuit_etoilee", "champ_de_ble_cypres", "cafe_de_nuit", "maisons_personnage"), Frame.TOURNESOL),
        Exhibition("mer", R.string.expo_mer, R.string.expo_mer_text,
            listOf("grande_vague", "naufrage_minotaure", "monet_impression", "voyageur_brume"), Frame.ECUME),
        Exhibition("impressionnistes", R.string.expo_impressionnistes, R.string.expo_impressionnistes_text,
            listOf("monet_nympheas", "monet_prairie", "pont_neuf", "grande_jatte"), Frame.NENUPHAR),
        Exhibition("crepuscule", R.string.expo_crepuscule, R.string.expo_crepuscule_text,
            listOf("monet_impression", "nuit_etoilee", "cafe_de_nuit", "le_cri"), Frame.NOCTURNE),
        Exhibition("visages", R.string.expo_visages, R.string.expo_visages_text,
            listOf("joconde", "vermeer_laitiere", "american_gothic", "le_cri"), Frame.VELOURS),
        Exhibition("paris", R.string.expo_paris, R.string.expo_paris_text,
            listOf("pont_neuf", "grande_jatte", "liberte_peuple"), Frame.ZINC),
        Exhibition("voyages", R.string.expo_voyages, R.string.expo_voyages_text,
            listOf("grande_vague", "noce_juive", "voyageur_brume", "american_gothic"), Frame.LAQUE),
        Exhibition("natures", R.string.expo_natures, R.string.expo_natures_text,
            listOf("panier_pommes", "monet_nympheas", "champ_de_ble_cypres", "naissance_venus"), Frame.VIGNE),
    )

    fun current(date: LocalDate = LocalDate.now()): Exhibition =
        ALL[Math.floorMod(date.year * 12 + date.monthValue - 1, ALL.size)]

    /** Jours restants avant le décrochage, aujourd'hui compris. */
    fun daysLeft(date: LocalDate = LocalDate.now()): Int = date.lengthOfMonth() - date.dayOfMonth + 1

    /** Clé du mois en cours, pour retenir les toiles finies pendant l'exposition. */
    fun monthKey(date: LocalDate = LocalDate.now()): String = "%04d-%02d".format(date.year, date.monthValue)
}
