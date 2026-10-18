package app.atelier.maitres.ui.studio

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.atelier.maitres.core.Difficulty
import app.atelier.maitres.data.Painting
import app.atelier.maitres.data.PaintingRepository
import app.atelier.maitres.ui.theme.CartelStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StudioScreen(painting: Painting, difficulty: Difficulty, repository: PaintingRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var state by remember { mutableStateOf<StudioState?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(painting.id, difficulty) {
        try {
            val loaded = repository.load(painting, difficulty) { loadProgress = it }
            state = withContext(Dispatchers.Default) { StudioState(loaded, repository, scope) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }

    // Sauvegarde à la sortie de l'écran et quand l'application passe en arrière-plan.
    val current = state
    DisposableEffect(current) { onDispose { current?.saveNow() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { state?.saveNow() }
    BackHandler(onBack = onBack)

    when {
        current != null -> Studio(current, onBack)
        failed -> LoadingScreen(painting, difficulty, progress = null, onBack = onBack)
        else -> LoadingScreen(painting, difficulty, progress = loadProgress, onBack = onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Studio(state: StudioState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var peek by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(state.session.paintedCount == 0) }
    val painting = state.loaded.painting
    val context = LocalContext.current
    val view = LocalView.current

    // Une vibration franche quand une couleur est terminée : la palette passe à la suivante.
    LaunchedEffect(state.colorsFinished) {
        if (state.colorsFinished > 0) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
                else HapticFeedbackConstants.LONG_PRESS,
            )
        }
    }

    fun share() {
        scope.launch {
            runCatching {
                val uri = state.shareableImage()
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    if (!painting.isImported) {
                        putExtra(Intent.EXTRA_TEXT, "« ${painting.title} », ${painting.artist}, repeint dans Atelier des Maîtres")
                    }
                    clipData = ClipData.newRawUri(null, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Partager la toile"))
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(context, "Le partage n'a pas pu être préparé.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(5_000)
        showHint = false
    }

    Scaffold(
        // Encoche et barres système comprises : rien ne passe dessous, même en paysage.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Retour à la galerie") }
                },
                title = {
                    state.version
                    val faithful = state.session.faithfulCount
                    val total = state.canvas.regionCount
                    Column {
                        Text(painting.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${state.loaded.difficulty.label} · $faithful / $total zones · ${faithful * 100 / maxOf(1, total)} %",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    // Maintenir pour voir l'original : on le compare à son propre travail.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (peek) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            .pointerInput(Unit) {
                                detectTapGestures(onPress = {
                                    peek = true
                                    tryAwaitRelease()
                                    peek = false
                                })
                            }
                            // Avec TalkBack, maintenir n'a pas de sens : l'action bascule l'original.
                            .semantics {
                                role = Role.Button
                                contentDescription = "Maintenir pour voir l'original"
                                stateDescription = if (peek) "Original affiché" else "Votre toile affichée"
                                onClick(label = if (peek) "Revenir à votre toile" else "Voir l'original") {
                                    peek = !peek
                                    true
                                }
                            },
                    ) {
                        Icon(Icons.Outlined.Visibility, contentDescription = null)
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Plus d'options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Vue d'ensemble") },
                            leadingIcon = { Icon(Icons.Outlined.FitScreen, contentDescription = null) },
                            onClick = {
                                menu = false
                                scope.launch { state.camera.animateToOverview() }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Partager la toile") },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                            onClick = {
                                menu = false
                                share()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Recommencer la toile") },
                            leadingIcon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                            onClick = {
                                menu = false
                                confirmRestart = true
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        val layoutDirection = LocalLayoutDirection.current
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                ),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
                PaintCanvas(state, peekOriginal = peek, modifier = Modifier.fillMaxSize())

                HintBanner(visible = showHint, modifier = Modifier.align(Alignment.TopCenter).padding(12.dp))

                // Outils flottants : annuler / rétablir à gauche, « trouver » à droite.
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.version
                    FloatingTool(Icons.AutoMirrored.Outlined.Undo, "Annuler", enabled = state.session.canUndo) { state.undo() }
                    FloatingTool(Icons.AutoMirrored.Outlined.Redo, "Rétablir", enabled = state.session.canRedo) { state.redo() }
                }
                FloatingTool(
                    Icons.Outlined.CenterFocusStrong,
                    "Trouver une zone à peindre",
                    enabled = true,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                ) { scope.launch { state.findNextRegion() } }
            }
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                // Seul le bas : les côtés sont déjà dégagés par la colonne.
                PaletteBar(state, Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)))
            }
        }
    }

    if (state.pickerOpen) ColorPickerSheet(state)

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            icon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
            title = { Text("Recommencer la toile ?") },
            text = { Text("Toutes les zones redeviennent blanches. Vous pourrez encore annuler.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestart = false
                    state.restart()
                }) { Text("Recommencer") }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Garder") } },
        )
    }

    if (state.showCompletion) {
        AlertDialog(
            onDismissRequest = { state.showCompletion = false },
            icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
            title = { Text("Chef-d'œuvre achevé", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column {
                    Text("Vous avez repeint « ${painting.title} » zone par zone, avec les couleurs du maître.")
                    if (painting.note.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(painting.note, style = CartelStyle)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    state.showCompletion = false
                    scope.launch { state.camera.animateToOverview() }
                }) { Text("Admirer") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        state.showCompletion = false
                        share()
                    }) { Text("Partager") }
                    TextButton(onClick = {
                        state.showCompletion = false
                        onBack()
                    }) { Text("Galerie") }
                }
            },
        )
    }
}

/** Bandeau d'aide affiché à l'ouverture d'une toile vierge. */
@Composable
private fun HintBanner(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier,
    ) {
        Surface(shape = RoundedCornerShape(50), tonalElevation = 6.dp, shadowElevation = 4.dp) {
            Text(
                "Touchez une zone pour la peindre · appui long pour choisir sa couleur",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).widthIn(max = 320.dp),
            )
        }
    }
}

@Composable
private fun FloatingTool(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.38f, label = "alpha")
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        modifier = modifier.size(48.dp).shadow(6.dp, CircleShape),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description) }
    }
}

@Composable
private fun LoadingScreen(painting: Painting, difficulty: Difficulty, progress: Float?, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(painting.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(painting.artist, style = CartelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.width(220.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.secondary,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "L'atelier prépare la toile (${difficulty.label.lowercase()})…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Cette image n'a pas pu être ouverte.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onBack) { Text("Retour à la galerie") }
            }
        }
    }
}
