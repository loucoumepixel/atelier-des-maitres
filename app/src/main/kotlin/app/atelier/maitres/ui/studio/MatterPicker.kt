package app.atelier.maitres.ui.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atelier.maitres.R
import app.atelier.maitres.data.Matter
import app.atelier.maitres.ui.theme.Fraunces
import app.atelier.maitres.ui.theme.Play

val Matter.labelRes: Int
    get() = when (this) {
        Matter.PLATE -> R.string.matter_plate
        Matter.LEGERE -> R.string.matter_light
        Matter.EPAISSE -> R.string.matter_thick
    }

val Matter.textRes: Int
    get() = when (this) {
        Matter.PLATE -> R.string.matter_plate_text
        Matter.LEGERE -> R.string.matter_light_text
        Matter.EPAISSE -> R.string.matter_thick_text
    }

/**
 * Un échantillon de matière dans la couleur donnée : aplat pour la plate, fines stries de
 * pinceau pour la légère, larges bandes en relief (un bord éclairé, un bord dans l'ombre)
 * pour l'épaisse.
 */
fun DrawScope.drawMatter(matter: Matter, color: Color) {
    drawRect(color)
    val step = when (matter) {
        Matter.PLATE -> return
        Matter.LEGERE -> 6.dp.toPx()
        Matter.EPAISSE -> 13.dp.toPx()
    }
    // Les touches vont en biais, comme un pinceau tiré vers le haut à droite.
    val h = size.height
    clipRect {
        var x = -h
        while (x < size.width + h) {
            if (matter == Matter.LEGERE) {
                drawLine(lerp(color, Color.Black, 0.22f), Offset(x, h), Offset(x + h * 0.7f, 0f), strokeWidth = step * 0.35f)
            } else {
                val w = step * 0.62f
                drawLine(
                    Brush.linearGradient(
                        listOf(lerp(color, Color.White, 0.5f), color, lerp(color, Color.Black, 0.45f)),
                        start = Offset(x - w / 2, h / 2),
                        end = Offset(x + w / 2, h / 2),
                    ),
                    Offset(x, h), Offset(x + h * 0.7f, 0f), strokeWidth = w,
                )
            }
            x += step
        }
    }
}

/** Le godet de matière, au début de la palette : l'échantillon et son nom, toujours sous le doigt. */
@Composable
fun MatterTile(matter: Matter, color: Color, open: Boolean, onClick: () -> Unit) {
    val label = stringResource(matter.labelRes)
    val description = stringResource(R.string.matter_button, label)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFFBF5))
            .border(2.dp, if (open) Play.Coral else Color(0x592B1B12), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .padding(horizontal = 6.dp, vertical = 5.dp),
    ) {
        Canvas(Modifier.size(width = 44.dp, height = 26.dp).clip(RoundedCornerShape(6.dp))) { drawMatter(matter, color) }
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Play.Ink, modifier = Modifier.padding(top = 2.dp))
    }
}

/**
 * La carte qui s'ouvre au-dessus de la palette : les trois matières en échantillons, dans la
 * couleur en cours. Toucher ailleurs la referme.
 */
@Composable
fun MatterChooser(current: Matter, color: Color, onChoose: (Matter) -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x33140A04))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .padding(start = 14.dp, end = 14.dp, bottom = 112.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFFFFFBF5))
                // La carte elle-même ne referme rien.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.matter_title),
                fontFamily = Fraunces,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = Play.Ink,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().selectableGroup()) {
                Matter.entries.forEach { m ->
                    val chosen = m == current
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (chosen) Color(0xFFFDF1EC) else Color.Transparent)
                            .border(2.dp, if (chosen) Play.Coral else Color(0xFFE8DFD0), RoundedCornerShape(14.dp))
                            .selectable(selected = chosen, role = Role.RadioButton) { onChoose(m) }
                            .padding(8.dp),
                    ) {
                        Canvas(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(8.dp))) { drawMatter(m, color) }
                        Text(
                            stringResource(m.labelRes),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Play.Ink,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            stringResource(m.textRes),
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFF6B5A4A),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
