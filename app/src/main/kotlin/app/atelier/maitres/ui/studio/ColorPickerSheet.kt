package app.atelier.maitres.ui.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atelier.maitres.R
import app.atelier.maitres.core.ColorSpace
import app.atelier.maitres.core.PaintingSession

/**
 * Nuancier : la palette du maître, les couleurs libres déjà utilisées, et un sélecteur
 * teinte / saturation / luminosité pour inventer sa propre couleur.
 *
 * Ouvert par un appui long sur une zone, il propose en premier la couleur d'origine
 * de cette zone ; la couleur choisie y est posée directement.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ColorPickerSheet(state: StudioState) {
    val region = state.pickerRegion
    val canvas = state.canvas
    val start = region?.let { r -> state.session.fills[r].takeIf { it != PaintingSession.EMPTY } ?: canvas.referenceColor(r) }
        ?: (state.tool as? Tool.Custom)?.color
        ?: (state.tool as? Tool.Palette)?.let { canvas.palette[it.index] }
        ?: 0xFF1F3A68.toInt()

    val hsv = remember(region) { FloatArray(3).also { android.graphics.Color.colorToHSV(start, it) } }
    var hue by remember(region) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(region) { mutableFloatStateOf(hsv[1]) }
    var value by remember(region) { mutableFloatStateOf(hsv[2]) }
    val chosen = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))

    ModalBottomSheet(
        onDismissRequest = state::closePicker,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                if (region != null) stringResource(R.string.picker_area, canvas.regionColor[region] + 1)
                else stringResource(R.string.picker_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (region != null) {
                Spacer(Modifier.height(12.dp))
                val reference = canvas.referenceColor(region)
                OutlinedButton(onClick = { state.onPicked(reference) }) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(Color(reference)))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.master_colour))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.canvas_palette), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (canvas.palette.toList() + state.customColors).distinct().forEach { color ->
                    val number = canvas.palette.indexOf(color).takeIf { it >= 0 }?.plus(1)
                    SmallSwatch(color, number) { state.onPicked(color) }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.free_colour), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            SaturationValuePanel(hue, saturation, value) { s, v -> saturation = s; value = v }
            Spacer(Modifier.height(14.dp))
            HueBar(hue) { hue = it }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(chosen))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "#%06X".format(chosen and 0xFFFFFF),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { state.onPicked(chosen) }) {
                    Text(stringResource(if (region != null) R.string.paint_area else R.string.take_colour))
                }
            }
        }
    }
}

@Composable
private fun SmallSwatch(color: Int, number: Int?, onClick: () -> Unit) {
    val ink = if (ColorSpace.luminance(color) > 0.35f) Color(0xFF231F1A) else Color.White
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(1.dp, Color.Black.copy(alpha = 0.08f), CircleShape)
            .clickable(onClick = onClick),
    ) {
        if (number != null) Text(number.toString(), color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SaturationValuePanel(hue: Float, saturation: Float, value: Float, onChange: (Float, Float) -> Unit) {
    val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    fun update(position: Offset, width: Int, height: Int) =
        onChange((position.x / width).coerceIn(0f, 1f), 1f - (position.y / height).coerceIn(0f, 1f))

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2.2f)
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(Unit) { detectTapGestures { update(it, size.width, size.height) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> update(change.position, size.width, size.height) } },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val center = Offset(saturation * size.width, (1f - value) * size.height)
        drawCircle(Color.White, radius = 11.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.3f), radius = 12.5f.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    val rainbow = remember { (0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) } }
    fun update(x: Float, width: Int) = onChange((x / width).coerceIn(0f, 1f) * 359.9f)

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) { detectTapGestures { update(it.x, size.width) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> update(change.position.x, size.width) } },
    ) {
        drawRoundRect(Brush.horizontalGradient(rainbow), cornerRadius = CornerRadius(size.height / 2))
        val x = hue / 360f * size.width
        val selected = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        drawCircle(selected, radius = size.height / 2 - 2.dp.toPx(), center = Offset(x, size.height / 2))
        drawCircle(Color.White, radius = size.height / 2, center = Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
    }
}
