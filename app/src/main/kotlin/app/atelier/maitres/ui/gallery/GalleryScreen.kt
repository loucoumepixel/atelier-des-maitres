package app.atelier.maitres.ui.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.atelier.maitres.core.Difficulty
import app.atelier.maitres.data.Painting
import app.atelier.maitres.data.PaintingRepository
import app.atelier.maitres.data.PaintingStats
import app.atelier.maitres.ui.theme.CartelStyle
import app.atelier.maitres.ui.theme.Pigments
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryScreen(repository: PaintingRepository, onOpen: (Painting, Difficulty) -> Unit) {
    val paintings by repository.paintings.collectAsState()
    val statsVersion by repository.statsVersion.collectAsState()
    var choosing by remember { mutableStateOf<Painting?>(null) }
    var deleting by remember { mutableStateOf<Painting?>(null) }
    var about by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            runCatching { repository.import(uri) }
                .onSuccess { choosing = it }
                .onFailure { snackbar.showSnackbar("Impossible de lire cette image.") }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // Encoche et barres système comprises : rien ne passe dessous, même en paysage.
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Atelier des Maîtres")
                        Text(
                            "Choisissez une toile, prenez le pinceau.",
                            style = CartelStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { about = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "À propos de l'application")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(168.dp),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalItemSpacing = 20.dp,
        ) {
            items(paintings, key = { it.id }) { painting ->
                // statsVersion force la relecture des pourcentages après chaque sauvegarde.
                val stats = remember(painting.id, statsVersion) { repository.stats(painting) }
                PaintingCard(
                    painting = painting,
                    repository = repository,
                    stats = stats,
                    onClick = { choosing = painting },
                    onLongClick = { if (painting.isImported) deleting = painting },
                )
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

    if (about) AboutDialog(onDismiss = { about = false })

    deleting?.let { painting ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("Retirer « ${painting.title} » ?") },
            text = { Text("L'image importée et votre progression sur cette toile seront supprimées.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { repository.delete(painting) }
                }) { Text("Retirer") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaintingCard(
    painting: Painting,
    repository: PaintingRepository,
    stats: List<PaintingStats>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val thumbnail by produceState<android.graphics.Bitmap?>(null, painting.id) {
        value = runCatching { repository.thumbnail(painting) }.getOrNull()
    }
    val best = stats.maxByOrNull { it.fraction }?.takeIf { it.faithful > 0 }
    val started = best != null
    // Les toiles jamais commencées sont présentées en sépia, comme une esquisse à peindre.
    val sepia by animateFloatAsState(if (started) 0f else 1f, tween(600), label = "sepia")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(
                onClickLabel = "Choisir le niveau",
                onLongClickLabel = if (painting.isImported) "Retirer cette image" else null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        // Cadre doré fin, à la manière d'une cimaise.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(3.dp, Brush.linearGradient(listOf(Pigments.OchreLight, Pigments.Gilt, Pigments.OchreLight))),
                    RoundedCornerShape(4.dp),
                )
                .padding(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val bitmap = thumbnail
            if (bitmap != null) {
                Image(
                    bitmap = remember(bitmap) { bitmap.asImageBitmap() },
                    // Le titre est lu juste en dessous : inutile de l'annoncer deux fois.
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(sepiaMatrix(sepia)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(bitmap.width.toFloat() / bitmap.height),
                )
            } else {
                Spacer(Modifier.fillMaxWidth().aspectRatio(0.8f))
            }
            if (best?.isComplete == true) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "Terminée",
                    tint = Pigments.PaperBright,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Pigments.Ultramarine.copy(alpha = 0.75f), RoundedCornerShape(50))
                        .padding(2.dp)
                        .size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            painting.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOf(painting.artist, painting.year).filter { it.isNotBlank() }.joinToString(", "),
            style = CartelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (best != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { best.fraction },
                    modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${(best.fraction * 100).toInt()} %",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
                Text("Importer une œuvre", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Un Monet, un Renoir, une photo de vacances : toute image de votre téléphone " +
                        "devient une toile à peindre.",
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
            Text("Choisissez votre niveau", style = MaterialTheme.typography.labelLarge)
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
                    Text("Retirer cette image")
                }
            }
        }
    }
}

@Composable
private fun DifficultyRow(difficulty: Difficulty, stat: PaintingStats?, highlighted: Boolean, onClick: () -> Unit) {
    val description = when (difficulty) {
        Difficulty.FACILE -> "Grandes zones, ${difficulty.colorCount} couleurs. Idéal pour se détendre."
        Difficulty.NORMAL -> "L'équilibre entre détail et plaisir, ${difficulty.colorCount} couleurs."
        Difficulty.EXPERT -> "Chaque nuance du maître, jusqu'à ${difficulty.colorCount} couleurs."
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(difficulty.label, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (stat != null && stat.faithful > 0) {
                Spacer(Modifier.width(12.dp))
                Text(
                    if (stat.isComplete) "Terminée" else "${(stat.fraction * 100).toInt()} %",
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
        title = { Text("Atelier des Maîtres") },
        text = {
            Column {
                if (version.isNotEmpty()) {
                    Text("Version $version", style = CartelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    "Toutes les toiles fournies sont dans le domaine public. Sans publicité, sans compte : " +
                        "rien ne quitte votre téléphone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { open(PRIVACY_URL) }) { Text("Politique de confidentialité") }
                TextButton(onClick = { open(SUPPORT_URL) }) { Text("Signaler un problème") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
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
