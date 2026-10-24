package app.atelier.maitres.ui.studio

import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atelier.maitres.R
import app.atelier.maitres.core.Difficulty
import app.atelier.maitres.core.PaintingSession
import app.atelier.maitres.data.Career
import app.atelier.maitres.data.Exhibitions
import app.atelier.maitres.ui.play.Stars
import app.atelier.maitres.ui.play.XpBar
import app.atelier.maitres.ui.play.flatShadow
import app.atelier.maitres.ui.play.formatCount
import app.atelier.maitres.ui.play.rankNames
import app.atelier.maitres.ui.theme.CartelStyle
import app.atelier.maitres.ui.theme.Fraunces
import app.atelier.maitres.ui.theme.HandStyle
import app.atelier.maitres.ui.theme.Play
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * La fête de fin de toile : le petit nom du maître, l'avant / après à faire glisser, les
 * étoiles, les touches gagnées, le rang et les toiles déballées.
 */
@Composable
fun WinScreen(state: StudioState, career: Career, onShare: () -> Unit, onOneMore: () -> Unit, onAdmire: () -> Unit) {
    val painting = state.loaded.painting
    val names = rankNames()
    val nickname = painting.nickname ?: stringResource(R.string.nickname_default)
    val touches = career.touches
    val rank = Career.rankOf(touches)
    val (fraction, next) = Career.progress(touches)
    // La progression n'est pas encore écrite : l'étoile de ce niveau est acquise d'office.
    val won = remember {
        career.stars(painting).mapIndexed { i, w -> w || Difficulty.entries[i] == state.loaded.difficulty }
    }
    val unlocked = remember { state.unlockedPaintings() }
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(600)) }

    // Le guide de musée prend la parole quand la fanfare se tait, si le son est mis.
    val context = LocalContext.current
    var speaking by remember { mutableStateOf(false) }
    val guide = remember { MuseumGuide(context) { speaking = it } }
    DisposableEffect(guide) { onDispose { guide.release() } }
    LaunchedEffect(Unit) {
        if (career.soundOn && guide.hasStory(painting)) {
            delay(1_800)
            guide.play(painting)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Play.TealDeep)
            // Rien ne doit traverser jusqu'à la toile, dessous.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 26.dp, vertical = 22.dp)
                .graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * 24.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.win_title, nickname),
                fontFamily = Fraunces,
                fontWeight = FontWeight.Black,
                fontSize = 40.sp,
                lineHeight = 42.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(18.dp))
            BeforeAfter(state, Modifier.widthIn(max = 460.dp))
            Text(
                stringResource(R.string.win_drag),
                style = HandStyle,
                color = Play.Cream.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
            Stars(won, Color.White, 34.dp)
            if (state.earned > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.win_gain, formatCount(state.earned)),
                    fontFamily = Fraunces,
                    fontWeight = FontWeight.Black,
                    fontSize = 30.sp,
                    color = Play.Sun,
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                XpBar(fraction, Color.White)
                Text(
                    if (next == null) stringResource(R.string.max_rank)
                    else stringResource(R.string.win_next, formatCount(next - touches), names[rank + 1]),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Play.Cream,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            state.rankUps.forEach { r ->
                Spacer(Modifier.height(12.dp))
                Pill(stringResource(R.string.rank_up, names[r]), Play.Sun, Play.Ink)
            }
            unlocked.forEach { p ->
                Spacer(Modifier.height(10.dp))
                Pill(stringResource(R.string.unlocked, p.title), Play.Coral, Color.White)
            }
            state.exhibitionWon?.let { expo ->
                Spacer(Modifier.height(10.dp))
                Pill(stringResource(R.string.expo_won, stringResource(expo.frame.labelRes), Exhibitions.BONUS), Play.Sun, Play.Ink)
            }
            if (guide.hasStory(painting)) {
                Spacer(Modifier.height(16.dp))
                GuideButton(speaking) { if (speaking) guide.stop() else guide.play(painting) }
            }
            if (painting.note.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    painting.note,
                    style = CartelStyle.copy(fontSize = 15.sp, lineHeight = 21.sp),
                    color = Color.White.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 460.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameButton(stringResource(R.string.show_friends), Color.Transparent, Color.White, onShare)
                GameButton(stringResource(R.string.one_more), Play.Coral, Color.White, onOneMore)
            }
            TextButton(onClick = onAdmire, modifier = Modifier.padding(top = 6.dp)) {
                Text(stringResource(R.string.admire), color = Play.Cream, fontSize = 16.sp)
            }
        }
    }
}

/** Le bouton du guide de musée : un casque, et l'onde qui bat pendant qu'il parle. */
@Composable
private fun GuideButton(speaking: Boolean, onClick: () -> Unit) {
    val beat = rememberInfiniteTransition(label = "guide")
    val k by beat.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "guideBeat")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (speaking) 0.22f + 0.08f * k else 0.14f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Icon(
            if (speaking) Icons.Outlined.StopCircle else Icons.Outlined.Headphones,
            contentDescription = null,
            tint = Play.Sun,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(if (speaking) R.string.guide_stop else R.string.guide_listen),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

/** Avant (la toile vierge, numérotée) et après (la toile peinte), séparés par un curseur à faire glisser. */
@Composable
private fun BeforeAfter(state: StudioState, modifier: Modifier = Modifier) {
    val canvas = state.canvas
    val before by produceState<Bitmap?>(null) {
        value = withContext(Dispatchers.Default) {
            runCatching { CanvasRenderer(canvas, PaintingSession(canvas), state.loaded.original).bitmap }.getOrNull()
        }
    }
    val after = remember { state.renderer.bitmap.asImageBitmap() }
    var split by remember { mutableFloatStateOf(0.5f) }
    Box(
        modifier
            .fillMaxWidth()
            .flatShadow(Color.Black.copy(alpha = 0.3f), 4.dp, 6.dp, 4.dp)
            .background(Color.White, RoundedCornerShape(4.dp))
            .padding(8.dp),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(canvas.width.toFloat() / canvas.height)
                .clip(RoundedCornerShape(2.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        split = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                }
                .pointerInput(Unit) { detectTapGestures { split = (it.x / size.width).coerceIn(0f, 1f) } }
                .clearAndSetSemantics {},
        ) {
            val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
            drawImage(after, srcOffset = IntOffset.Zero, srcSize = IntSize(after.width, after.height), dstSize = dst, filterQuality = FilterQuality.Medium)
            before?.let { b ->
                val image = b.asImageBitmap()
                clipRect(right = size.width * split) {
                    drawImage(image, srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height), dstSize = dst, filterQuality = FilterQuality.Medium)
                }
            }
            val x = size.width * split
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
            drawCircle(Play.Coral, 13.dp.toPx(), Offset(x, size.height / 2))
            drawCircle(Color.White, 13.dp.toPx(), Offset(x, size.height / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
        }
    }
}

@Composable
private fun Pill(text: String, background: Color, color: Color) {
    Text(
        text,
        color = color,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .flatShadow(Play.Ink, 2.dp, 3.dp, 18.dp)
            .background(background, RoundedCornerShape(18.dp))
            .border(2.dp, Play.Ink, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun GameButton(text: String, background: Color, color: Color, onClick: () -> Unit) {
    val filled = background != Color.Transparent
    Text(
        text,
        color = color,
        fontFamily = Fraunces,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 17.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .then(if (filled) Modifier.flatShadow(Play.Ink, 2.dp, 4.dp, 24.dp) else Modifier)
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .border(2.dp, if (filled) Play.Ink else Color.White, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    )
}
