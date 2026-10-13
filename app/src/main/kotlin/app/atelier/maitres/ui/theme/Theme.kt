package app.atelier.maitres.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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

@Composable
fun AtelierTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AtelierTypography,
        content = content,
    )
}
