package app.atelier.maitres.ui.studio

import android.content.Context
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
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * La musique du musée, jouée en boucle sous la galerie et le musée, avec un fondu d'entrée.
 * C'est la musique enregistrée `assets/musique/ambiance-musee.ogg` ; si elle manque, l'ambiance
 * de salle composée sur place ([MuseumAmbience]) la remplace. Elle se tait dès qu'on entre
 * dans l'atelier, qui a la musique de son époque.
 */
class AmbiencePlayer(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var loop: ShortArray? = null
    private var job: Job? = null
    private var recorded: LoopingTrack? = null
    private var lookedForTrack = false
    private var wanted = false
    private var released = false
    @Volatile private var gain = 1f
    @Volatile private var synth: AudioTrack? = null

    /** Le volume de la musique (réglage du joueur, de 0 à 1), appliqué tout de suite. */
    fun setVolume(volume: Float) {
        gain = volume * LEVEL
        recorded?.setLevel(gain)
        synth?.let { runCatching { it.setVolume(gain) } }
    }

    /** Joue l'ambiance si [on], sinon l'arrête. Appeler autant de fois qu'on veut, du fil principal. */
    fun play(on: Boolean) {
        if (released) return
        wanted = on
        if (!lookedForTrack) {
            if (!on) return
            lookedForTrack = true
            scope.launch(Dispatchers.IO) {
                val opened = LoopingTrack.open(context, LoopingTrack.MUSEUM, gain)
                withContext(Dispatchers.Main) {
                    if (released) opened?.release() else recorded = opened
                    play(wanted)
                }
            }
            return
        }
        recorded?.let {
            it.play(on)
            return
        }
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
                synth = track
                val fade = launch { for (k in 1..30) { delay(100); track.setVolume(k / 30f * gain) } }
                var pos = 0
                val chunk = rate / 10
                while (isActive) {
                    val n = min(chunk, pcm.size - pos)
                    track.write(pcm, pos, n)
                    pos = (pos + n) % pcm.size
                }
                fade.cancel()
            } finally {
                synth = null
                runCatching {
                    track.pause()
                    track.flush()
                    track.release()
                }
            }
        }
    }

    /** Arrête tout pour de bon (fin de l'activité). */
    fun release() {
        released = true
        job?.cancel()
        job = null
        recorded?.release()
        recorded = null
    }

    private companion object {
        /** La musique du musée, un peu plus discrète que celle de l'atelier au même réglage. */
        const val LEVEL = 0.8f
    }
}
