package app.atelier.maitres.ui.welcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Un pinceau d'artiste dessiné : manche laqué, virole de métal, poils trempés dans [paint].
 * [tip] est la pointe des poils ; le manche part vers le haut à droite. [unit] est la taille
 * en pixels d'une unité du dessin, qui mesure 146 unités de la pointe au bout du manche.
 */
fun DrawScope.drawPaintbrush(tip: Offset, unit: Float, paint: Color, alpha: Float = 1f) {
    if (alpha <= 0f) return
    translate(tip.x, tip.y) {
        scale(unit, unit, pivot = Offset.Zero) {
            rotate(-40f, pivot = Offset.Zero) {
                // Ombre portée, décalée vers le bas comme posée au-dessus de la toile.
                translate(2f, 6f) {
                    drawPath(BrushPaths.silhouette, Color(0xFF281E14), alpha = 0.22f * alpha)
                }
                drawPath(BrushPaths.handle, HandleGradient, alpha = alpha)
                drawLine(Color.White.copy(alpha = 0.28f * alpha), Offset(62f, -3.4f), Offset(136f, -2f), 1.2f, StrokeCap.Round)
                drawRoundRect(FerruleGradient, Offset(30f, -6.8f), Size(29f, 13.6f), CornerRadius(1.6f), alpha = alpha)
                drawLine(Color(0xFF7D766A).copy(alpha = alpha), Offset(49f, -6.8f), Offset(49f, 6.8f), 0.8f)
                drawLine(Color(0xFF7D766A).copy(alpha = alpha), Offset(52.5f, -6.8f), Offset(52.5f, 6.8f), 0.8f)
                drawPath(BrushPaths.hair, HairGradient, alpha = alpha)
                drawPath(BrushPaths.paint, paint, alpha = alpha)
                drawPath(BrushPaths.gloss, Color.White.copy(alpha = 0.35f * alpha), style = Stroke(0.9f, cap = StrokeCap.Round))
            }
        }
    }
}

private val HandleGradient = Brush.verticalGradient(
    0f to Color(0xFFA4473A), 0.4f to Color(0xFF7A2A20), 1f to Color(0xFF4E1913),
    startY = -5.6f, endY = 5.6f,
)
private val FerruleGradient = Brush.verticalGradient(
    0f to Color(0xFFF4F1EA), 0.45f to Color(0xFFB9B3A7), 0.55f to Color(0xFF8E877B), 1f to Color(0xFFD6D0C4),
    startY = -6.8f, endY = 6.8f,
)
private val HairGradient = Brush.horizontalGradient(listOf(Color(0xFF5B4A36), Color(0xFFC9A877)), startX = 0f, endX = 31f)

private object BrushPaths {
    val handle = Path().apply {
        moveTo(58f, -5.6f); lineTo(139f, -3.6f)
        quadraticTo(146f, -3.6f, 146f, 0f); quadraticTo(146f, 3.6f, 139f, 3.6f)
        lineTo(58f, 5.6f); close()
    }
    val hair = Path().apply {
        moveTo(0f, 0f); cubicTo(6f, -2.2f, 14f, -6.2f, 31f, -6.6f)
        lineTo(31f, 6.6f); cubicTo(14f, 6.2f, 6f, 2.2f, 0f, 0f); close()
    }
    val paint = Path().apply {
        moveTo(0f, 0f); cubicTo(5f, -1.9f, 10f, -4.3f, 17f, -5.2f)
        cubicTo(15f, -1f, 15f, 1f, 17f, 5.2f); cubicTo(10f, 4.3f, 5f, 1.9f, 0f, 0f); close()
    }
    val gloss = Path().apply { moveTo(3f, -0.6f); cubicTo(8f, -2f, 11f, -3f, 15f, -3.4f) }
    val silhouette = Path().apply {
        addPath(hair); addPath(handle)
        addRect(Rect(30f, -6.8f, 59f, 6.8f))
    }
}

/**
 * Le bouton d'entrée : un coup de pinceau peint de gauche à droite, le pinceau encore posé
 * au bout du trait. Tout l'ensemble est le bouton.
 */
@Composable
fun BrushStrokeButton(
    label: String,
    onClick: () -> Unit,
    animate: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    labelStyle: TextStyle = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic, fontSize = 19.sp),
    labelColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val reveal = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) reveal.animateTo(1f, tween(1300, delayMillis = 1100, easing = StrokeEasing))
    }
    val labelAlpha by animateFloatAsState(if (reveal.value > 0.85f) 1f else 0f, tween(700), label = "label")
    // De temps en temps, le pinceau se soulève un peu pour inviter à le toucher.
    val wiggle by rememberInfiniteTransition(label = "wiggle").animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3600
                0f at 0; 0f at 2950; -7f at 3170; 4f at 3380; 0f at 3600
            },
            RepeatMode.Restart,
        ),
        label = "wiggle",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "press")

    Box(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .graphicsLayer { scaleX = press; scaleY = press }
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = 92.dp.toPx() / 124f
            val strokeLeft = 0f
            val strokeTop = 6.dp.toPx()
            val strokeWidth = size.width - 54.dp.toPx()
            val strokeHeight = size.height - 12.dp.toPx()
            val sx = strokeWidth / 320f
            val sy = strokeHeight / 64f
            clipRect(right = strokeLeft + strokeWidth * reveal.value) {
                translate(strokeLeft, strokeTop) {
                    scale(sx, sy, pivot = Offset.Zero) {
                        drawPath(StrokePaths.body, color)
                        drawPath(StrokePaths.streaks, color, alpha = 0.55f, style = Stroke(2f, cap = StrokeCap.Round))
                    }
                }
            }
            val wobble = if (reveal.value >= 1f) wiggle else 0f
            val tipX = 4.5.dp.toPx() + (size.width - 92.dp.toPx()) * reveal.value
            val tipY = 55.dp.toPx()
            val brushAlpha = if (animate) (reveal.value / 0.12f).coerceIn(0f, 1f) else 1f
            rotate(wobble, pivot = Offset(tipX, tipY)) {
                drawPaintbrush(Offset(tipX, tipY), unit, color, brushAlpha)
            }
        }
        Text(
            label,
            style = labelStyle,
            color = labelColor,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(end = 54.dp)
                .fillMaxWidth()
                .graphicsLayer { alpha = labelAlpha },
            textAlign = TextAlign.Center,
        )
    }
}

private val StrokeEasing = CubicBezierEasing(0.45f, 0.05f, 0.25f, 1f)

/** Le trait de peinture, dessiné dans une boîte de 320 × 64 puis étiré à la taille du bouton. */
private object StrokePaths {
    val body = Path().apply {
        moveTo(7f, 33f)
        cubicTo(20f, 13f, 82f, 9f, 160f, 11f)
        cubicTo(232f, 12f, 292f, 8f, 314f, 21f)
        cubicTo(321f, 30f, 317f, 45f, 306f, 51f)
        cubicTo(250f, 59f, 180f, 52f, 110f, 56f)
        cubicTo(60f, 59f, 19f, 58f, 9f, 48f)
        cubicTo(2f, 43f, 2f, 38f, 7f, 33f)
        close()
    }
    val streaks = Path().apply {
        moveTo(24f, 8f); cubicTo(90f, 3f, 200f, 5f, 300f, 10f)
        moveTo(30f, 60f); cubicTo(110f, 63f, 220f, 60f, 296f, 57f)
    }
}
