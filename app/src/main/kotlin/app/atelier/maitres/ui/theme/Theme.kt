package app.atelier.maitres.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import app.atelier.maitres.R

/** Couleurs inspirées des pigments historiques : outremer, ocre, vermillon, papier. */
object Pigments {
    val Ultramarine = Color(0xFF1F3A68)
    val UltramarineLight = Color(0xFF9DB4E0)
    val Ochre = Color(0xFFC8952B)
    val OchreLight = Color(0xFFE8C77A)
    val Vermilion = Color(0xFFB8432F)
    val Paper = Color(0xFFF6F1E7)
    val PaperBright = Color(0xFFFFFBF5)
    val Ink = Color(0xFF231F1A)
    val Gilt = Color(0xFFB8913D)
    val Night = Color(0xFF14161B)
    val NightSurface = Color(0xFF1D2027)
}

private val LightColors = lightColorScheme(
    primary = Pigments.Ultramarine,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2F5),
    onPrimaryContainer = Color(0xFF0D1F3D),
    secondary = Pigments.Ochre,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E2BC),
    onSecondaryContainer = Color(0xFF3B2A06),
    tertiary = Pigments.Vermilion,
    background = Pigments.Paper,
    onBackground = Pigments.Ink,
    surface = Pigments.Paper,
    onSurface = Pigments.Ink,
    surfaceVariant = Color(0xFFEAE3D6),
    onSurfaceVariant = Color(0xFF5C554B),
    surfaceContainerLowest = Pigments.PaperBright,
    surfaceContainerLow = Color(0xFFF9F5EE),
    surfaceContainer = Color(0xFFF1EBE0),
    surfaceContainerHigh = Color(0xFFEBE4D8),
    surfaceContainerHighest = Color(0xFFE4DCCE),
    outline = Color(0xFF8C8478),
    outlineVariant = Color(0xFFD6CEC0),
)

private val DarkColors = darkColorScheme(
    primary = Pigments.UltramarineLight,
    onPrimary = Color(0xFF0D1F3D),
    primaryContainer = Color(0xFF2C4675),
    onPrimaryContainer = Color(0xFFD9E2F5),
    secondary = Pigments.OchreLight,
    onSecondary = Color(0xFF3B2A06),
    secondaryContainer = Color(0xFF5A4413),
    onSecondaryContainer = Color(0xFFF3E2BC),
    tertiary = Color(0xFFF0A08F),
    background = Pigments.Night,
    onBackground = Color(0xFFECE6DC),
    surface = Pigments.Night,
    onSurface = Color(0xFFECE6DC),
    surfaceVariant = Color(0xFF2B2E36),
    onSurfaceVariant = Color(0xFFC4BDB2),
    surfaceContainerLowest = Color(0xFF0F1115),
    surfaceContainerLow = Color(0xFF191B21),
    surfaceContainer = Pigments.NightSurface,
    surfaceContainerHigh = Color(0xFF262931),
    surfaceContainerHighest = Color(0xFF30333C),
    outline = Color(0xFF8E8A82),
    outlineVariant = Color(0xFF3D4049),
)

private val Display = FontFamily.Serif

private val AtelierTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontFamily = Display, fontWeight = FontWeight.Normal),
        headlineLarge = headlineLarge.copy(fontFamily = Display),
        headlineMedium = headlineMedium.copy(fontFamily = Display),
        headlineSmall = headlineSmall.copy(fontFamily = Display),
        titleLarge = titleLarge.copy(fontFamily = Display, fontWeight = FontWeight.Medium),
        titleMedium = titleMedium.copy(fontFamily = Display, fontWeight = FontWeight.Medium, fontSize = 17.sp),
    )
}

/** Style des cartels de musée : italique, discret. */
val CartelStyle = TextStyle(fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp)

/** Couleurs franches de l'atelier en mode jeu : jaune soleil, corail, encre, kraft. */
object Play {
    val Sun = Color(0xFFFFC93C)
    val Coral = Color(0xFFE8553B)
    val CoralLight = Color(0xFFF07A5F)
    val Teal = Color(0xFF3FB6A8)
    val TealDeep = Color(0xFF10302C)
    val Ink = Color(0xFF2B1B12)
    val Cream = Color(0xFFFFE9C7)
}

/** Fond, encre et papier photo : clairs le jour, chauds et sombres la nuit. */
@Immutable
data class PlayColors(val background: Color, val dots: Color, val ink: Color, val kraft: Color, val polaroid: Color)

private val PlayLight = PlayColors(Color(0xFFFFF1D9), Color(0xFFEBCFA4), Play.Ink, Color(0xFF8A5A3C), Color.White)
private val PlayDark = PlayColors(Color(0xFF1C1814), Color(0xFF362C23), Color(0xFFF4E6D2), Color(0xFFD2AE85), Color(0xFF2B2520))

val LocalPlayColors = staticCompositionLocalOf { PlayLight }

/** Titres ronds et joueurs (Fraunces « soft » et « wonky »), notes écrites à la main (Caveat). */
@OptIn(ExperimentalTextApi::class)
val Fraunces = FontFamily(
    Font(
        R.font.fraunces,
        FontWeight.Black,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(900),
            FontVariation.Setting("SOFT", 100f),
            FontVariation.Setting("WONK", 1f),
        ),
    ),
    Font(
        R.font.fraunces,
        FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800), FontVariation.Setting("SOFT", 100f)),
    ),
)

@OptIn(ExperimentalTextApi::class)
val Caveat = FontFamily(
    Font(R.font.caveat, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Gros titre de jeu, en Fraunces noir. */
val PlayTitle = TextStyle(fontFamily = Fraunces, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 31.sp)

/** Écriture à la main, pour les légendes et les petits mots. */
val HandStyle = TextStyle(fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 22.sp)

@Composable
fun AtelierTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AtelierTypography,
    ) {
        CompositionLocalProvider(LocalPlayColors provides if (darkTheme) PlayDark else PlayLight, content = content)
    }
}
