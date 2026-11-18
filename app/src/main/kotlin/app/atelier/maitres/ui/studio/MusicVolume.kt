package app.atelier.maitres.ui.studio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.atelier.maitres.R
import app.atelier.maitres.data.Career
import kotlin.math.roundToInt

/**
 * Le curseur du volume de la musique : on l'entend changer pendant qu'on le glisse.
 * Il règle la musique de l'atelier comme celle du musée.
 */
@Composable
fun MusicVolumeSlider(career: Career, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    val volume by career.musicVolumeFlow.collectAsState()
    val label = stringResource(R.string.music_volume)
    val percent = stringResource(R.string.music_volume_percent, (volume * 100).roundToInt())
    Column(modifier) {
        if (showTitle) Text(label, style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.VolumeDown, contentDescription = null)
            Slider(
                value = volume,
                onValueChange = { career.musicVolume = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .semantics {
                        contentDescription = label
                        stateDescription = percent
                    },
            )
            Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null)
        }
    }
}

/** La fenêtre du volume de la musique, ouverte depuis le menu de l'atelier. */
@Composable
fun MusicVolumeDialog(career: Career, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null) },
        title = { Text(stringResource(R.string.music_volume)) },
        text = {
            Column {
                Text(stringResource(R.string.music_volume_hint), style = MaterialTheme.typography.bodyMedium)
                MusicVolumeSlider(career, Modifier.padding(top = 12.dp), showTitle = false)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
