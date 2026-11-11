package app.atelier.maitres.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * L'ambiance d'une grande salle de musée, calculée sur place : l'air de la pièce (un souffle
 * très grave), des pas feutrés qui passent au loin, et parfois un murmure de visiteurs. Le
 * tout est très bas, pour accompagner la galerie et le musée sans jamais couvrir le guide.
 *
 * La boucle se raccorde sans clic : son début et sa fin se fondent l'un dans l'autre.
 */
object MuseumAmbience {
    const val SECONDS = 24
    private const val TARGET_RMS = 0.012f

    fun render(seed: Int = 7): ShortArray {
        val rate = EraMusic.RATE
        val n = rate * SECONDS
        val rnd = Random(seed)
        val dry = FloatArray(n)

        // L'air de la salle : un bruit brun filtré, à peine audible.
        val air = EraMusic.LowPass(220f)
        var brown = 0f
        for (i in 0 until n) {
            brown = (brown + (rnd.nextFloat() * 2 - 1) * 0.02f).coerceIn(-1f, 1f) * 0.998f
            dry[i] += air.next(brown) * 0.5f
        }

        // Des visiteurs qui traversent : quelques pas réguliers qui montent puis s'éloignent.
        var t = 1.5f
        while (t < SECONDS - 4f) {
            val steps = 5 + rnd.nextInt(5)
            val gap = 0.5f + rnd.nextFloat() * 0.15f
            val loudness = 0.7f + rnd.nextFloat() * 0.4f
            for (k in 0 until steps) {
                val swell = sin(PI * (k + 0.5) / steps).toFloat()
                step(dry, ((t + k * gap) * rate).roundToInt(), rate, loudness * swell, rnd)
            }
            t += steps * gap + 2.5f + rnd.nextFloat() * 4f
        }

        // Un murmure lointain : un bruit étroit autour des voix, qui enfle et retombe lentement.
        val voice = EraMusic.LowPass(700f)
        val dc = EraMusic.LowPass(180f)
        val start = (rnd.nextFloat() * SECONDS * 0.5f * rate).toInt()
        val length = (6f * rate).toInt()
        for (i in 0 until length) {
            val x = rnd.nextFloat() * 2 - 1
            val band = voice.next(x) - dc.next(x)
            val env = sin(PI * i / length).toFloat()
            val syllables = 0.6f + 0.4f * sin(2 * PI * 3.1 * i / rate).toFloat()
            dry[(start + i) % n] += band * env * syllables * 0.06f
        }

        return pcm(loopFade(EraMusic.reverb(dry, 0.9f), rate))
    }

    /** Un pas feutré : un choc grave et bref, comme un talon sur un parquet. */
    private fun step(out: FloatArray, at: Int, rate: Int, amp: Float, rnd: Random) {
        val len = (0.14f * rate).toInt()
        val f = 85f + rnd.nextFloat() * 30f
        val click = EraMusic.LowPass(1200f)
        for (i in 0 until len) {
            val p = at + i
            if (p !in out.indices) return
            val time = i.toFloat() / rate
            val body = sin(2 * PI * f * time).toFloat() * exp(-time * 38f)
            val tick = click.next(rnd.nextFloat() * 2 - 1) * exp(-time * 120f) * 0.35f
            out[p] += (body + tick) * amp
        }
    }

    /**
     * Fond la dernière seconde dans la première et la retire : en bouclant, la fin du tableau
     * enchaîne sur un début qui contient déjà sa suite, sans couture.
     */
    private fun loopFade(x: FloatArray, rate: Int): FloatArray {
        val f = rate
        val n = x.size - f
        val y = x.copyOf(n)
        for (i in 0 until f) {
            val a = i.toFloat() / f
            y[i] = x[i] * a + x[n + i] * (1 - a)
        }
        return y
    }

    private fun pcm(x: FloatArray): ShortArray {
        var sum = 0.0
        var peak = 1e-6f
        for (v in x) { sum += v * v; peak = max(peak, abs(v)) }
        val rms = sqrt(sum / x.size).toFloat().coerceAtLeast(1e-6f)
        val gain = minOf(TARGET_RMS / rms, 0.5f / peak)
        return ShortArray(x.size) { (x[it] * gain * Short.MAX_VALUE).roundToInt().coerceIn(-32767, 32767).toShort() }
    }
}
