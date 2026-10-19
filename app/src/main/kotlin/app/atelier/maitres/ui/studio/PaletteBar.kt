package app.atelier.maitres.ui.studio

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atelier.maitres.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Palette du peintre : une planche de bois où attendent les noisettes de peinture du maître,
 * numérotées, puis les couleurs libres, le nuancier et la gomme. Un petit trait sous chaque
 * noisette montre la part des zones de cette couleur déjà peintes ; la couleur choisie se
 * soulève de la planche.
 */
@Composable
fun PaletteBar(state: StudioState, look: AtelierLook, modifier: Modifier = Modifier) {
    // Lu pour recomposer après chaque touche (les traits de progression changent).
    state.version
    val palette = state.canvas.palette
    val tool = state.tool
    val listState = rememberLazyListState()

    // La pastille active reste visible quand la sélection avance toute seule.
    LaunchedEffect(tool) {
        val index = (tool as? Tool.Palette)?.index ?: return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == index && it.offset >= 0 }) listState.animateScrollToItem(maxOf(0, index - 2))
    }

    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth().woodTray(look),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(palette.toList(), key = { index, _ -> "p$index" }) { index, color ->
            val total = state.canvas.regionsPerColor[index]
            val remaining = state.session.remainingPerColor[index]
            PaintDab(
                color = color,
                seed = index * 7 + 3,
                lowered = index % 2 == 1,
                mark = look.mark,
                label = (index + 1).toString(),
                progress = if (total == 0) 1f else 1f - remaining.toFloat() / total,
                done = remaining == 0,
                selected = tool == Tool.Palette(index),
                description = pluralStringResource(R.plurals.colour_swatch, remaining, index + 1, remaining),
                onClick = { state.select(Tool.Palette(index)) },
            )
        }
        items(state.customColors.toList(), key = { "c$it" }) { color ->
            PaintDab(
                color = color,
                seed = (color and 0xFFFF) % 97,
                lowered = false,
                mark = look.mark,
                label = null,
                progress = null,
                done = false,
                selected = tool == Tool.Custom(color),
                description = stringResource(R.string.free_colour),
                onClick = { state.select(Tool.Custom(color)) },
            )
        }
        item(key = "picker") {
            ToolButton(selected = false, description = stringResource(R.string.colour_picker_open), onClick = state::openPicker) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = Color(0xFF3A2414))
            }
        }
        item(key = "eraser") {
            ToolButton(selected = tool == Tool.Eraser, description = stringResource(R.string.eraser), onClick = { state.select(Tool.Eraser) }) {
                Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, tint = Color(0xFF3A2414))
            }
        }
    }
}

/** La planche : un dégradé de bois, son fil, un reflet sur l'arête et une ombre douce dessous. */
private fun Modifier.woodTray(look: AtelierLook): Modifier {
    val shape = RoundedCornerShape(26.dp)
    return this
        .shadow(14.dp, shape, ambientColor = Color(0xFF3C1E0A), spotColor = Color(0xFF3C1E0A))
        .clip(shape)
        .background(Brush.verticalGradient(listOf(look.woodTop, look.woodBottom)))
        .drawWithContent {
            drawRect(Textures.wood, blendMode = BlendMode.Multiply)
            drawRect(Color.White.copy(alpha = 0.3f), size = size.copy(height = 2.dp.toPx()))
            drawRect(Brush.verticalGradient(0.85f to Color.Transparent, 1f to Color(0x4D50280A)))
            drawContent()
        }
}

/**
 * Une noisette de peinture à l'huile tout juste pressée du tube : une forme un peu irrégulière
 * (toujours la même pour une couleur), un modelé, une ombre portée sur le bois, un reflet
 * brillant et le sillon laissé par le tube en tournant.
 */
@Composable
private fun PaintDab(
    color: Int,
    seed: Int,
    lowered: Boolean,
    mark: Color,
    label: String?,
    progress: Float?,
    done: Boolean,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val lift by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "lift")
    val paint = Color(color)
    val path = remember(seed) { dabPath(seed) }
    val fill = remember(color) {
        listOf(0f to lerp(paint, Color.White, 0.25f), 0.55f to paint, 1f to lerp(paint, Color.Black, 0.38f))
    }
    val blur = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(2.4f, BlurMaskFilter.Blur.NORMAL) } }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp, 64.dp)
            .graphicsLayer {
                val rest = if (lowered) 6.dp.toPx() else 0f
                translationY = rest + (-5.dp.toPx() - rest) * lift
                scaleX = 1f + 0.16f * lift
                scaleY = 1f + 0.16f * lift
            }
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
                this.selected = selected
            },
    ) {
        Canvas(Modifier.size(50.dp, 52.dp).graphicsLayer { alpha = if (done) 0.55f else 1f }) {
            // Le dessin est fait dans une boîte de 48 × 50, comme la maquette.
            val k = size.width / 48f
            scale(k, k, pivot = Offset.Zero) {
                translate(1.5f, 3f) {
                    blur.color = 0x5928140A
                    drawContext.canvas.nativeCanvas.drawPath(path.asAndroidPath(), blur)
                }
                drawPath(path, Brush.radialGradient(*fill.toTypedArray(), center = Offset(18f, 16f), radius = 36f))
                drawPath(
                    Path().apply { moveTo(12f, 24f); cubicTo(12f, 14f, 30f, 10f, 35f, 19f); cubicTo(38f, 26f, 28f, 31f, 22f, 27f) },
                    Color.Black.copy(alpha = 0.18f),
                    style = Stroke(2f, cap = StrokeCap.Round),
                )
                drawPath(
                    Path().apply { moveTo(15f, 19f); cubicTo(19f, 13f, 27f, 12f, 31f, 15f) },
                    Color.White.copy(alpha = 0.7f),
                    style = Stroke(2.4f, cap = StrokeCap.Round),
                )
            }
        }
        val number = TextStyle(
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 4f),
        )
        Box(Modifier.padding(bottom = 6.dp)) {
            when {
                done -> Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                label != null -> Text(label, style = number)
            }
        }
        if (progress != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .width(26.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (selected) mark else Color(0x382B1B12))
                    .drawWithContent {
                        if (!selected) drawRect(Color(0xD9FFFCF5), size = size.copy(width = size.width * progress))
                    },
            )
        }
    }
}

/** Contour de noisette : neuf points autour d'un ovale, un peu bousculés, liés par des courbes douces. */
private fun dabPath(seed: Int): Path {
    val random = Random(seed)
    val n = 9
    val points = List(n) { i ->
        val a = 2 * PI * i / n
        val r = 17f + (random.nextFloat() * 5.5f - 2.5f)
        Offset(24f + r * cos(a).toFloat() * 1.08f, 25f + r * sin(a).toFloat() * 0.9f)
    }
    return Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 0 until n) {
            val p0 = points[(i + n - 1) % n]
            val p1 = points[i]
            val p2 = points[(i + 1) % n]
            val p3 = points[(i + 2) % n]
            val c1 = p1 + (p2 - p0) / 6f
            val c2 = p2 - (p3 - p1) / 6f
            cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
        }
        close()
    }
}

/** Nuancier et gomme : des godets creusés dans la planche. */
@Composable
private fun ToolButton(selected: Boolean, description: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .padding(4.dp)
            .clip(CircleShape)
            .background(if (selected) Color(0xBFFFF8EB) else Color(0x47FFF8EB))
            .drawWithContent {
                drawContent()
                // Le creux du godet : une ombre sur le bord haut.
                drawCircle(
                    Brush.verticalGradient(0f to Color(0x593C1E0A), 0.35f to Color.Transparent),
                    radius = size.minDimension / 2,
                )
            }
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
                this.selected = selected
            },
    ) {
        Box(Modifier.size(24.dp)) { icon() }
    }
}
