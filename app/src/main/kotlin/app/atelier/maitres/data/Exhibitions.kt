package app.atelier.maitres.data

import androidx.annotation.StringRes
import app.atelier.maitres.R
import java.time.LocalDate

/**
 * Les cadres du musée : trois offerts d'emblée, un baroque pour les grands collectionneurs,
 * et un cadre propre à chaque exposition, gagné en la terminant.
 *
 * [wood] est la teinte du bois (ou de la dorure), [light] celle de ses arêtes éclairées.
 */
enum class Frame(@StringRes val labelRes: Int, val wood: Long, val light: Long, val ornate: Boolean = false) {
    CHENE(R.string.frame_chene, 0xFFB98A55, 0xFFE0BC88),
    NOYER(R.string.frame_noyer, 0xFF5C3A22, 0xFF8E6343),
    DORE(R.string.frame_dore, 0xFFC49A3E, 0xFFF3D98A),
    BAROQUE(R.string.frame_baroque, 0xFFB0802A, 0xFFFFE29A, ornate = true),
    TOURNESOL(R.string.frame_tournesol, 0xFFE2A82B, 0xFFFFDD6E),
    ECUME(R.string.frame_ecume, 0xFF2F6E86, 0xFFBFE6EE),
    NENUPHAR(R.string.frame_nenuphar, 0xFF5E8A62, 0xFFC9E3B5),
    NOCTURNE(R.string.frame_nocturne, 0xFF22264A, 0xFF7A80C2),
    VELOURS(R.string.frame_velours, 0xFF7A2333, 0xFFC4606F),
    ZINC(R.string.frame_zinc, 0xFF7D858A, 0xFFD3D9DC),
    LAQUE(R.string.frame_laque, 0xFF1E1A18, 0xFFB5432F),
    VIGNE(R.string.frame_vigne, 0xFF6B3A55, 0xFFB58AA3);

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
