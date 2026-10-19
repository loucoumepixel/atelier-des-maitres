package app.atelier.maitres.ui.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atelier.maitres.R
import app.atelier.maitres.core.Difficulty
import app.atelier.maitres.ui.theme.Play
import app.atelier.maitres.ui.theme.PlayColors
import java.text.NumberFormat

/** Le papier à pois de l'atelier, sous la galerie et l'accueil. */
fun Modifier.dottedPaper(colors: PlayColors, step: Dp = 14.dp): Modifier = background(colors.background).drawBehind {
    val s = step.toPx()
    val r = 1.dp.toPx()
    var y = s / 2
    while (y < size.height) {
        var x = s / 2
        while (x < size.width) {
            drawCircle(colors.dots, r, Offset(x, y))
            x += s
        }
        y += s
    }
}

/** Nom traduit de chaque rang, de Gribouilleur à Génie. */
@Composable
@ReadOnlyComposable
fun rankNames(): Array<String> = stringArrayResource(R.array.rank_names)

/** Texte avec un mot mis en avant en corail, à la place de `%1$s`. */
@Composable
@ReadOnlyComposable
fun emphasized(template: Int, word: Int): AnnotatedString {
    val text = stringResource(template)
    val em = stringResource(word)
    val at = text.indexOf("%1\$s")
    return buildAnnotatedString {
        if (at < 0) {
            append(text)
            return@buildAnnotatedString
        }
        append(text.substring(0, at))
        withStyle(SpanStyle(color = Play.Coral)) { append(em) }
        append(text.substring(at + 4))
    }
}

fun formatCount(n: Int): String = NumberFormat.getIntegerInstance().format(n)

/** Médailles des trois niveaux : bronze, argent, or. */
val Difficulty.medal: Color
    get() = when (this) {
        Difficulty.FACILE -> Color(0xFFD98B4A)
        Difficulty.NORMAL -> Color(0xFFC9CED6)
        Difficulty.EXPERT -> Play.Sun
    }

private val StarPath = Path().apply {
    // Étoile à cinq branches dans une boîte de 24.
    moveTo(12f, 2.5f); lineTo(14.9f, 8.6f); lineTo(21.5f, 9.4f); lineTo(16.6f, 14f); lineTo(17.9f, 20.6f)
    lineTo(12f, 17.3f); lineTo(6.1f, 20.6f); lineTo(7.4f, 14f); lineTo(2.5f, 9.4f); lineTo(9.1f, 8.6f); close()
}

fun DrawScope.drawStar(fill: Color?, outline: Color) {
    val k = size.minDimension / 24f
    scale(k, k, pivot = Offset.Zero) {
        if (fill != null) drawPath(StarPath, fill)
        drawPath(StarPath, outline, style = Stroke(1.6f, join = StrokeJoin.Round))
    }
}

/** Une étoile de niveau, pleine si gagnée. */
@Composable
fun Star(difficulty: Difficulty, won: Boolean, outline: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        if (won) drawStar(difficulty.medal, Play.Ink) else drawStar(null, outline.copy(alpha = 0.55f))
    }
}

@Composable
fun Stars(won: List<Boolean>, outline: Color, size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Difficulty.entries.forEachIndexed { i, d -> Star(d, won.getOrElse(i) { false }, outline, size) }
    }
}

private val XpPath = Path().apply {
    moveTo(3f, 9f); cubicTo(40f, 3f, 80f, 12f, 120f, 7f); cubicTo(160f, 2f, 190f, 11f, 217f, 6f)
    lineTo(217f, 12f); cubicTo(190f, 16f, 160f, 8f, 120f, 13f); cubicTo(80f, 17f, 40f, 8f, 3f, 14f); close()
}

/** La barre de rang : un coup de pinceau qui se remplit, du jaune au corail. */
@Composable
fun XpBar(fraction: Float, track: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(18.dp)) {
        val sx = size.width / 220f
        val sy = size.height / 16f
        scale(sx, sy, pivot = Offset.Zero) {
            drawPath(XpPath, track.copy(alpha = 0.14f))
            clipRect(right = 220f * fraction.coerceIn(0f, 1f)) {
                drawPath(XpPath, Brush.horizontalGradient(listOf(Play.Sun, Play.Coral), startX = 0f, endX = 220f))
            }
        }
    }
}

private val FlamePath = Path().apply {
    moveTo(13.5f, 2f); cubicTo(13.5f, 2f, 14.2f, 5f, 11.9f, 7.6f); cubicTo(9.6f, 10.2f, 7f, 12f, 7f, 15.5f)
    cubicTo(7f, 18.5f, 9.2f, 21f, 12f, 21f); cubicTo(15.3f, 21f, 18f, 18.3f, 18f, 15f); cubicTo(18f, 12f, 16.2f, 9.6f, 15f, 8.4f)
    cubicTo(15.1f, 10f, 14.5f, 11.4f, 13.4f, 12f); cubicTo(14f, 9.2f, 13.2f, 5f, 9.5f, 2f); close()
}

/** La série de jours : pastille sombre si l'on a peint aujourd'hui, en creux sinon. */
@Composable
fun FlameChip(streak: Int, lit: Boolean, kraft: Color, modifier: Modifier = Modifier) {
    val color = if (lit) Play.Sun else kraft
    Row(
        modifier
            .rotate(3f)
            .then(
                if (lit) Modifier.background(Play.Ink, RoundedCornerShape(12.dp))
                else Modifier.border(1.5.dp, kraft, RoundedCornerShape(12.dp)),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(Modifier.size(15.dp)) {
            val k = size.minDimension / 24f
            scale(k, k, pivot = Offset.Zero) { drawPath(FlamePath, color) }
        }
        Text(pluralStringResource(R.plurals.streak_days, streak, streak), color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private val BrushGlyph = Path().apply {
    // Pictogramme « pinceau » (24 × 24).
    moveTo(20.7f, 3.3f); cubicTo(20.3f, 2.9f, 19.7f, 2.9f, 19.3f, 3.3f); lineTo(10f, 12.6f); lineTo(11.4f, 14f)
    lineTo(20.7f, 4.7f); cubicTo(21.1f, 4.3f, 21.1f, 3.7f, 20.7f, 3.3f); close()
    moveTo(8.5f, 14f); cubicTo(6.8f, 14f, 5.5f, 15.3f, 5.5f, 17f); cubicTo(5.5f, 18.1f, 4.6f, 19f, 3.5f, 19f)
    cubicTo(4.4f, 20.2f, 5.9f, 21f, 7.5f, 21f); cubicTo(9.7f, 21f, 11.5f, 19.2f, 11.5f, 17f); cubicTo(11.5f, 15.3f, 10.2f, 14f, 8.5f, 14f); close()
}

/** La médaille de rang : disque jaune cerné d'encre, ombre corail, pinceau au centre. */
@Composable
fun RankBadge(size: Dp = 52.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).rotate(-8f)) {
        val r = this.size.minDimension / 2f
        val border = 3.dp.toPx()
        drawCircle(Play.Coral, r - border / 2, center + Offset(2.dp.toPx(), 3.dp.toPx()))
        drawCircle(Play.Sun, r - border)
        drawCircle(Play.Ink, r - border, style = Stroke(border))
        val glyph = r
        translate(center.x - glyph / 2, center.y - glyph / 2) {
            val k = glyph / 24f
            scale(k, k, pivot = Offset.Zero) { drawPath(BrushGlyph, Play.Ink) }
        }
    }
}

private val LockPath = Path().apply {
    moveTo(7f, 10f); lineTo(7f, 7f); cubicTo(7f, 4.2f, 9.2f, 2f, 12f, 2f); cubicTo(14.8f, 2f, 17f, 4.2f, 17f, 7f); lineTo(17f, 10f)
    lineTo(18f, 10f); cubicTo(18.6f, 10f, 19f, 10.4f, 19f, 11f); lineTo(19f, 20f); cubicTo(19f, 20.6f, 18.6f, 21f, 18f, 21f)
    lineTo(6f, 21f); cubicTo(5.4f, 21f, 5f, 20.6f, 5f, 20f); lineTo(5f, 11f); cubicTo(5f, 10.4f, 5.4f, 10f, 6f, 10f); close()
    moveTo(9f, 10f); lineTo(15f, 10f); lineTo(15f, 7f); cubicTo(15f, 5.3f, 13.7f, 4f, 12f, 4f); cubicTo(10.3f, 4f, 9f, 5.3f, 9f, 7f); close()
}

@Composable
fun LockIcon(color: Color, size: Dp = 26.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        scale(k, k, pivot = Offset.Zero) { drawPath(LockPath, color, style = androidx.compose.ui.graphics.drawscope.Fill) }
    }
}

/** Le dé « Surprends-moi » : cinq points d'encre. */
@Composable
fun DiceFace(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(60.dp)
            .rotate(-10f)
            .drawBehind {
                val corner = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx())
                drawRoundRect(Play.Ink, topLeft = Offset(3.dp.toPx(), 4.dp.toPx()), size = size, cornerRadius = corner)
                drawRoundRect(Play.Sun, cornerRadius = corner)
                drawRoundRect(Play.Ink, cornerRadius = corner, style = Stroke(3.dp.toPx()))
                val u = size.width / 24f
                listOf(7.5f to 7.5f, 16.5f to 16.5f, 12f to 12f, 16.5f to 7.5f, 7.5f to 16.5f).forEach { (x, y) ->
                    drawCircle(Play.Ink, 1.9f * u * 1.1f, Offset(x * u, y * u))
                }
            },
    )
}

/** Ombre portée « à plat », décalée, façon autocollant. */
fun Modifier.flatShadow(color: Color, dx: Dp, dy: Dp, corner: Dp): Modifier = drawBehind {
    drawRoundRect(
        color,
        topLeft = Offset(dx.toPx(), dy.toPx()),
        size = Size(size.width, size.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner.toPx()),
    )
}

/** Disque coloré pour annoncer une couleur terminée. */
@Composable
fun ColourDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(20.dp).background(color, CircleShape).border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape))
}
