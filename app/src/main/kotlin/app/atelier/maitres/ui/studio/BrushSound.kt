package app.atelier.maitres.ui.studio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Le petit « frrt » du pinceau : un bruit rose filtré dont la bande monte vers les aigus,
 * comme des poils qui frottent la toile. Quelques variantes sont calculées à l'avance et jouées
 * au hasard, pour que deux touches ne sonnent jamais tout à fait pareil.
 */
class BrushSound {
    private val tracks: List<AudioTrack> = List(VARIANTS) { i -> track(render(Random(i * 7919 + 13))) }
    private var next = 0

    fun play() {
        val track = tracks[(next++ + Random.nextInt(VARIANTS)) % VARIANTS]
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    fun release() = tracks.forEach { runCatching { it.release() } }

    private fun track(samples: ShortArray): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
            .also { it.write(samples, 0, samples.size) }

    private fun render(random: Random): ShortArray {
        val duration = 0.13f + random.nextFloat() * 0.09f
        val n = (RATE * duration).roundToInt()
        val f0 = 1400f + random.nextFloat() * 600f
        val f1 = f0 * (2.2f + random.nextFloat() * 1.0f)
        val out = ShortArray(n)
        // Bruit rose (filtre de Paul Kellet, version économique).
        var b0 = 0f; var b1 = 0f; var b2 = 0f
        // Passe-bande à variables d'état, puis passe-haut à 500 Hz.
        var low = 0f; var band = 0f
        val hpA = exp(-2.0 * PI * 500 / RATE).toFloat()
        var hpPrevIn = 0f; var hpPrevOut = 0f
        val q = 1.4f
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val white = random.nextFloat() * 2f - 1f
            b0 = 0.99765f * b0 + white * 0.0990460f
            b1 = 0.96300f * b1 + white * 0.2965164f
            b2 = 0.57000f * b2 + white * 1.0526913f
            val pink = (b0 + b1 + b2 + white * 0.1848f) * 0.2f

            val cutoff = f0 * exp(ln(f1 / f0) * t)
            val f = (2f * sin(PI * cutoff / RATE)).toFloat()
            val high = pink - low - band / q
            band += f * high
            low += f * band

            val hp = hpA * (hpPrevOut + band - hpPrevIn)
            hpPrevIn = band
            hpPrevOut = hp

            // Attaque brève, petit palier, puis extinction exponentielle.
            val envelope = when {
                t < 0.04f -> 0.32f * (t / 0.04f)
                t < 0.35f -> 0.32f - (0.32f - 0.26f) * ((t - 0.04f) / 0.31f)
                else -> 0.26f * exp(ln(0.0001f / 0.26f) * ((t - 0.35f) / 0.65f))
            }
            out[i] = (hp * envelope * 2.4f * Short.MAX_VALUE).roundToInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private companion object {
        const val RATE = 44100
        const val VARIANTS = 5
    }
}
