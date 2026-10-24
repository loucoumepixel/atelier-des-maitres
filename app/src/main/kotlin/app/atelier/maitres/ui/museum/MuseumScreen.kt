package app.atelier.maitres.ui.museum

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atelier.maitres.R
import app.atelier.maitres.core.Corridor
import app.atelier.maitres.core.Moulding
import app.atelier.maitres.data.Career
import app.atelier.maitres.data.Frame
import app.atelier.maitres.data.Painting
import app.atelier.maitres.data.PaintingRepository
import app.atelier.maitres.ui.theme.Caveat
import app.atelier.maitres.ui.theme.Fraunces
import app.atelier.maitres.ui.theme.Play
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Mon musée : les toiles terminées, accrochées dans une grande galerie aux murs de damas rouge,
 * que l'on parcourt en glissant le doigt. Toucher une toile la sélectionne ; l'étagère du bas
 * change son cadre.
 */
@Composable
fun MuseumScreen(repository: PaintingRepository, career: Career, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val all by repository.paintings.collectAsState()
    val hung = remember(all) { all.filter { p -> career.stars(p).any { it } } }
    val frames = remember(all) { career.frames(all) }
    val thumbnails = remember { mutableStateMapOf<String, Bitmap>() }
    val chosen = remember { mutableStateMapOf<String, Frame>().apply { hung.forEach { put(it.id, career.frameOf(it)) } } }
    var selected by remember { mutableIntStateOf(-1) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val corridor = remember(hung.size, viewSize) { Corridor(hung.size, viewSize.width.toFloat(), viewSize.height.toFloat()) }
    val camera = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // Les coins à l'écran de chaque encadrement, au dernier dessin, pour reconnaître le toucher.
    val quads = remember(hung.size) { Array(hung.size) { FloatArray(8) } }
    val shown = remember(hung.size) { BooleanArray(hung.size) }
    val textures by produceState<GalleryPainter?>(null) { value = withContext(Dispatchers.IO) { GalleryPainter.load(context) } }

    LaunchedEffect(hung) {
        hung.forEach { p -> runCatching { repository.thumbnail(p) }.getOrNull()?.let { thumbnails[p.id] = it } }
    }

    Box(Modifier.fillMaxSize().background(DARK)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it }
                .pointerInput(corridor) {
                    val tracker = VelocityTracker()
                    // Glisser vers le haut ou vers la gauche fait avancer dans la galerie.
                    val unitsPerPx = 4f / max(1, this.size.height)
                    detectDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDragEnd = {
                            val v = tracker.calculateVelocity()
                            val speed = -(v.y + v.x) * unitsPerPx
                            scope.launch {
                                camera.updateBounds(0f, corridor.maxCamera)
                                camera.animateDecay(speed, exponentialDecay(frictionMultiplier = 1.4f))
                            }
                        },
                    ) { change, drag ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        val next = (camera.value - (drag.y + drag.x) * unitsPerPx).coerceIn(0f, corridor.maxCamera)
                        scope.launch { camera.snapTo(next) }
                    }
                }
                .pointerInput(corridor) {
                    detectTapGestures { at ->
                        // La toile la plus proche d'abord : elle cache celles du fond.
                        val hit = hung.indices
                            .filter { shown[it] && Corridor.contains(quads[it], at.x, at.y) }
                            .minByOrNull { corridor.hangAt(it) }
                        selected = if (hit == null || hit == selected) -1 else hit
                    }
                }
                .semantics { contentDescription = hung.joinToString { it.title } },
        ) {
            val painter = textures ?: return@Canvas
            if (viewSize.width == 0) return@Canvas
            val native = drawContext.canvas.nativeCanvas
            val cam = camera.value
            painter.room(native, corridor, cam)
            // Du fond vers l'entrée : les toiles proches recouvrent les lointaines.
            for (i in hung.indices.sortedByDescending { corridor.hangAt(it) }) {
                val painting = hung[i]
                shown[i] = painter.painting(
                    native, corridor, cam, i, painting, thumbnails[painting.id],
                    chosen[painting.id] ?: Frame.CHENE, i == selected, quads[i],
                )
            }
            painter.vignette(native, corridor)
        }

        if (hung.isEmpty()) {
            Text(
                stringResource(R.string.museum_empty),
                fontFamily = Caveat,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Play.Cream,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(40.dp),
            )
        }

        // En-tête : titre, nombre de toiles et retour.
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(DARK.copy(alpha = 0.92f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 40.dp),
        ) {
            Column(Modifier.padding(end = 52.dp)) {
                Text(stringResource(R.string.museum_title), fontFamily = Fraunces, fontWeight = FontWeight.Black, fontSize = 30.sp, color = Color.White)
                Text(
                    pluralStringResource(R.plurals.museum_count, hung.size, hung.size),
                    fontFamily = Caveat,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                    color = WARM,
                )
            }
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopEnd).background(Color.White.copy(alpha = 0.2f), CircleShape)) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close), tint = Color.White)
            }
        }

        // Étagère des cadres.
        if (hung.isNotEmpty()) Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, DARK.copy(alpha = 0.95f))))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 40.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val target = hung.getOrNull(selected)
            Text(
                if (target == null) stringResource(R.string.museum_hint) else stringResource(R.string.museum_frame_hint, target.title),
                fontFamily = Caveat,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                color = WARM,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val shelf = (frames + if (Frame.BAROQUE in frames) emptyList() else listOf(Frame.BAROQUE)).sortedBy { it.ordinal }
                shelf.forEach { frame ->
                    val locked = frame !in frames
                    FrameSwatch(
                        frame = frame,
                        painter = textures,
                        on = target != null && chosen[target.id] == frame,
                        enabled = target != null && !locked,
                        caption = if (locked) stringResource(R.string.museum_baroque_at, Frame.BAROQUE_AT) else null,
                    ) {
                        if (target != null) {
                            chosen[target.id] = frame
                            career.setFrame(target, frame)
                        }
                    }
                }
            }
        }
    }
}

/** Un petit cadre vide sur l'étagère, dessiné comme ceux du musée, avec son nom. */
@Composable
private fun FrameSwatch(frame: Frame, painter: GalleryPainter?, on: Boolean, enabled: Boolean, caption: String?, onClick: () -> Unit) {
    val alpha = if (enabled || on) 1f else 0.45f
    Column(
        Modifier
            .width(64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 44.dp)
                .then(if (on) Modifier.border(2.dp, Play.Sun, RoundedCornerShape(4.dp)) else Modifier)
                .padding(4.dp)
                .drawBehind {
                    val c = drawContext.canvas.nativeCanvas
                    val b = size.height * 0.22f
                    val count = c.saveLayerAlpha(0f, 0f, size.width, size.height, (alpha * 255).toInt())
                    c.translate(b, b)
                    painter?.drawFrame(c, frame, size.width - 2 * b, size.height - 2 * b, b, SWATCH_INSIDE)
                    c.restoreToCount(count)
                },
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(frame.labelRes), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Play.Cream.copy(alpha = alpha), textAlign = TextAlign.Center, maxLines = 2)
        if (caption != null) Text(caption, fontSize = 10.sp, color = Play.Cream.copy(alpha = alpha), textAlign = TextAlign.Center)
    }
}

/**
 * Dessine la galerie avec les outils de dessin Android. Murs, sol et plafond sont des textures
 * plaquées en perspective (une matrice par surface) ; chaque toile est dessinée dans son propre
 * repère, plaqué sur son mur : halo du spot, ombre, cadre mouluré, toile et cartel.
 */
private class GalleryPainter(wall: Bitmap, floor: Bitmap, ceiling: Bitmap, private val end: Bitmap) {
    private val wallHeight = wall.height.toFloat()
    private val floorWidth = floor.width.toFloat()
    private val ceilingWidth = ceiling.width.toFloat()
    private val wallShader = BitmapShader(wall, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP)
    private val floorShader = BitmapShader(floor, Shader.TileMode.CLAMP, Shader.TileMode.REPEAT)
    private val ceilingShader = BitmapShader(ceiling, Shader.TileMode.CLAMP, Shader.TileMode.REPEAT)
    private val endShader = BitmapShader(end, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

    // La pénombre de la galerie, et la brume qui s'épaissit au loin.
    private val surface = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = LightingColorFilter(0xADADAD, 0x0A0503)
    }
    private val bare = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fogAlongU = LinearGradient(FOG_START * PPU, 0f, FOG_END * PPU, 0f, FOG and 0xFFFFFF, FOG_MAX, Shader.TileMode.CLAMP)
    private val fogAlongV = LinearGradient(0f, FOG_START * PPU, 0f, FOG_END * PPU, FOG and 0xFFFFFF, FOG_MAX, Shader.TileMode.CLAMP)
    private val fogPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val image = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        color = 0xFF3C2C1E.toInt()
    }
    private val layer = Paint()
    private val path = Path()
    private val matrix = Matrix()
    private val local = Matrix()
    private val world = FloatArray(12)
    private val screen = FloatArray(8)
    private val src = FloatArray(8)
    private val rect = RectF()

    // Halo des spots (un disque unité, étiré en ellipse) et ombre intérieure de la toile.
    private val halo = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(0xCCFFD696.toInt(), 0x90FFD696.toInt(), 0x59FFD696, 0x27FFD696, 0x00FFD696),
        floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val inset = LinearGradient(0f, 0f, 1f, 0f, 0x73000000, 0x00000000, Shader.TileMode.CLAMP)
    private val vignette = RadialGradient(
        0f, 0f, 1f, intArrayOf(0x00000000, 0x00000000, 0x8C000000.toInt()), floatArrayOf(0f, 0.35f, 0.85f), Shader.TileMode.CLAMP,
    )
    private val mouldings = HashMap<Frame, Array<Shader>>()

    fun room(c: android.graphics.Canvas, k: Corridor, camera: Float) {
        val near = camera + Corridor.NEAR + 1e-3f
        val far = k.length
        val w = Corridor.WALL
        val top = Corridor.CEILING
        val bottom = Corridor.FLOOR
        val shift = camera * PPU
        // Les textures sont accrochées au monde : u (ou v) = z × PPU, si bien qu'elles défilent avec le pas.
        tex(near * PPU, 0f, far * PPU, 0f, far * PPU, wallHeight, near * PPU, wallHeight)
        plane(c, k, camera, -w, top, near, -w, top, far, -w, bottom, far, -w, bottom, near, wallShader, fogAlongU, shift, 0f)
        plane(c, k, camera, w, top, near, w, top, far, w, bottom, far, w, bottom, near, wallShader, fogAlongU, shift, 0f)
        tex(0f, near * PPU, floorWidth, near * PPU, floorWidth, far * PPU, 0f, far * PPU)
        plane(c, k, camera, -w, bottom, near, w, bottom, near, w, bottom, far, -w, bottom, far, floorShader, fogAlongV, 0f, shift)
        tex(0f, near * PPU, ceilingWidth, near * PPU, ceilingWidth, far * PPU, 0f, far * PPU)
        plane(c, k, camera, -w, top, near, w, top, near, w, top, far, -w, top, far, ceilingShader, fogAlongV, 0f, shift)
        // Le mur du fond et sa porte lumineuse : déjà dans la pénombre, dans sa texture même.
        tex(0f, 0f, end.width.toFloat(), 0f, end.width.toFloat(), end.height.toFloat(), 0f, end.height.toFloat())
        plane(c, k, camera, -w, top, far, w, top, far, w, bottom, far, -w, bottom, far, endShader, null, 0f, 0f)
    }

    /** Assombrit les bords de l'écran, comme l'œil dans une salle peu éclairée. */
    fun vignette(c: android.graphics.Canvas, k: Corridor) {
        local.setScale(k.width, k.height)
        local.postTranslate(k.centerX, k.height / 2f)
        vignette.setLocalMatrix(local)
        fill.shader = vignette
        c.drawRect(0f, 0f, k.width, k.height, fill)
        fill.shader = null
    }

    /**
     * Une toile et son cadre ; renvoie faux si elle est derrière le visiteur. [out] reçoit les
     * coins de l'encadrement à l'écran.
     */
    fun painting(
        c: android.graphics.Canvas, k: Corridor, camera: Float, i: Int, painting: Painting, bitmap: Bitmap?,
        frame: Frame, selected: Boolean, out: FloatArray,
    ): Boolean {
        val pw = (bitmap?.width ?: 400).toFloat()
        val ph = (bitmap?.height ?: 500).toFloat()
        val b = ph * if (frame.style == Frame.Style.BAROQUE) 0.17f else 0.13f
        if (!k.project(k.corners(i, pw / ph, b / ph), camera, screen)) return false
        screen.copyInto(out)
        tex(-b, -b, pw + b, -b, pw + b, ph + b, -b, ph + b)
        matrix.setPolyToPoly(src, 0, screen, 0, 4)

        val fw = pw + 2 * b
        val fh = ph + 2 * b
        val fog = ((k.hangAt(i) - camera - 2.5f) / 16f).coerceIn(0f, 0.5f)
        c.save()
        c.concat(matrix)
        rect.set(-b - fw * 1.2f, -b - fh * 1.1f, pw + b + fw * 1.2f, ph + b + ph * 0.3f)
        val count = if (fog > 0.02f) {
            // La brume du lointain voile la toile et son halo.
            val keep = 1f - fog
            val mul = (keep * 255).toInt().let { (it shl 16) or (it shl 8) or it }
            val add = ((0x1E * fog).toInt() shl 16) or ((0x10 * fog).toInt() shl 8) or (0x0A * fog).toInt()
            layer.colorFilter = LightingColorFilter(mul, add)
            c.saveLayer(rect, layer)
        } else -1

        // Halo du spot, centré un peu au-dessus du haut de la toile.
        local.setScale(fw * 1.15f, fh * 1.1f)
        local.postTranslate(pw / 2f, -b + fh * 0.15f)
        halo.setLocalMatrix(local)
        fill.shader = halo
        c.drawRect(rect, fill)
        fill.shader = null
        // Ombre portée douce : quelques voiles superposés, de plus en plus larges.
        fill.color = 0x26190A05
        for (s in 0..3) {
            val grow = b * 0.22f * s
            c.drawRect(-b + b * 0.3f - grow, b * -0.1f - grow, pw + b + b * 0.3f + grow, ph + b * 2.1f + grow, fill)
        }
        drawFrame(c, frame, pw, ph, b, null)
        if (bitmap != null) c.drawBitmap(bitmap, null, RectF(0f, 0f, pw, ph), image)
        else { fill.color = 0xFF3B2C22.toInt(); c.drawRect(0f, 0f, pw, ph, fill) }
        // La toile est en retrait : le cadre y jette une ombre en haut et à gauche.
        val depth = ph * 0.035f
        local.setScale(depth, 1f)
        inset.setLocalMatrix(local)
        fill.shader = inset
        c.drawRect(0f, 0f, depth, ph, fill)
        local.setValues(floatArrayOf(0f, 1f, 0f, depth, 0f, 0f, 0f, 0f, 1f))
        inset.setLocalMatrix(local)
        c.drawRect(0f, 0f, pw, depth, fill)
        fill.shader = null

        // Cartel.
        val label = listOf(painting.title, painting.artist).filter { it.isNotBlank() }.joinToString(" · ")
        text.textSize = ph * 0.038f
        val tw = min(text.measureText(label), fw * 1.3f)
        val ly = ph + b + ph * 0.07f
        fill.color = 0xFFFAF4E6.toInt()
        c.drawRect(pw / 2f - tw / 2f - ph * 0.04f, ly, pw / 2f + tw / 2f + ph * 0.04f, ly + ph * 0.075f, fill)
        c.drawText(label, pw / 2f, ly + ph * 0.051f, text)
        if (selected) {
            stroke.color = 0xFFFFC93C.toInt()
            stroke.strokeWidth = b * 0.22f
            c.drawRect(-b * 1.3f, -b * 1.3f, pw + b * 1.3f, ph + b * 1.3f, stroke)
        }
        if (count >= 0) c.restoreToCount(count)
        c.restore()
        return true
    }

    /**
     * Le cadre autour d'une ouverture de [pw] × [ph], de largeur [b] : quatre baguettes en onglet,
     * chacune peinte du profil de la moulure sous sa propre lumière. [inside] remplit l'ouverture.
     */
    fun drawFrame(c: android.graphics.Canvas, frame: Frame, pw: Float, ph: Float, b: Float, inside: Int?) {
        if (inside != null) {
            fill.color = inside
            c.drawRect(0f, 0f, pw, ph, fill)
        }
        val shaders = mouldings.getOrPut(frame) {
            Array(4) { side ->
                val colors = Moulding.colors(frame.wood.toInt(), side, gilt = frame.style != Frame.Style.BOIS)
                LinearGradient(0f, 0f, 1f, 0f, colors, null, Shader.TileMode.CLAMP)
            }
        }
        // Haut, gauche, droite, bas : le dégradé va du bord extérieur (0) vers la toile (1).
        side(c, shaders[0], floatArrayOf(0f, 1f, 0f, b, 0f, -b, 0f, 0f, 1f), -b, -b, pw + b, -b, pw, 0f, 0f, 0f)
        side(c, shaders[1], floatArrayOf(b, 0f, -b, 0f, 1f, 0f, 0f, 0f, 1f), -b, -b, 0f, 0f, 0f, ph, -b, ph + b)
        side(c, shaders[2], floatArrayOf(-b, 0f, pw + b, 0f, 1f, 0f, 0f, 0f, 1f), pw + b, -b, pw + b, ph + b, pw, ph, pw, 0f)
        side(c, shaders[3], floatArrayOf(0f, 1f, 0f, -b, 0f, ph + b, 0f, 0f, 1f), -b, ph + b, 0f, ph, pw, ph, pw + b, ph + b)
        if (frame.style == Frame.Style.BOIS) {
            // Le fil du bois : de fines veines le long de chaque baguette.
            stroke.color = 0x1A000000
            stroke.strokeWidth = b * 0.03f
            for (t in floatArrayOf(0.12f, 0.31f, 0.57f, 0.69f)) {
                c.drawRect(-b + b * t, -b + b * t, pw + b - b * t, ph + b - b * t, stroke)
            }
        }
        if (frame.style == Frame.Style.BAROQUE) ornaments(c, frame, pw, ph, b)
    }

    /** Le cadre baroque : un rang de perles dorées et une rosace à chaque coin. */
    private fun ornaments(c: android.graphics.Canvas, frame: Frame, pw: Float, ph: Float, b: Float) {
        val base = frame.wood.toInt()
        fill.color = tint(base, 1.4f)
        val inset = b * 0.38f
        val step = b * 0.32f
        val r = b * 0.055f
        var x = 0f
        while (x <= pw) {
            c.drawCircle(x, -inset, r, fill)
            c.drawCircle(x, ph + inset, r, fill)
            x += step
        }
        var y = 0f
        while (y <= ph) {
            c.drawCircle(-inset, y, r, fill)
            c.drawCircle(pw + inset, y, r, fill)
            y += step
        }
        for ((cx, cy) in listOf(-b / 2 to -b / 2, pw + b / 2 to -b / 2, pw + b / 2 to ph + b / 2, -b / 2 to ph + b / 2)) {
            fill.color = tint(base, 0.75f)
            c.drawCircle(cx + b * 0.03f, cy + b * 0.05f, b * 0.62f, fill)
            fill.color = tint(base, 1.3f)
            c.drawCircle(cx, cy, b * 0.6f, fill)
            fill.color = tint(base, 1.0f)
            for (petal in 0 until 6) {
                val a = petal * Math.PI / 3
                c.drawCircle(cx + (b * 0.34f * Math.cos(a)).toFloat(), cy + (b * 0.34f * Math.sin(a)).toFloat(), b * 0.16f, fill)
            }
            fill.color = tint(base, 1.5f)
            c.drawCircle(cx - b * 0.05f, cy - b * 0.05f, b * 0.14f, fill)
        }
    }

    private fun side(c: android.graphics.Canvas, shader: Shader, m: FloatArray, vararg p: Float) {
        local.setValues(m)
        shader.setLocalMatrix(local)
        path.reset()
        path.moveTo(p[0], p[1])
        for (k in 2 until p.size step 2) path.lineTo(p[k], p[k + 1])
        path.close()
        fill.shader = shader
        c.drawPath(path, fill)
        fill.shader = null
    }

    private fun tex(vararg p: Float) = p.copyInto(src)

    /**
     * Une surface du monde (quatre coins x, y, z), couverte de [shader] posé selon les coins de
     * texture de [src], puis voilée de brume ; la brume suit la profondeur, décalée de [fogU], [fogV].
     */
    private fun plane(
        c: android.graphics.Canvas, k: Corridor, camera: Float,
        x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float, x3: Float, y3: Float, z3: Float,
        shader: Shader, fog: Shader?, fogU: Float, fogV: Float,
    ) {
        world[0] = x0; world[1] = y0; world[2] = z0
        world[3] = x1; world[4] = y1; world[5] = z1
        world[6] = x2; world[7] = y2; world[8] = z2
        world[9] = x3; world[10] = y3; world[11] = z3
        k.project(world, camera, screen)
        matrix.setPolyToPoly(src, 0, screen, 0, 4)
        path.reset()
        path.moveTo(screen[0], screen[1])
        path.lineTo(screen[2], screen[3])
        path.lineTo(screen[4], screen[5])
        path.lineTo(screen[6], screen[7])
        path.close()
        shader.setLocalMatrix(matrix)
        val paint = if (fog == null) bare else surface
        paint.shader = shader
        c.drawPath(path, paint)
        if (fog != null) {
            local.set(matrix)
            local.preTranslate(fogU, fogV)
            fog.setLocalMatrix(local)
            fogPaint.shader = fog
            c.drawPath(path, fogPaint)
        }
    }

    private fun tint(color: Int, f: Float): Int {
        val r = (((color shr 16) and 0xFF) * f).toInt().coerceAtMost(255)
        val g = (((color shr 8) and 0xFF) * f).toInt().coerceAtMost(255)
        val b = ((color and 0xFF) * f).toInt().coerceAtMost(255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    companion object {
        /** Pixels de texture par unité du monde. */
        const val PPU = 256f
        val FOG = 0xFF1E100A.toInt()
        const val FOG_MAX = 0x731E100A
        const val FOG_START = 1.5f
        const val FOG_END = 7.8f

        fun load(context: Context): GalleryPainter {
            fun read(name: String) = context.assets.open("museum/$name").use { BitmapFactory.decodeStream(it) }
            return GalleryPainter(read("mur.jpg"), read("parquet.jpg"), read("plafond.jpg"), read("fond.jpg"))
        }
    }
}

private const val SWATCH_INSIDE = 0xFF34261E.toInt()
private val DARK = Color(0xFF1E120C)
private val WARM = Color(0xFFF2D9B0)
