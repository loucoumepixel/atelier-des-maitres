package app.atelier.maitres.ui.studio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import app.atelier.maitres.core.EraMusic
import app.atelier.maitres.core.MuseumAmbience
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * L'ambiance de salle de musée ([MuseumAmbience]) jouée en boucle sous la galerie et le
 * musée, avec un fondu d'entrée. Elle se calcule à la première demande (moins d'une seconde,
 * hors du fil principal) et se tait dès qu'on entre dans l'atelier, qui a sa propre musique.
 */
class AmbiencePlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var loop: ShortArray? = null
    private var job: Job? = null

    /** Joue l'ambiance si [on], sinon l'arrête. Appeler autant de fois qu'on veut. */
    fun play(on: Boolean) {
        if (!on) {
            job?.cancel()
            job = null
            return
        }
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            val pcm = loop ?: MuseumAmbience.render().also { loop = it }
            val rate = EraMusic.RATE
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(rate)
                    .build()
            }.getOrNull() ?: return@launch
            try {
                track.setVolume(0f)
                track.play()
                val fade = launch { for (k in 1..30) { delay(100); track.setVolume(k / 30f) } }
                var pos = 0
                val chunk = rate / 10
                while (isActive) {
                    val n = min(chunk, pcm.size - pos)
                    track.write(pcm, pos, n)
                    pos = (pos + n) % pcm.size
                }
                fade.cancel()
            } finally {
                runCatching {
                    track.pause()
                    track.flush()
                    track.release()
                }
            }
        }
    }
}
