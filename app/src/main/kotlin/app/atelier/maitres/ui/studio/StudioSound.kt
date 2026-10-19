package app.atelier.maitres.ui.studio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Les sons de l'atelier, tous calculés sur place.
 *
 * Chaque touche joue une note de bois tendre, façon marimba, dans une gamme pentatonique :
 * la mélodie avance d'un pas à chaque zone et monte pendant une série de coups justes.
 * Une couleur finie joue un petit arpège, une toile finie une fanfare. En fond, un piano
 * feutré égrène des accords lents. Une petite réverbération de pièce fait respirer le tout.
 */
class StudioSound(context: Context) {
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
            musicLoop = renderMusic()
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
        val lp = LowPass(min(2400f, f * 3f))
        for (i in 0 until n) {
            val t = i.toFloat() / RATE
            val s = sin(2 * PI * f * t).toFloat() * decay(t, 1f, 1.1f) +
                sin(2 * PI * f * 4 * t).toFloat() * decay(t, 0.06f, 0.08f)
            val env = if (t < 0.012f) ramp(0.0001f, 0.16f, t / 0.012f) else ramp(0.16f, 0.0001f, ((t - 0.012f) / 1.088f).coerceAtMost(1f))
            out[i] = lp.next(s) * env * OUT_GAIN
        }
        return out
    }

    /** Une note de piano feutré : triangle et deux sinus, un filtre qui s'ouvre avec la force. */
    private fun piano(out: FloatArray, midi: Int, start: Float, velocity: Float, random: Random) {
        val f = hz(midi)
        val detune = { 2.0.pow((random.nextFloat() - 0.5) * 6 / 1200).toFloat() }
        val partials = listOf(Triple(1f * detune(), 0.7f, true), Triple(2f * detune(), 0.25f, false), Triple(0.5f * detune(), 0.18f, false))
        val lp = LowPass(900f + velocity * 700f)
        val first = (start * RATE).roundToInt()
        val n = (4.3f * RATE).roundToInt()
        for (i in 0 until n) {
            val k = first + i
            if (k >= out.size) break
            val t = i.toFloat() / RATE
            var s = 0f
            for ((ratio, amp, triangle) in partials) {
                val phase = f * ratio * t
                s += amp * if (triangle) 4f * abs(phase - floor(phase + 0.5f)) - 1f else sin(2 * PI * phase).toFloat()
            }
            val env = when {
                t < 0.02f -> ramp(0.0001f, 0.2f * velocity, t / 0.02f)
                t < 0.9f -> ramp(0.2f * velocity, 0.06f * velocity, (t - 0.02f) / 0.88f)
                else -> ramp(0.06f * velocity, 0.0001f, ((t - 0.9f) / 3.3f).coerceAtMost(1f))
            }
            out[k] += lp.next(s) * env * MUSIC_GAIN * OUT_GAIN
        }
    }

    /** Huit mesures de quatre secondes, en boucle : un accord brisé, parfois une petite mélodie dessus. */
    private fun renderMusic(): ShortArray {
        val random = Random(1886)
        val bars = 8
        val length = bars * 4 * RATE
        val tail = 5 * RATE
        val out = FloatArray(length + tail)
        for (bar in 0 until bars) {
            val chord = CHORDS[bar % CHORDS.size]
            val t0 = bar * 4f + 0.1f
            piano(out, chord[0] - 12, t0, 0.55f, random)
            chord.drop(1).shuffled(random).forEachIndexed { i, m ->
                piano(out, m, t0 + 0.35f + i * (0.55f + random.nextFloat() * 0.25f), 0.35f + random.nextFloat() * 0.15f, random)
            }
            if (random.nextFloat() < 0.55f) {
                val top = 67 + PENTA[random.nextInt(5)]
                piano(out, top, t0 + 2.4f + random.nextFloat() * 0.6f, 0.3f, random)
                if (random.nextFloat() < 0.5f) piano(out, top + if (random.nextBoolean()) 2 else -3, t0 + 3.2f, 0.25f, random)
            }
        }
        val wet = reverbFloat(out, WET_MUSIC)
        // La fin de la boucle retombe sur son début : aucune couture à l'oreille.
        for (i in 0 until tail) wet[i] += wet[length + i]
        return toPcm(wet, length)
    }

    private fun reverb(dry: FloatArray, wet: Float): ShortArray {
        val padded = dry.copyOf(dry.size + (RATE * 1.5f).roundToInt())
        val out = reverbFloat(padded, wet)
        return toPcm(out, out.size)
    }

    /** Réverbération de Schroeder : quatre peignes amortis en parallèle, puis deux passe-tout. */
    private fun reverbFloat(input: FloatArray, wet: Float): FloatArray {
        val combs = intArrayOf(558, 594, 638, 678).map { Comb(it, 0.8f, 0.3f) }
        val allpasses = intArrayOf(278, 112).map { AllPass(it, 0.5f) }
        val out = FloatArray(input.size)
        for (i in input.indices) {
            var r = 0f
            for (c in combs) r += c.next(input[i])
            for (a in allpasses) r = a.next(r)
            out[i] = input[i] + r * wet * 0.25f
        }
        return out
    }

    private class LowPass(cutoff: Float) {
        // Deux pôles simples en série : une pente douce, sans résonance.
        private val a = exp(-2.0 * PI * cutoff / RATE).toFloat()
        private var y1 = 0f
        private var y2 = 0f
        fun next(x: Float): Float {
            y1 = (1 - a) * x + a * y1
            y2 = (1 - a) * y1 + a * y2
            return y2
        }
    }

    private class Comb(size: Int, private val feedback: Float, private val damp: Float) {
        private val buffer = FloatArray(size)
        private var index = 0
        private var store = 0f
        fun next(x: Float): Float {
            val y = buffer[index]
            store = y * (1 - damp) + store * damp
            buffer[index] = x + store * feedback
            index = (index + 1) % buffer.size
            return y
        }
    }

    private class AllPass(size: Int, private val gain: Float) {
        private val buffer = FloatArray(size)
        private var index = 0
        fun next(x: Float): Float {
            val b = buffer[index]
            val y = -x + b
            buffer[index] = x + b * gain
            index = (index + 1) % buffer.size
            return y
        }
    }

    private companion object {
        const val RATE = 22050
        const val OUT_GAIN = 1.8f
        const val MUSIC_GAIN = 0.5f
        const val WET_NOTES = 0.32f
        const val WET_MUSIC = 0.32f
        val PENTA = intArrayOf(0, 2, 4, 7, 9)
        val STEPS = intArrayOf(-1, 1, 1, 2, -2, 0, 1)
        val CHORDS = listOf(
            intArrayOf(48, 55, 59, 64, 67),
            intArrayOf(45, 52, 55, 60, 64),
            intArrayOf(41, 48, 52, 57, 60),
            intArrayOf(43, 50, 55, 59, 64),
        )

        /** Toutes les notes de bois dont le jeu a besoin. */
        val NOTES = (0..9).map { 55 + 12 * (it / 5) + PENTA[it % 5] }.toSet() + setOf(45, 60, 72)

        fun hz(midi: Int) = 440f * 2f.pow((midi - 69) / 12f)

        fun ramp(from: Float, to: Float, x: Float) = from * (to / from).pow(x.coerceIn(0f, 1f))

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
