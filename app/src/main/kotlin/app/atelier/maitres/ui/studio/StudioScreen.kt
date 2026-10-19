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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MusicOff
import app.atelier.maitres.ui.theme.Fraunces
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.atelier.maitres.R
import app.atelier.maitres.core.Difficulty
import app.atelier.maitres.data.Career
import app.atelier.maitres.data.Painting
import app.atelier.maitres.data.PaintingRepository
import app.atelier.maitres.ui.labelRes
import app.atelier.maitres.ui.play.ColourDot
import app.atelier.maitres.ui.play.rankNames
import app.atelier.maitres.ui.theme.CartelStyle
import app.atelier.maitres.ui.theme.Play
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun StudioScreen(painting: Painting, difficulty: Difficulty, repository: PaintingRepository, career: Career, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var state by remember { mutableStateOf<StudioState?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(painting.id, difficulty) {
        try {
            val loaded = repository.load(painting, difficulty) { loadProgress = it }
            val daily = career.daily(repository.paintings.value)?.id == painting.id
            state = withContext(Dispatchers.Default) { StudioState(loaded, repository, career, daily, scope) }
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
        current != null -> Studio(current, career, onBack)
        failed -> LoadingScreen(painting, difficulty, progress = null, onBack = onBack)
        else -> LoadingScreen(painting, difficulty, progress = loadProgress, onBack = onBack)
    }
}

@Composable
private fun Studio(state: StudioState, career: Career, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var peek by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmRestart by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(state.session.paintedCount == 0) }
    val painting = state.loaded.painting
    val context = LocalContext.current
    val view = LocalView.current
    val look = atelierLook()
    var soundOn by remember { mutableStateOf(career.soundOn) }
    var musicOn by remember { mutableStateOf(career.musicOn) }
    var inFront by remember { mutableStateOf(true) }
    val sound = remember { runCatching { StudioSound(context.applicationContext) }.getOrNull() }
    DisposableEffect(sound) { onDispose { sound?.release() } }
    LaunchedEffect(sound) { sound?.prepare() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { inFront = true }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { inFront = false }
    // Le piano ne joue que dans l'atelier, à l'écran, et jamais quand le son est coupé.
    LaunchedEffect(soundOn, musicOn, inFront) { sound?.music(soundOn && musicOn && inFront) }

    // Une note de bois à chaque touche, si le son n'est pas coupé.
    LaunchedEffect(state.lastStroke) {
        val stroke = state.lastStroke ?: return@LaunchedEffect
        if (soundOn) sound?.stroke(stroke.faithful, stroke.combo)
    }
    LaunchedEffect(state.showCompletion) {
        if (state.showCompletion && soundOn) sound?.fanfare()
    }

    // Une vibration franche et un petit arpège quand une couleur est terminée : la palette passe à la suivante.
    LaunchedEffect(state.colorsFinished) {
        if (state.colorsFinished > 0) {
            if (soundOn && !state.showCompletion) sound?.colourDone()
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
                        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text, painting.title, painting.artist))
                    }
                    clipData = ClipData.newRawUri(null, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, context.getString(R.string.share_canvas)))
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(context, R.string.share_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(5_000)
        showHint = false
    }

    Box(Modifier.fillMaxSize()) {
        // Tout l'atelier est posé sur le mur : barre du haut, toile et palette.
        Column(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .atelierWall(look)
                // Encoche et barres système comprises : rien ne passe dessous, même en paysage.
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 4.dp),
            ) {
                WallButton(look, onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back_to_gallery))
                }
                state.version
                val faithful = state.session.faithfulCount
                val total = state.canvas.regionCount
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        painting.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = Fraunces,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                        lineHeight = 22.sp,
                        color = look.ink,
                    )
                    Text(
                        stringResource(
                            R.string.studio_progress,
                            stringResource(state.loaded.difficulty.labelRes),
                            faithful,
                            total,
                            faithful * 100 / maxOf(1, total),
                        ).uppercase(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                        color = look.meta,
                    )
                    val shown by animateFloatAsState(faithful.toFloat() / maxOf(1, total), label = "progress")
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(look.progressTrack),
                    ) {
                        Box(Modifier.fillMaxWidth(shown).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(look.progress))
                    }
                }
                if (sound != null) {
                    WallButton(look, onClick = {
                        soundOn = !soundOn
                        career.soundOn = soundOn
                        if (soundOn) sound.hello()
                    }) {
                        Icon(
                            if (soundOn) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                            contentDescription = stringResource(if (soundOn) R.string.sound_mute else R.string.sound_unmute),
                        )
                    }
                }
                // Maintenir pour voir l'original : on le compare à son propre travail.
                val peekHold = stringResource(R.string.peek_hold)
                val peekOn = stringResource(R.string.peek_on)
                val peekOff = stringResource(R.string.peek_off)
                val peekShow = stringResource(R.string.peek_show)
                val peekHide = stringResource(R.string.peek_hide)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(2.dp)
                        .wallButtonShadow(look)
                        .clip(CircleShape)
                        .background(if (peek) MaterialTheme.colorScheme.secondaryContainer else look.button)
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
                            contentDescription = peekHold
                            stateDescription = if (peek) peekOn else peekOff
                            onClick(label = if (peek) peekHide else peekShow) {
                                peek = !peek
                                true
                            }
                        },
                ) {
                    Icon(Icons.Outlined.Visibility, contentDescription = null, tint = if (peek) MaterialTheme.colorScheme.onSecondaryContainer else look.ink)
                }
                Box {
                    WallButton(look, onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.overview)) },
                            leadingIcon = { Icon(Icons.Outlined.FitScreen, contentDescription = null) },
                            onClick = {
                                menu = false
                                scope.launch { state.camera.animateToOverview() }
                            },
                        )
                        if (sound != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (musicOn) R.string.music_off else R.string.music_on)) },
                                leadingIcon = { Icon(if (musicOn) Icons.Outlined.MusicOff else Icons.Outlined.MusicNote, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    musicOn = !musicOn
                                    career.musicOn = musicOn
                                    // Demander la musique, c'est aussi vouloir du son.
                                    if (musicOn && !soundOn) {
                                        soundOn = true
                                        career.soundOn = true
                                    }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_canvas)) },
                            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                            onClick = {
                                menu = false
                                share()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.restart_canvas)) },
                            leadingIcon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                            onClick = {
                                menu = false
                                confirmRestart = true
                            },
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                PaintCanvas(state, peekOriginal = peek, look = look, modifier = Modifier.fillMaxSize())

                HintBanner(visible = showHint, look = look, modifier = Modifier.align(Alignment.TopCenter).padding(12.dp))
                ToastPill(state.toast, Modifier.align(Alignment.TopCenter).padding(top = 14.dp))

                // Outils flottants : annuler / rétablir à gauche, « trouver » à droite.
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.version
                    FloatingTool(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo), look, enabled = state.session.canUndo) { state.undo() }
                    FloatingTool(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.redo), look, enabled = state.session.canRedo) { state.redo() }
                }
                state.version
                val mistakes = state.session.mistakeCount
                FloatingTool(
                    Icons.Outlined.CenterFocusStrong,
                    if (mistakes > 0) pluralStringResource(R.plurals.find_mistake, mistakes, mistakes) else stringResource(R.string.find_area),
                    look,
                    enabled = true,
                    badge = mistakes,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                ) { scope.launch { state.findNextRegion() } }
            }
            PaletteBar(state, look, Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp))
        }

        if (state.pickerOpen) ColorPickerSheet(state)

        if (confirmRestart) {
            AlertDialog(
                onDismissRequest = { confirmRestart = false },
                icon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                title = { Text(stringResource(R.string.restart_title)) },
                text = { Text(stringResource(R.string.restart_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmRestart = false
                        state.restart()
                    }) { Text(stringResource(R.string.restart)) }
                },
                dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text(stringResource(R.string.keep)) } },
            )
        }

        if (state.showCompletion) {
            WinScreen(
                state = state,
                career = career,
                onShare = { share() },
                onOneMore = {
                    state.showCompletion = false
                    onBack()
                },
                onAdmire = {
                    state.showCompletion = false
                    scope.launch { state.camera.animateToOverview() }
                },
            )
            BackHandler { state.showCompletion = false }
        }
    }
}

/** Petit mot de jeu (série, couleur finie, nouveau rang) qui s'affiche un instant puis s'en va. */
@Composable
private fun ToastPill(toast: GameToast?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<GameToast?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(toast) {
        if (toast == null) return@LaunchedEffect
        shown = toast
        visible = true
        delay(if (toast is GameToast.RankUp) 2_600 else 1_600)
        visible = false
    }
    val names = rankNames()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut(),
        modifier = modifier,
    ) {
        // Le dernier mot reste affiché pendant qu'il s'efface.
        shown?.let { t -> ToastContent(t, names) }
    }
}

@Composable
private fun ToastContent(t: GameToast, names: Array<String>) {
    val rankUp = t is GameToast.RankUp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (rankUp) Play.Sun else Play.Ink.copy(alpha = 0.86f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        if (t is GameToast.ColourDone) {
            ColourDot(Color(t.color or (0xFF shl 24)))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            when (t) {
                is GameToast.Combo -> stringResource(R.string.combo, t.count)
                is GameToast.ColourDone -> stringResource(R.string.colour_done, t.number)
                is GameToast.RankUp -> stringResource(R.string.rank_up, names[t.rank])
            },
            color = if (rankUp) Play.Ink else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

/** Bandeau d'aide affiché à l'ouverture d'une toile vierge. */
@Composable
private fun HintBanner(visible: Boolean, look: AtelierLook, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.hint),
            style = MaterialTheme.typography.labelLarge,
            color = look.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .wallButtonShadow(look)
                .clip(RoundedCornerShape(50))
                .background(if (look.evening) Color(0xE61E140E) else Color(0xEBFFFCF5))
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .widthIn(max = 320.dp),
        )
    }
}

/** Ombre légère des boutons posés sur le mur, le jour seulement : le soir, la lampe n'en fait pas. */
private fun Modifier.wallButtonShadow(look: AtelierLook) =
    if (look.buttonShadow) shadow(2.dp, CircleShape, ambientColor = Color(0xFF3C1E0A), spotColor = Color(0xFF3C1E0A)) else this

/** Bouton rond, couleur crème le jour, à peine éclairé le soir. */
@Composable
private fun WallButton(look: AtelierLook, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = look.button,
        contentColor = look.ink,
        modifier = Modifier.size(48.dp).padding(2.dp).wallButtonShadow(look),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun FloatingTool(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    look: AtelierLook,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.38f, label = "alpha")
    Box(modifier) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = look.button,
            contentColor = look.ink.copy(alpha = alpha),
            modifier = Modifier.size(48.dp).wallButtonShadow(look),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description) }
        }
        // Le nombre de zones à reprendre, en pastille corail sur le bouton.
        if (badge > 0) {
            Text(
                if (badge > 99) "99+" else badge.toString(),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .clearAndSetSemantics {}
                    .widthIn(min = 20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Play.Coral)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
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
                    stringResource(R.string.preparing, stringResource(difficulty.labelRes).lowercase()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(stringResource(R.string.open_failed), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onBack) { Text(stringResource(R.string.back_to_gallery)) }
            }
        }
    }
}
