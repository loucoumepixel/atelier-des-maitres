package app.atelier.maitres.ui.studio

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atelier.maitres.R
import app.atelier.maitres.data.Career
import app.atelier.maitres.data.Painting
import app.atelier.maitres.data.PaintingRepository
import app.atelier.maitres.ui.play.flatShadow
import app.atelier.maitres.ui.play.rankNames
import app.atelier.maitres.ui.theme.Fraunces
import app.atelier.maitres.ui.theme.HandStyle
import app.atelier.maitres.ui.theme.Play
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val Deep = Color(0xFF173A55)
private val Mid = Color(0xFF235173)
private val Gold = Color(0xFFE9B949)

/**
 * La fête du nouveau rang, avant l'écran de fin de toile : rayons qui tournent, confettis,
 * la médaille d'or, le ruban au nom du rang et les toiles qui viennent d'être déballées.
 */
@Composable
fun RankParty(rank: Int, touches: Int, unlocked: List<Painting>, repository: PaintingRepository, onContinue: () -> Unit) {
    val names = rankNames()
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 180f)) }
    val spin = rememberInfiniteTransition(label = "party")
    val turn by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "rays")
    val fall by spin.animateFloat(0f, 1f, infiniteRepeatable(tween(7_000, easing = LinearEasing)), label = "confetti")
    val bob by spin.animateFloat(-1f, 1f, infiniteRepeatable(tween(1_800), RepeatMode.Reverse), label = "bob")
    val confetti = remember {
        val r = Random(rank * 31 + 7)
        List(18) { Confetto(r.nextFloat(), r.nextFloat(), r.nextFloat() * 360f, r.nextFloat() * 0.5f + 0.6f, CONFETTI[it % CONFETTI.size]) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Mid, Deep)))
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height * 0.42f)
            val reach = size.maxDimension
            rotate(turn, c) {
                for (i in 0 until 16) {
                    val a0 = (i * 2) * PI / 16
                    val a1 = (i * 2 + 1) * PI / 16
                    val path = Path().apply {
                        moveTo(c.x, c.y)
                        lineTo(c.x + reach * cos(a0).toFloat(), c.y + reach * sin(a0).toFloat())
                        lineTo(c.x + reach * cos(a1).toFloat(), c.y + reach * sin(a1).toFloat())
                        close()
                    }
                    drawPath(path, Color.White.copy(alpha = 0.06f))
                }
            }
            confetti.forEach { k ->
                val y = ((k.y + fall * k.speed) % 1.1f - 0.05f) * size.height
                val x = k.x * size.width + sin((fall * 6f + k.x * 10f).toDouble()).toFloat() * 14.dp.toPx()
                rotate(k.angle + fall * 360f * k.speed, Offset(x, y)) {
                    drawRoundRect(k.color, Offset(x - 6.dp.toPx(), y - 10.dp.toPx()), Size(12.dp.toPx(), 20.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.rank_party_title),
                style = HandStyle.copy(fontSize = 30.sp, lineHeight = 32.sp),
                color = Color.White,
                modifier = Modifier.graphicsLayer { alpha = pop.value.coerceIn(0f, 1f) },
            )
            Spacer(Modifier.height(22.dp))
            // La médaille d'or, la palette du peintre au centre.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                        translationY = bob * 4.dp.toPx()
                    }
                    .size(150.dp)
                    .border(8.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFFF7DE8C), Gold, Color(0xFFB8862B)), center = Offset(110f, 80f))),
            ) {
                Text("🎨", fontSize = 64.sp)
            }
            // Le ruban corail, aux bouts fendus.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(top = 0.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
                    .ribbon(),
            ) {
                Text(
                    names[rank] + " !",
                    fontFamily = Fraunces,
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 8.dp).semantics { heading() },
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.rank_party_rank, rank + 1, Career.RANK_AT.size, touches),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
            )
            if (unlocked.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.rank_party_unlocked),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    unlocked.take(3).forEachIndexed { i, p -> PartyPolaroid(p, repository, tilt = if (i % 2 == 0) -3f else 4f) }
                }
            }
            Spacer(Modifier.height(26.dp))
            Text(
                stringResource(R.string.rank_party_go),
                fontFamily = Fraunces,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 19.sp,
                color = Play.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .flatShadow(Color.Black.copy(alpha = 0.25f), 0.dp, 4.dp, 28.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Play.Cream)
                    .clickable(role = Role.Button, onClick = onContinue)
                    .padding(horizontal = 44.dp, vertical = 14.dp),
            )
        }
    }
}

/** Le ruban : un bandeau corail dont les deux bouts sont entaillés en queue d'aronde. */
private fun Modifier.ribbon() = this.then(
    Modifier.background(Play.Coral, RibbonShape),
)

private val RibbonShape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val notch = size.height * 0.32f
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - notch, size.height / 2)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    lineTo(notch, size.height / 2)
    close()
}

@Composable
private fun PartyPolaroid(painting: Painting, repository: PaintingRepository, tilt: Float) {
    val thumb by produceState<android.graphics.Bitmap?>(null, painting.id) {
        value = runCatching { repository.thumbnail(painting) }.getOrNull()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .rotate(tilt)
            .flatShadow(Color.Black.copy(alpha = 0.3f), 3.dp, 5.dp, 2.dp)
            .background(Color.White)
            .padding(start = 7.dp, end = 7.dp, top = 7.dp, bottom = 4.dp)
            .width(108.dp),
    ) {
        Box(Modifier.aspectRatio(1f).background(Color(0xFFEDE6DA))) {
            thumb?.let {
                Image(
                    bitmap = remember(it) { it.asImageBitmap() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            painting.title,
            style = HandStyle.copy(fontSize = 17.sp),
            color = Play.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

private class Confetto(val x: Float, val y: Float, val angle: Float, val speed: Float, val color: Color)

private val CONFETTI = listOf(Play.Sun, Play.Coral, Color(0xFF7ED0B4), Color(0xFF9DB4E0))
