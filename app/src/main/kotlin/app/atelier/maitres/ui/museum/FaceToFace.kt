package app.atelier.maitres.ui.museum

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atelier.maitres.R
import app.atelier.maitres.data.Career
import app.atelier.maitres.data.Frame
import app.atelier.maitres.data.Painting
import app.atelier.maitres.data.PaintingRepository
import app.atelier.maitres.ui.studio.MuseumGuide
import app.atelier.maitres.ui.theme.Caveat
import app.atelier.maitres.ui.theme.Play
import kotlin.math.abs
import kotlin.math.min

/**
 * Face à face avec une toile : on s'arrête devant elle, sous son spot, avec son cartel. On glisse
 * pour passer à la voisine le long du mur, on pince pour s'approcher des coups de pinceau, et le
 * guide de musée raconte l'histoire du tableau. [onClose] rend la toile devant laquelle on était.
 */
@Composable
internal fun FaceToFace(
    hung: List<Painting>,
    start: Int,
    painter: GalleryPainter?,
    thumbnails: Map<String, Bitmap>,
    repository: PaintingRepository,
    career: Career,
    frames: List<Frame>,
    chosen: SnapshotStateMap<String, Frame>,
    onClose: (Int) -> Unit,
) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = start) { hung.size }
    BackHandler { onClose(pager.currentPage) }

    var speaking by remember { mutableStateOf(false) }
    val guide = remember { MuseumGuide(context) { speaking = it } }
    DisposableEffect(guide) { onDispose { guide.release() } }

    // Les toiles en grand : celle que l'on regarde et ses deux voisines.
    val artworks = remember { mutableStateMapOf<String, Bitmap>() }
    LaunchedEffect(pager.settledPage) {
        guide.stop()
        val near = (pager.settledPage - 1..pager.settledPage + 1).mapNotNull { hung.getOrNull(it) }
        artworks.keys.retainAll(near.map { it.id }.toSet())
        for (p in near) if (p.id !in artworks) runCatching { repository.artwork(p) }.getOrNull()?.let { artworks[p.id] = it }
    }

    var zoomed by remember { mutableStateOf(false) }
    var shelfOpen by remember { mutableStateOf(false) }
    val wall = remember(painter) { painter?.let { FacadePainter(it) } }
    val gutter = 44.dp
    val gutterPx = with(LocalDensity.current) { gutter.toPx() }

    Box(Modifier.fillMaxSize().background(DARK)) {
        // Le mur défile avec les toiles qui y sont accrochées.
        Canvas(Modifier.fillMaxSize()) {
            val pageWidth = size.width - 2 * gutterPx
            wall?.wall(drawContext.canvas.nativeCanvas, size.width, size.height, (pager.currentPage + pager.currentPageOffsetFraction) * pageWidth)
        }
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = gutter),
            beyondViewportPageCount = 1,
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val painting = hung[page]
            OnWall(
                painting = painting,
                bitmap = artworks[painting.id] ?: thumbnails[painting.id],
                frame = chosen[painting.id] ?: Frame.CHENE,
                facade = wall,
                lit = abs(page - pager.currentPage - pager.currentPageOffsetFraction).coerceAtMost(1f),
                zoomable = page == pager.currentPage,
                onZoom = { zoomed = it },
            )
        }

        // En-tête : retour au couloir et place de la toile.
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(DARK.copy(alpha = 0.9f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 6.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Button) { onClose(pager.currentPage) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = Play.Cream)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.museum_back), color = Play.Cream, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.museum_position, pager.currentPage + 1, hung.size), color = WARM, fontSize = 16.sp)
        }

        // Le guide, l'aide au geste et les cadres.
        val current = hung[pager.currentPage]
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, DARK.copy(alpha = 0.92f), DARK)))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 48.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (guide.hasStory(current)) {
                ListenButton(speaking) { if (speaking) guide.stop() else guide.play(current) }
                Spacer(Modifier.height(12.dp))
            }
            Text(
                stringResource(R.string.museum_swipe),
                fontFamily = Caveat,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = WARM,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.museum_frames),
                color = Play.Cream,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.5.dp, GOLD, RoundedCornerShape(50))
                    .clickable(role = Role.Button) { shelfOpen = !shelfOpen }
                    .padding(horizontal = 28.dp, vertical = 9.dp),
            )
            AnimatedVisibility(shelfOpen, enter = expandVertically(), exit = shrinkVertically()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(12.dp))
                    FrameShelf(frames, painter, current, chosen) { frame ->
                        chosen[current.id] = frame
                        career.setFrame(current, frame)
                    }
                }
            }
        }
    }
}

/** Le gros bouton doré du guide : lecture ou arrêt, et une petite onde qui bat pendant qu'il parle. */
@Composable
private fun ListenButton(speaking: Boolean, onClick: () -> Unit) {
    val beat = rememberInfiniteTransition(label = "listen")
    val k by beat.animateFloat(0f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "listenBeat")
    Row(
        Modifier
            .padding(horizontal = 32.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(GOLD)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(Color(0xFFFFF8E8), CircleShape), contentAlignment = Alignment.Center) {
            Icon(if (speaking) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, contentDescription = null, tint = Color(0xFF96641E))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(if (speaking) R.string.museum_listen_stop else R.string.museum_listen),
                color = Color(0xFF301A0A),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
            Text(stringResource(R.string.museum_listen_sub), color = Color(0xFF56381A), fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 10.dp)) {
            for ((i, h) in listOf(8, 16, 12, 20, 13, 18, 9).withIndex()) {
                val wave = if (speaking) 0.45f + 0.55f * abs(((k + i * 0.29f) % 1f) * 2f - 1f) else 1f
                Box(Modifier.size(width = 4.dp, height = (h * wave).dp).background(Color(0xFF6E481A), RoundedCornerShape(2.dp)))
            }
        }
    }
}

/**
 * Une toile accrochée face à nous : halo du spot, lampe, cadre, toile et cartel. Quand elle est
 * devant nous ([zoomable]), deux doigts l'approchent ; un double toucher revient en arrière.
 */
@Composable
private fun OnWall(
    painting: Painting,
    bitmap: Bitmap?,
    frame: Frame,
    facade: FacadePainter?,
    lit: Float,
    zoomable: Boolean,
    onZoom: (Boolean) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(zoomable) {
        if (!zoomable) { scale = 1f; pan = Offset.Zero }
    }
    LaunchedEffect(scale > 1.01f, zoomable) { if (zoomable) onZoom(scale > 1.01f) }

    Canvas(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = "${painting.title}, ${painting.artist}" }
            .pointerInput(zoomable) {
                if (!zoomable) return@pointerInput
                detectTapGestures(onDoubleTap = { scale = 1f; pan = Offset.Zero })
            }
            .pointerInput(zoomable) {
                if (!zoomable) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        // Un seul doigt sans zoom : c'est au défilement des toiles de répondre.
                        if (fingers >= 2 || scale > 1.01f) {
                            val zoom = event.calculateZoom()
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val next = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                            val k = next / scale
                            var p = pan * k + (centroid - center) * (1 - k) + event.calculatePan()
                            val maxX = size.width * (next - 1) / 2f
                            val maxY = size.height * (next - 1) / 2f
                            p = Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
                            scale = next
                            pan = p
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = pan.x
                translationY = pan.y
            },
    ) {
        facade?.painting(drawContext.canvas.nativeCanvas, size.width, size.height, painting, bitmap, frame, 1f - 0.45f * lit)
    }
}

/**
 * Dessine le mur vu de face, avec les outils de dessin Android : damas et lambris de la galerie,
 * parquet au pied du mur, puis chaque toile sous son spot.
 */
internal class FacadePainter(private val gallery: GalleryPainter) {
    private val wallShader = BitmapShader(gallery.wallBitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
    private val floorShader = BitmapShader(gallery.floorBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val surface = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = LightingColorFilter(0x7A7268, 0x050302)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        color = 0xFF30201A.toInt()
    }
    private val caption = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xFF60463A.toInt()
    }
    private val matrix = Matrix()
    private val rect = RectF()
    private val halo = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(0x8CFFD696.toInt(), 0x5CFFD696, 0x2EFFD696, 0x10FFD696, 0x00FFD696),
        floatArrayOf(0f, 0.3f, 0.55f, 0.8f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val inset = LinearGradient(0f, 0f, 1f, 0f, 0x73000000, 0x00000000, Shader.TileMode.CLAMP)

    /** Le mur, décalé de [scroll] pixels quand on longe la galerie. */
    fun wall(c: android.graphics.Canvas, w: Float, h: Float, scroll: Float) {
        val wallH = h * WALL_SHARE
        val k = wallH / gallery.wallBitmap.height
        matrix.setScale(k, k)
        matrix.postTranslate(-scroll, 0f)
        wallShader.setLocalMatrix(matrix)
        surface.shader = wallShader
        c.drawRect(0f, 0f, w, wallH, surface)
        val f = k * 0.8f
        matrix.setScale(f, f)
        matrix.postTranslate(-scroll, wallH)
        floorShader.setLocalMatrix(matrix)
        surface.shader = floorShader
        c.drawRect(0f, wallH, w, h, surface)
        surface.shader = null
        // L'ombre du mur sur le parquet.
        fill.shader = LinearGradient(0f, wallH, 0f, wallH + h * 0.05f, 0x99000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        c.drawRect(0f, wallH, w, wallH + h * 0.05f, fill)
        fill.shader = null
    }

    /** Une toile au centre d'une page de [w] × [h], éclairée à [light] (1 : plein spot). */
    fun painting(c: android.graphics.Canvas, w: Float, h: Float, painting: Painting, bitmap: Bitmap?, frame: Frame, light: Float) {
        val aspect = bitmap?.let { it.width.toFloat() / it.height } ?: 0.8f
        val border = if (frame.style == Frame.Style.BAROQUE) 0.17f else 0.13f
        // La plus grande toile qui tient, cadre compris, dans la largeur et dans le haut du mur.
        val ph = min(h * 0.36f, w * 0.94f / (aspect + 2 * border))
        val pw = ph * aspect
        val b = ph * border
        val cx = w / 2f
        val cy = h * 0.38f
        val left = cx - pw / 2f
        val top = cy - ph / 2f

        // Halo du spot sur le damas.
        matrix.setScale(pw + 2 * b + w * 0.25f, ph * 1.5f + 2 * b)
        matrix.postTranslate(cx, top - b + ph * 0.25f)
        halo.setLocalMatrix(matrix)
        fill.shader = halo
        fill.alpha = (255 * light).toInt()
        c.drawRect(0f, 0f, w, h, fill)
        fill.shader = null
        fill.alpha = 255

        // La lampe en laiton au-dessus du cadre.
        val lampY = top - b - ph * 0.12f
        fill.color = 0xFF785C2C.toInt()
        c.drawRect(cx - ph * 0.012f, lampY - ph * 0.16f, cx + ph * 0.012f, lampY, fill)
        fill.color = 0xFFB08A46.toInt()
        rect.set(cx - ph * 0.14f, lampY - ph * 0.02f, cx + ph * 0.14f, lampY + ph * 0.05f)
        c.drawRoundRect(rect, ph * 0.03f, ph * 0.03f, fill)

        c.save()
        c.translate(left, top)
        // Ombre portée douce sous le cadre.
        fill.color = 0x2E190A05
        for (s in 0..3) {
            val grow = b * 0.25f * s
            c.drawRect(-b + b * 0.25f - grow, -b + b * 0.4f - grow, pw + b + b * 0.25f + grow, ph + b + b * 0.6f + grow, fill)
        }
        gallery.drawFrame(c, frame, pw, ph, b, null)
        if (bitmap != null) c.drawBitmap(bitmap, null, RectF(0f, 0f, pw, ph), image)
        else { fill.color = 0xFF3B2C22.toInt(); c.drawRect(0f, 0f, pw, ph, fill) }
        // La toile est en retrait : le cadre y jette une ombre en haut et à gauche.
        val depth = ph * 0.03f
        matrix.setScale(depth, 1f)
        inset.setLocalMatrix(matrix)
        fill.shader = inset
        c.drawRect(0f, 0f, depth, ph, fill)
        matrix.setValues(floatArrayOf(0f, 1f, 0f, depth, 0f, 0f, 0f, 0f, 1f))
        inset.setLocalMatrix(matrix)
        c.drawRect(0f, 0f, pw, depth, fill)
        fill.shader = null
        // Le reste de la galerie est dans la pénombre : la toile voisine aussi.
        if (light < 0.99f) {
            fill.color = ((255 * (1f - light)).toInt() shl 24) or 0x1E120C
            c.drawRect(-b, -b, pw + b, ph + b, fill)
        }
        c.restore()

        // Le cartel, sur le mur sous la toile.
        title.textSize = min(w * 0.055f, h * 0.024f)
        caption.textSize = title.textSize * 0.68f
        val second = listOf(painting.artist, painting.year).filter { it.isNotBlank() }.joinToString(" · ")
        val tw = maxOf(title.measureText(painting.title), caption.measureText(second)) + title.textSize * 1.4f
        val ty = top + ph + b + h * 0.03f
        rect.set(cx - tw / 2f, ty, cx + tw / 2f, ty + title.textSize * 2.6f)
        fill.color = 0xFFECDEBE.toInt()
        c.drawRoundRect(rect, title.textSize * 0.2f, title.textSize * 0.2f, fill)
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = title.textSize * 0.08f
        fill.color = 0xFF967032.toInt()
        c.drawRoundRect(rect, title.textSize * 0.2f, title.textSize * 0.2f, fill)
        fill.style = Paint.Style.FILL
        c.drawText(painting.title, cx, ty + title.textSize * 1.2f, title)
        c.drawText(second, cx, ty + title.textSize * 2.15f, caption)
        if (light < 0.99f) {
            fill.color = ((200 * (1f - light)).toInt() shl 24) or 0x1E120C
            c.drawRect(rect, fill)
        }
    }

    private companion object {
        /** Part de l'écran occupée par le mur ; le parquet prend le reste. */
        const val WALL_SHARE = 0.84f
    }
}

private const val MAX_ZOOM = 4f
private val DARK = Color(0xFF1E120C)
private val WARM = Color(0xFFF2D9B0)
private val GOLD = Color(0xFFC79942)
