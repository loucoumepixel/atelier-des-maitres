package app.atelier.maitres.ui.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atelier.maitres.R
import app.atelier.maitres.core.Difficulty
import app.atelier.maitres.data.Career
import app.atelier.maitres.data.Painting
import app.atelier.maitres.data.PaintingRepository
import app.atelier.maitres.data.PaintingStats
import app.atelier.maitres.ui.labelRes
import app.atelier.maitres.ui.play.DiceFace
import app.atelier.maitres.ui.play.FlameChip
import app.atelier.maitres.ui.play.LockIcon
import app.atelier.maitres.ui.play.RankBadge
import app.atelier.maitres.ui.play.Star
import app.atelier.maitres.ui.play.Stars
import app.atelier.maitres.ui.play.XpBar
import app.atelier.maitres.ui.play.dottedPaper
import app.atelier.maitres.ui.play.emphasized
import app.atelier.maitres.ui.play.flatShadow
import app.atelier.maitres.ui.play.formatCount
import app.atelier.maitres.ui.play.rankNames
import app.atelier.maitres.ui.theme.CartelStyle
import app.atelier.maitres.ui.theme.Caveat
import app.atelier.maitres.ui.theme.Fraunces
import app.atelier.maitres.ui.theme.HandStyle
import app.atelier.maitres.ui.theme.LocalPlayColors
import app.atelier.maitres.ui.theme.Play
import app.atelier.maitres.ui.theme.PlayTitle
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(repository: PaintingRepository, career: Career, onOpen: (Painting, Difficulty) -> Unit) {
    val paintings by repository.paintings.collectAsState()
    val statsVersion by repository.statsVersion.collectAsState()
    val state by career.state.collectAsState()
    val colors = LocalPlayColors.current
    var choosing by remember { mutableStateOf<Painting?>(null) }
    var locked by remember { mutableStateOf<Painting?>(null) }
    var deleting by remember { mutableStateOf<Painting?>(null) }
    var about by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val importFailed = stringResource(R.string.import_failed)
    val names = rankNames()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            runCatching { repository.import(uri) }
                .onSuccess { choosing = it }
                .onFailure { snackbar.showSnackbar(importFailed) }
        }
    }

    // La toile du jour ne bouge pas de la journée ; elle change si on la finit à tous les niveaux.
    val daily = remember(paintings, statsVersion, LocalDate.now()) { career.daily(paintings) }

    fun open(painting: Painting) {
        if (career.isUnlocked(painting)) choosing = painting else locked = painting
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().dottedPaper(colors),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            val label = stringResource(R.string.surprise_label)
            DiceFace(
                Modifier
                    .clickable(onClickLabel = label) {
                        val pool = paintings.filter { career.isUnlocked(it) }
                        val painting = pool.randomOrNull() ?: return@clickable
                        val won = career.stars(painting)
                        val difficulty = Difficulty.entries.filterIndexed { i, _ -> !won[i] }.randomOrNull()
                            ?: Difficulty.entries.random()
                        onOpen(painting, difficulty)
                    }
                    .semantics { contentDescription = label },
            )
        },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(156.dp),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 18.dp,
                end = padding.calculateEndPadding(layoutDirection) + 18.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalItemSpacing = 22.dp,
        ) {
            item(key = "career", span = StaggeredGridItemSpan.FullLine) {
                CareerHeader(
                    touches = state.touches,
                    streak = career.streak,
                    paintedToday = career.paintedToday,
                    names = names,
                    onAbout = { about = true },
                )
            }
            item(key = "question", span = StaggeredGridItemSpan.FullLine) {
                Text(
                    emphasized(R.string.gallery_q, R.string.gallery_q_em),
                    style = PlayTitle.copy(fontSize = 27.sp, lineHeight = 29.sp),
                    color = colors.ink,
                    modifier = Modifier.padding(top = 4.dp).semantics { heading() },
                )
            }
            daily?.let { painting ->
                item(key = "daily", span = StaggeredGridItemSpan.FullLine) {
                    DailyCard(painting, repository, onClick = { open(painting) })
                }
            }
            items(paintings.filter { it != daily }, key = { it.id }) { painting ->
                // statsVersion force la relecture des étoiles après chaque sauvegarde.
                val won = remember(painting.id, statsVersion) { career.stars(painting) }
                val started = remember(painting.id, statsVersion) { repository.stats(painting).any { it.faithful > 0 } }
                if (career.isUnlocked(painting)) {
                    Polaroid(
                        painting = painting,
                        repository = repository,
                        won = won,
                        started = started,
                        onClick = { choosing = painting },
                        onLongClick = { if (painting.isImported) deleting = painting },
                    )
                } else {
                    LockedPolaroid(painting, names[painting.unlockRank], onClick = { locked = painting })
                }
            }
            item(key = "import", span = StaggeredGridItemSpan.FullLine) {
                ImportCard(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            }
        }
    }

    choosing?.let { painting ->
        DifficultySheet(
            painting = painting,
            stats = remember(painting.id, statsVersion) { repository.stats(painting) },
            last = remember(painting.id, statsVersion) { repository.lastDifficulty(painting) },
            onDismiss = { choosing = null },
            onChoose = { difficulty ->
                choosing = null
                onOpen(painting, difficulty)
            },
            onDelete = if (painting.isImported) {
                {
                    choosing = null
                    deleting = painting
                }
            } else null,
        )
    }

    locked?.let { painting ->
        val missing = Career.RANK_AT[painting.unlockRank] - state.touches
        AlertDialog(
            onDismissRequest = { locked = null },
            icon = { LockIcon(Play.Coral) },
            title = { Text(stringResource(R.string.locked_title)) },
            text = { Text(stringResource(R.string.locked_text, names[painting.unlockRank], formatCount(missing.coerceAtLeast(1)))) },
            confirmButton = { TextButton(onClick = { locked = null }) { Text(stringResource(R.string.ok)) } },
        )
    }

    if (about) AboutDialog(onDismiss = { about = false })

    deleting?.let { painting ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.remove_title, painting.title)) },
            text = { Text(stringResource(R.string.remove_text)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { repository.delete(painting) }
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Rang, série de jours et barre de progression vers le rang suivant. */
@Composable
private fun CareerHeader(touches: Int, streak: Int, paintedToday: Boolean, names: Array<String>, onAbout: () -> Unit) {
    val colors = LocalPlayColors.current
    val rank = Career.rankOf(touches)
    val (fraction, next) = Career.progress(touches)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RankBadge()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    names[rank],
                    fontFamily = Fraunces,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(R.string.rank_of, rank + 1, Career.RANK_AT.size), style = HandStyle, color = colors.kraft)
            }
            val streakLabel = stringResource(if (paintedToday) R.string.streak_kept else R.string.streak_hint)
            FlameChip(streak, paintedToday, colors.kraft, Modifier.semantics { contentDescription = streakLabel })
            IconButton(onClick = onAbout) {
                Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.about_open), tint = colors.kraft)
            }
        }
        Spacer(Modifier.height(10.dp))
        XpBar(fraction, colors.ink)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(
                stringResource(R.string.touches_n, formatCount(touches)),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.kraft,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (next == null) stringResource(R.string.max_rank)
                else stringResource(R.string.next_rank, names[rank + 1], formatCount(next)),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.kraft,
            )
        }
    }
}

/** Petite inclinaison stable par toile, pour l'effet « photos posées sur la table ». */
private fun tiltOf(id: String): Float = ((abs(id.hashCode()) % 7) - 3) * 0.7f

@Composable
private fun rememberThumbnail(painting: Painting, repository: PaintingRepository) =
    produceState<android.graphics.Bitmap?>(null, painting.id) {
        value = runCatching { repository.thumbnail(painting) }.getOrNull()
    }

/** La toile du jour, en grand, qui rapporte double. */
@Composable
private fun DailyCard(painting: Painting, repository: PaintingRepository, onClick: () -> Unit) {
    val colors = LocalPlayColors.current
    val thumbnail by rememberThumbnail(painting, repository)
    Box(Modifier.fillMaxWidth().padding(top = 6.dp, end = 6.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .rotate(-1.2f)
                .flatShadow(Play.Ink.copy(alpha = 0.18f), 4.dp, 6.dp, 4.dp)
                .background(colors.polaroid, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClickLabel = stringResource(R.string.action_choose_level), onClick = onClick)
                .padding(10.dp),
        ) {
            val bitmap = thumbnail
            if (bitmap != null) {
                Image(
                    bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                )
            } else {
                Spacer(Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(colors.dots))
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.daily), style = HandStyle, color = Play.Coral)
                    Text(
                        painting.title,
                        fontFamily = Fraunces,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                        lineHeight = 21.sp,
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(painting.artist, style = CartelStyle, color = colors.kraft)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.paint_now),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .flatShadow(Play.Ink, 2.dp, 3.dp, 20.dp)
                        .background(Play.Coral, RoundedCornerShape(20.dp))
                        .border(2.dp, Play.Ink, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        // Tampon « ×2 » collé en coin.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .rotate(12f)
                .size(54.dp)
                .background(Play.Sun, CircleShape)
                .border(3.dp, Play.Ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("×2", fontFamily = Fraunces, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Play.Ink)
        }
    }
}

/** Une toile disponible, en polaroïd : photo, scotch, légende manuscrite et trois étoiles. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Polaroid(
    painting: Painting,
    repository: PaintingRepository,
    won: List<Boolean>,
    started: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = LocalPlayColors.current
    val thumbnail by rememberThumbnail(painting, repository)
    // Les toiles jamais commencées sont présentées en sépia, comme une esquisse à peindre.
    val sepia by animateFloatAsState(if (started) 0f else 1f, tween(600), label = "sepia")
    val count = won.count { it }
    val starsText = stringResource(R.string.stars_label, count)

    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .rotate(tiltOf(painting.id))
                .flatShadow(Play.Ink.copy(alpha = 0.16f), 3.dp, 5.dp, 3.dp)
                .background(colors.polaroid, RoundedCornerShape(3.dp))
                .clip(RoundedCornerShape(3.dp))
                .combinedClickable(
                    onClickLabel = stringResource(R.string.action_choose_level),
                    onLongClickLabel = if (painting.isImported) stringResource(R.string.remove_image) else null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .semantics(mergeDescendants = true) { contentDescription = "${painting.title}, ${painting.artist}, $starsText" }
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
        ) {
            val bitmap = thumbnail
            if (bitmap != null) {
                Image(
                    bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(sepiaMatrix(sepia)),
                    modifier = Modifier.fillMaxWidth().aspectRatio((bitmap.width.toFloat() / bitmap.height).coerceIn(0.7f, 1.4f)),
                )
            } else {
                Spacer(Modifier.fillMaxWidth().aspectRatio(0.85f).background(colors.dots))
            }
            Text(
                painting.title,
                fontFamily = Caveat,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                lineHeight = 19.sp,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Stars(won, colors.kraft, 15.dp)
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        count == 3 -> stringResource(R.string.cap_done)
                        !started -> stringResource(R.string.cap_new)
                        else -> ""
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (count == 3) Play.Coral else colors.kraft,
                )
            }
        }
        Tape(Modifier.align(Alignment.TopCenter))
    }
}

/** Bout de scotch jaune en travers du haut de la photo. */
@Composable
private fun Tape(modifier: Modifier = Modifier) {
    Box(
        modifier
            .offset(y = (-6).dp)
            .rotate(-4f)
            .size(width = 56.dp, height = 18.dp)
            .background(Play.Sun.copy(alpha = 0.72f)),
    )
}

/** Une toile encore emballée : papier kraft rayé et cadenas, avec le rang qui la déballe. */
@Composable
private fun LockedPolaroid(painting: Painting, rankName: String, onClick: () -> Unit) {
    val colors = LocalPlayColors.current
    val caption = stringResource(R.string.locked_cap, rankName)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .rotate(tiltOf(painting.id))
            .flatShadow(Play.Ink.copy(alpha = 0.12f), 3.dp, 5.dp, 3.dp)
            .background(colors.polaroid, RoundedCornerShape(3.dp))
            .clip(RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = caption }
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .background(colors.kraft.copy(alpha = 0.35f))
                .drawWithContent {
                    // Rayures diagonales du papier d'emballage.
                    val step = 14.dp.toPx()
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(
                            colors.kraft.copy(alpha = 0.28f),
                            Offset(x, size.height),
                            Offset(x + size.height, 0f),
                            strokeWidth = 5.dp.toPx(),
                        )
                        x += step
                    }
                    drawContent()
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(52.dp).background(colors.polaroid, CircleShape).border(2.5.dp, colors.ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) { LockIcon(colors.ink) }
        }
        Text(
            caption,
            fontFamily = Caveat,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 18.sp,
            color = colors.kraft,
            maxLines = 2,
            modifier = Modifier.padding(top = 8.dp).widthIn(max = 220.dp),
        )
    }
}

@Composable
private fun ImportCard(onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = androidx.compose.material3.CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.AddPhotoAlternate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.import_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.import_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DifficultySheet(
    painting: Painting,
    stats: List<PaintingStats>,
    last: Difficulty?,
    onDismiss: () -> Unit,
    onChoose: (Difficulty) -> Unit,
    onDelete: (() -> Unit)?,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text(painting.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                listOf(painting.artist, painting.year).filter { it.isNotBlank() }.joinToString(", "),
                style = CartelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (painting.note.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(painting.note, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.choose_level), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Difficulty.entries.forEach { difficulty ->
                val stat = stats.firstOrNull { it.difficulty == difficulty }
                DifficultyRow(difficulty, stat, highlighted = difficulty == (last ?: Difficulty.NORMAL)) { onChoose(difficulty) }
                Spacer(Modifier.height(8.dp))
            }
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.remove_image))
                }
            }
        }
    }
}

@Composable
private fun DifficultyRow(difficulty: Difficulty, stat: PaintingStats?, highlighted: Boolean, onClick: () -> Unit) {
    val description = when (difficulty) {
        Difficulty.FACILE -> stringResource(R.string.level_easy_text, difficulty.colorCount)
        Difficulty.NORMAL -> stringResource(R.string.level_normal_text, difficulty.colorCount)
        Difficulty.EXPERT -> stringResource(R.string.level_expert_text, difficulty.colorCount)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Star(difficulty, stat?.isComplete == true, MaterialTheme.colorScheme.onSurfaceVariant, 22.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(difficulty.labelRes), style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (stat != null && stat.faithful > 0) {
                Spacer(Modifier.width(12.dp))
                Text(
                    if (stat.isComplete) stringResource(R.string.finished)
                    else stringResource(R.string.percent, (stat.fraction * 100).toInt()),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

/** Version, confidentialité, contact et crédits : ce qu'un utilisateur (et la Play Console) s'attend à trouver. */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    fun open(url: String) {
        runCatching { uriHandler.openUri(url) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Palette, contentDescription = null) },
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column {
                if (version.isNotEmpty()) {
                    Text(stringResource(R.string.version, version), style = CartelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    stringResource(R.string.about_text),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { open(PRIVACY_URL) }) { Text(stringResource(R.string.privacy_policy)) }
                TextButton(onClick = { open(SUPPORT_URL) }) { Text(stringResource(R.string.report_problem)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

private const val PRIVACY_URL = "https://github.com/loucoumepixel/atelier-des-maitres/blob/main/docs/confidentialite.md"
private const val SUPPORT_URL = "https://github.com/loucoumepixel/atelier-des-maitres/issues"

/** Interpolation entre l'image d'origine (0) et un sépia délavé (1). */
private fun sepiaMatrix(amount: Float): ColorMatrix {
    val a = amount.coerceIn(0f, 1f)
    val identity = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
    // Sépia léger, éclairci vers le papier pour évoquer une esquisse.
    val sepia = floatArrayOf(
        0.33f, 0.52f, 0.13f, 0f, 30f,
        0.30f, 0.47f, 0.12f, 0f, 26f,
        0.24f, 0.37f, 0.09f, 0f, 22f,
        0f, 0f, 0f, 1f, 0f,
    )
    return ColorMatrix(FloatArray(20) { i -> identity[i] + (sepia[i] - identity[i]) * a })
}
