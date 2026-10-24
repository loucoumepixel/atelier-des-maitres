package app.atelier.maitres.ui.studio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import app.atelier.maitres.core.Era
import app.atelier.maitres.core.EraMusic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Les sons de l'atelier, tous calculés sur place.
 *
 * Chaque touche joue une note de bois tendre, façon marimba, dans une gamme pentatonique :
 * la mélodie avance d'un pas à chaque zone et monte pendant une série de coups justes.
 * Une couleur finie joue un petit arpège, une toile finie une fanfare. En fond, la musique
 * de l'époque du tableau ([EraMusic]) : valse au piano pour Van Gogh, arpèges à la Debussy
 * pour Monet, koto pour Hokusai… Une petite réverbération de pièce fait respirer le tout.
 */
class StudioSound(context: Context, private val era: Era = Era.POSTIMPRESSIONNISME) {
    private val dir = File(context.cacheDir, "sons")
    private val pool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(attributes(AudioAttributes.CONTENT_TYPE_SONIFICATION))
        .build()
    private val notes = ConcurrentHashMap<Int, Int>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var musicLoop: ShortArray? = null
    private var musicJob: Job? = null
    private var wantMusic = false
    private var step = 7

    /** Calcule les notes et la musique ; à appeler une fois, hors du fil principal. */
    suspend fun prepare() = withContext(Dispatchers.Default) {
        runCatching {
            dir.mkdirs()
            for (midi in NOTES) {
                val file = File(dir, "bois_v1_$midi.wav")
                if (!file.exists()) writeWav(file, reverb(bell(midi), WET_NOTES))
                notes[midi] = pool.load(file.path, 1)
            }
            musicLoop = EraMusic.render(era)
        }
        withContext(Dispatchers.Main) { if (wantMusic) startMusic() }
    }

    /** Une zone peinte : une note juste qui monte avec la série, ou une note sourde si la couleur est fausse. */
    fun stroke(faithful: Boolean, combo: Int) {
        if (!faithful) {
            play(45, 0.4f)
            return
        }
        val lift = min(4, combo / 10)
        step = (step + STEPS.random()).coerceIn(0, 9)
        val i = min(9, step + lift)
        play(55 + 12 * (i / 5) + PENTA[i % 5], 0.8f + Random.nextFloat() * 0.2f)
    }

    /** Une couleur terminée : un petit arpège qui monte. */
    fun colourDone() = arpeggio(listOf(60, 64, 67, 72), 100)

    /** La toile finie. */
    fun fanfare() = arpeggio(listOf(55, 60, 64, 67, 72, 76), 130)

    /** Le son revient : deux notes pour le dire. */
    fun hello() = arpeggio(listOf(60, 67), 100)

    /** Lance ou arrête la musique de fond, avec un fondu. */
    fun music(on: Boolean) {
        wantMusic = on
        if (on) startMusic() else stopMusic()
    }

    fun release() {
        wantMusic = false
        scope.cancel()
        runCatching { pool.release() }
    }

    private fun arpeggio(midi: List<Int>, gapMs: Long) {
        scope.launch { midi.forEach { play(it, 0.9f); delay(gapMs) } }
    }

    private fun play(midi: Int, volume: Float) {
        val id = notes[midi] ?: return
        runCatching { pool.play(id, volume, volume, 1, 0, 1f) }
    }

    private fun startMusic() {
        val loop = musicLoop ?: return
        if (musicJob?.isActive == true) return
        musicJob = scope.launch(Dispatchers.IO) {
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(attributes(AudioAttributes.CONTENT_TYPE_MUSIC))
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(RATE) // une demi-seconde
                    .build()
            }.getOrNull() ?: return@launch
            try {
                track.setVolume(0f)
                track.play()
                // Fondu d'entrée sur deux secondes, pendant que la musique s'écrit.
                val fade = launch { for (k in 1..20) { delay(100); track.setVolume(k / 20f) } }
                var pos = 0
                val chunk = RATE / 10
                while (isActive) {
                    val n = min(chunk, loop.size - pos)
                    track.write(loop, pos, n)
                    pos = (pos + n) % loop.size
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

    private fun stopMusic() {
        val job = musicJob ?: return
        musicJob = null
        job.cancel()
    }

    // ---------------------------------------------------------------- synthèse

    /** Note de bois : deux sinus (fondamentale et un 4ᵉ partiel très bref), filtrés doucement. */
    private fun bell(midi: Int): FloatArray {
        val f = hz(midi)
        val n = (RATE * 1.2f).roundToInt()
        val out = FloatArray(n)
        val lp = EraMusic.LowPass(min(2400f, f * 3f))
        for (i in 0 until n) {
            val t = i.toFloat() / RATE
            val s = sin(2 * PI * f * t).toFloat() * decay(t, 1f, 1.1f) +
                sin(2 * PI * f * 4 * t).toFloat() * decay(t, 0.06f, 0.08f)
            val env = if (t < 0.012f) ramp(0.0001f, 0.16f, t / 0.012f) else ramp(0.16f, 0.0001f, ((t - 0.012f) / 1.088f).coerceAtMost(1f))
            out[i] = lp.next(s) * env * OUT_GAIN
        }
        return out
    }

    private fun reverb(dry: FloatArray, wet: Float): ShortArray {
        val padded = dry.copyOf(dry.size + (RATE * 1.5f).roundToInt())
        val out = EraMusic.reverb(padded, wet)
        return toPcm(out, out.size)
    }

    private companion object {
        const val RATE = EraMusic.RATE
        const val OUT_GAIN = EraMusic.OUT_GAIN
        const val WET_NOTES = 0.32f
        val PENTA = EraMusic.PENTA
        val STEPS = intArrayOf(-1, 1, 1, 2, -2, 0, 1)

        /** Toutes les notes de bois dont le jeu a besoin. */
        val NOTES = (0..9).map { 55 + 12 * (it / 5) + PENTA[it % 5] }.toSet() + setOf(45, 60, 72)

        fun hz(midi: Int) = EraMusic.hz(midi)

        fun ramp(from: Float, to: Float, x: Float) = EraMusic.ramp(from, to, x)

        fun decay(t: Float, amp: Float, length: Float) = if (t >= length) 0f else ramp(amp, 0.0001f * amp, t / length)

        fun attributes(content: Int): AudioAttributes =
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(content).build()

        fun toPcm(samples: FloatArray, length: Int) =
            ShortArray(length) { (samples[it] * Short.MAX_VALUE).roundToInt().coerceIn(-32767, 32767).toShort() }

        fun writeWav(file: File, pcm: ShortArray) {
            val data = pcm.size * 2
            val buffer = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
            buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
            buffer.put("data".toByteArray()).putInt(data)
            pcm.forEach { buffer.putShort(it) }
            val tmp = File(file.path + ".tmp")
            FileOutputStream(tmp).use { it.write(buffer.array()) }
            tmp.renameTo(file)
        }
    }
}
