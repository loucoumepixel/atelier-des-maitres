package app.atelier.maitres.ui.studio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atelier.maitres.R
import app.atelier.maitres.ui.theme.Fraunces
import app.atelier.maitres.ui.theme.Play

/**
 * La première toile, guidée pas à pas : choisir un pot, peindre une zone, s'approcher, puis
 * le petit œil. Chaque étape avance d'elle-même quand le geste est fait ; « Passer » arrête tout.
 */
class GuidedTour {
    var step by mutableIntStateOf(0)
        private set
    private var zoomAtStep by mutableFloatStateOf(1f)

    val finished: Boolean get() = step >= STEPS

    fun advance(zoom: Float) {
        step++
        zoomAtStep = zoom
    }

    fun skip() {
        step = STEPS
    }

    /** Le geste attendu à l'étape en cours a-t-il été fait ? */
    fun satisfied(toolChanged: Boolean, painted: Boolean, zoom: Float, peek: Boolean): Boolean = when (step) {
        0 -> toolChanged || painted
        1 -> painted
        2 -> zoom > zoomAtStep * 1.3f
        3 -> peek
        else -> false
    }

    companion object {
        const val STEPS = 4
    }
}

/** La carte d'explication, posée au-dessus de la palette, avec ses points d'étape. */
@Composable
fun GuidedCard(tour: GuidedTour, onNext: () -> Unit, modifier: Modifier = Modifier) {
    val titles = stringArrayResource(R.array.guided_titles)
    val texts = stringArrayResource(R.array.guided_texts)
    AnimatedContent(targetState = tour.step, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "guided", modifier = modifier) { step ->
        if (step >= GuidedTour.STEPS) return@AnimatedContent
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFFFFBF5))
                .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                "${step + 1}. ${titles.getOrElse(step) { "" }}",
                fontFamily = Fraunces,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = Play.Ink,
            )
            Text(
                texts.getOrElse(step) { "" },
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = Color(0xFF3A2C22),
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(GuidedTour.STEPS) { i ->
                        Spacer(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (i <= step) Color(0xFF8A5A3C) else Color(0x408A5A3C)),
                        )
                    }
                }
                Text(
                    stringResource(R.string.guided_step, step + 1, GuidedTour.STEPS),
                    fontSize = 13.sp,
                    color = Color(0xFF8A5A3C),
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                )
                // Les deux dernières étapes se lisent plus qu'elles ne se font : on peut avancer.
                val last = step == GuidedTour.STEPS - 1
                if (step >= 2) {
                    Text(
                        stringResource(if (last) R.string.guided_done else R.string.guided_next),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Play.Coral,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onNext).padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
                if (!last) {
                    Text(
                        stringResource(R.string.guided_skip),
                        fontSize = 14.sp,
                        color = Color(0xFF8A5A3C),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { tour.skip() }.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Le pointillé corail qui bat autour de ce qu'il faut toucher. */
@Composable
fun Modifier.guideOutline(active: Boolean): Modifier {
    if (!active) return this
    val beat = rememberInfiniteTransition(label = "outline")
    val k by beat.animateFloat(0.45f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "outlineBeat")
    return drawWithContent {
        drawContent()
        val w = 3.dp.toPx()
        drawRoundRect(
            Play.Coral.copy(alpha = k),
            topLeft = androidx.compose.ui.geometry.Offset(-w, -w),
            size = androidx.compose.ui.geometry.Size(size.width + 2 * w, size.height + 2 * w),
            cornerRadius = CornerRadius(30.dp.toPx()),
            style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))),
        )
    }
}

/** Suit les gestes du joueur et fait avancer la visite guidée. */
@Composable
fun GuidedTourEffects(tour: GuidedTour, state: StudioState, peek: Boolean, onFinished: () -> Unit) {
    val startTool = remember { state.tool }
    val startStroke = remember { state.lastStroke }
    val toolChanged = state.tool != startTool || tour.step > 0
    val painted = state.lastStroke.let { it != null && it != startStroke && it.faithful }
    val zoom = state.camera.zoom
    LaunchedEffect(tour.step, toolChanged, painted, zoom, peek) {
        if (!tour.finished && tour.satisfied(toolChanged, painted, zoom, peek)) tour.advance(zoom)
    }
    LaunchedEffect(tour.finished) { if (tour.finished) onFinished() }
}
