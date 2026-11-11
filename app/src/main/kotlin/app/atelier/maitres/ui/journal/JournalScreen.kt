package app.atelier.maitres.ui.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.atelier.maitres.R
import app.atelier.maitres.core.Achievement
import app.atelier.maitres.core.masterpieceGoal
import app.atelier.maitres.data.Career
import app.atelier.maitres.data.PaintingRepository
import app.atelier.maitres.ui.play.dottedPaper
import app.atelier.maitres.ui.play.formatCount
import app.atelier.maitres.ui.play.rankNames
import app.atelier.maitres.ui.theme.Fraunces
import app.atelier.maitres.ui.theme.HandStyle
import app.atelier.maitres.ui.theme.LocalPlayColors
import app.atelier.maitres.ui.theme.Play

/**
 * Le carnet de l'apprenti : la série de jours et son jour de grâce, les succès, l'histoire de
 * l'apprenti chapitre par chapitre (un par rang) et, au-delà de Génie, le Chef-d'œuvre.
 */
@Composable
fun JournalScreen(career: Career, repository: PaintingRepository, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val colors = LocalPlayColors.current
    val names = rankNames()
    val paintings = repository.paintings.value
    val progress = remember { career.progress(paintings) }
    val won = remember { Achievement.won(progress).toSet() }
    val achievementNames = stringArrayResource(R.array.achievement_names)
    val storyTitles = stringArrayResource(R.array.story_titles)
    val storyTexts = stringArrayResource(R.array.story_texts)
    val rank = career.rank
    val card = if (colors.ink == Play.Ink) Color(0xFFFFFBF5) else Color(0xFF2B2520)

    Box(Modifier.fillMaxSize().dottedPaper(colors)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.journal_close), tint = colors.ink)
                    }
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(
                            stringResource(R.string.journal_title),
                            fontFamily = Fraunces,
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp,
                            color = colors.ink,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            names[rank] + " · " + stringResource(R.string.touches_n, formatCount(career.touches)),
                            style = HandStyle,
                            color = colors.kraft,
                        )
                    }
                }

                // La série et son jour de grâce.
                JournalCard(card) {
                    val streak = career.streak
                    Text(
                        when (streak) {
                            0 -> stringResource(R.string.journal_streak_none)
                            1 -> stringResource(R.string.journal_streak_one) + " 🔥"
                            else -> stringResource(R.string.journal_streak, streak) + " 🔥"
                        },
                        fontFamily = Fraunces,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                        color = colors.ink,
                    )
                    val text = when {
                        streak == 0 -> stringResource(R.string.journal_streak_start)
                        career.streakNeedsGrace -> stringResource(R.string.journal_grace_used_today)
                        career.graces > 0 -> stringResource(R.string.journal_grace_ready)
                        else -> stringResource(R.string.journal_grace_none, career.daysToGrace)
                    }
                    Text(text, fontSize = 15.sp, lineHeight = 21.sp, color = colors.ink.copy(alpha = 0.85f), modifier = Modifier.padding(top = 4.dp))
                }

                // Les succès : médailles d'or pour les décrochés, grises pour les autres.
                JournalCard(card) {
                    Text(
                        stringResource(R.string.journal_achievements, won.size, Achievement.entries.size),
                        fontFamily = Fraunces,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                        color = colors.ink,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    // Les décrochés d'abord, dans l'ordre du catalogue des succès.
                    val ordered = remember(won) { Achievement.entries.sortedBy { if (it in won) 0 else 1 } }
                    val locked = stringResource(R.string.journal_locked)
                    ordered.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            row.forEach { a ->
                                val name = achievementNames.getOrElse(a.ordinal) { a.name }
                                Medal(
                                    a.emoji,
                                    name,
                                    a in won,
                                    colors.ink,
                                    Modifier.weight(1f).semantics(mergeDescendants = true) {
                                        contentDescription = if (a in won) name else "$name, $locked"
                                    },
                                )
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                // L'histoire de l'apprenti : un chapitre par rang atteint, le plus récent en haut.
                for (r in rank downTo 0) {
                    JournalCard(card) {
                        Text(
                            stringResource(R.string.journal_story, r + 1, storyTitles.getOrElse(r) { "" }),
                            fontFamily = Fraunces,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = colors.ink,
                        )
                        Text(
                            storyTexts.getOrElse(r) { "" },
                            style = HandStyle.copy(fontSize = if (r == rank) 21.sp else 18.sp, lineHeight = if (r == rank) 25.sp else 21.sp),
                            color = colors.ink.copy(alpha = if (r == rank) 0.9f else 0.7f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                // Après Génie : le Chef-d'œuvre, trois étoiles Expert partout.
                JournalCard(card) {
                    val (done, total) = masterpieceGoal(progress)
                    Text(
                        stringResource(R.string.journal_masterpiece),
                        fontFamily = Fraunces,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                        color = colors.ink,
                    )
                    Text(
                        stringResource(R.string.journal_masterpiece_text, done, total),
                        fontSize = 15.sp,
                        color = colors.ink.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.kraft.copy(alpha = 0.18f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(if (total == 0) 0f else done.toFloat() / total)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Play.Coral),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun JournalCard(background: Color, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) { content() }
}

/** Une médaille : pièce d'or et son emoji si le succès est décroché, pièce grise sinon. */
@Composable
private fun Medal(emoji: String, name: String, won: Boolean, ink: Color, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.padding(horizontal = 2.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(58.dp)
                .shadow(if (won) 3.dp else 0.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    if (won) Brush.radialGradient(listOf(Color(0xFFF7E3A0), Color(0xFFE2B649), Color(0xFFB98A2E)), center = Offset(40f, 30f))
                    else Brush.radialGradient(listOf(Color(0xFFEDEAE5), Color(0xFFD9D5CE))),
                )
                .clearAndSetSemantics {},
        ) {
            Text(emoji, fontSize = 26.sp, modifier = Modifier.alpha(if (won) 1f else 0.3f))
        }
        Text(
            name,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            color = ink.copy(alpha = if (won) 0.85f else 0.55f),
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp).clearAndSetSemantics {},
        )
    }
}
