package app.atelier.maitres.ui.studio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atelier.maitres.core.ColorSpace

/**
 * Palette du peintre : les couleurs numérotées du maître, puis les couleurs libres,
 * le nuancier et la gomme. Un anneau autour de chaque pastille montre la part des
 * zones de cette couleur déjà peintes.
 */
@Composable
fun PaletteBar(state: StudioState, modifier: Modifier = Modifier) {
    // Lu pour recomposer après chaque touche (les anneaux de progression changent).
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
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(palette.toList(), key = { index, _ -> "p$index" }) { index, color ->
            val total = state.canvas.regionsPerColor[index]
            val remaining = state.session.remainingPerColor[index]
            Swatch(
                color = color,
                label = (index + 1).toString(),
                progress = if (total == 0) 1f else 1f - remaining.toFloat() / total,
                done = remaining == 0,
                selected = tool == Tool.Palette(index),
                description = "Couleur ${index + 1}, $remaining zones restantes",
                onClick = { state.select(Tool.Palette(index)) },
            )
        }
        items(state.customColors.toList(), key = { "c$it" }) { color ->
            Swatch(
                color = color,
                label = null,
                progress = null,
                done = false,
                selected = tool == Tool.Custom(color),
                description = "Couleur libre",
                onClick = { state.select(Tool.Custom(color)) },
            )
        }
        item(key = "picker") {
            ToolButton(selected = false, description = "Nuancier : choisir une couleur libre", onClick = state::openPicker) {
                Icon(Icons.Outlined.Add, contentDescription = null)
            }
        }
        item(key = "eraser") {
            ToolButton(selected = tool == Tool.Eraser, description = "Gomme", onClick = { state.select(Tool.Eraser) }) {
                Icon(Icons.Outlined.AutoFixHigh, contentDescription = null)
            }
        }
    }
}

@Composable
private fun Swatch(
    color: Int,
    label: String?,
    progress: Float?,
    done: Boolean,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    size: Dp = 46.dp,
) {
    val grow by animateFloatAsState(if (selected) 1.14f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "grow")
    val ink = if (ColorSpace.luminance(color) > 0.35f) Color(0xFF231F1A) else Color.White
    val ring = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.outlineVariant
    val selectedRing = MaterialTheme.colorScheme.onSurface

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size + 10.dp)
            .scale(grow)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 3.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
            if (selected) {
                drawCircle(selectedRing, radius = this.size.minDimension / 2 - inset, style = Stroke(strokeWidth))
            } else if (progress != null) {
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(strokeWidth))
                drawArc(ring, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Round))
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size - 4.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(1.dp, Color.Black.copy(alpha = 0.08f), CircleShape),
        ) {
            when {
                done -> Icon(Icons.Rounded.Check, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
                label != null -> Text(label, color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ToolButton(selected: Boolean, description: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .padding(5.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
    ) { icon() }
}
