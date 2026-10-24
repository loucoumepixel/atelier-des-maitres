package app.atelier.maitres.core

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
 * L'époque d'une toile, qui choisit la musique de fond de l'atelier. Aucune musique n'est
 * enregistrée : tout est composé et joué par [EraMusic], sans droits à payer à personne.
 */
enum class Era {
    /** Monet, Renoir, Seurat : arpèges flottants à la Debussy, accords de neuvième. */
    IMPRESSIONNISME,

    /** Van Gogh, Cézanne : valse lente au piano feutré, à la Satie. */
    POSTIMPRESSIONNISME,

    /** Hokusai : koto pincé sur une gamme japonaise, beaucoup de silence. */
    JAPON,

    /** Botticelli, Léonard, Vermeer : luth et mode ancien. */
    RENAISSANCE,

    /** Friedrich, Delacroix, Turner : violoncelle et piano en mineur. */
    ROMANTISME,

    /** Munch : bourdon grave et notes suspendues. */
    EXPRESSIONNISME,

    /** Grant Wood : guitare picking et cantique de campagne. */
    AMERIQUE,
}

/**
 * Compose la boucle musicale d'une [Era] : une trentaine de secondes qui se reboucle sans
 * couture. Tout est synthétisé (piano feutré, cordes pincées, violoncelle), puis passe dans
 * une petite réverbération de pièce. Les timbres restent graves et doux.
 */
object EraMusic {
    const val RATE = 22050
    const val OUT_GAIN = 1.8f
    private const val MUSIC_GAIN = 0.5f
    private const val WET = 0.32f

    /** Volume moyen de chaque boucle : celui de la valse au piano, déjà réglé avec Louc. */
    private const val TARGET_RMS = 0.028f

    /** La boucle de l'époque [era], en PCM 16 bits mono à [RATE] Hz. */
    fun render(era: Era): ShortArray = when (era) {
        Era.POSTIMPRESSIONNISME -> loop(8, 4.2f, 1886) { out, bar, t0, r -> waltz(out, bar, t0, r) }
        Era.IMPRESSIONNISME -> loop(8, 4f, 1872) { out, bar, t0, r -> debussy(out, bar, t0, r) }
        Era.JAPON -> loop(8, 4f, 1831) { out, bar, t0, r -> koto(out, bar, t0, r) }
        Era.RENAISSANCE -> loop(8, 4f, 1485) { out, bar, t0, r -> lute(out, bar, t0, r) }
        Era.ROMANTISME -> loop(6, 5f, 1818) { out, bar, t0, r -> romantic(out, bar, t0, r) }
        Era.EXPRESSIONNISME -> loop(6, 5f, 1893) { out, bar, t0, r -> expressionist(out, bar, t0, r) }
        Era.AMERIQUE -> loop(8, 4f, 1930) { out, bar, t0, r -> folk(out, bar, t0, r) }
    }

    /** [bars] mesures de [barSeconds] secondes, écrites par [write], bouclées sans couture. */
    private fun loop(bars: Int, barSeconds: Float, seed: Int, write: (FloatArray, Int, Float, Random) -> Unit): ShortArray {
        val random = Random(seed)
        val length = (bars * barSeconds * RATE).roundToInt()
        val tail = 6 * RATE
        val out = FloatArray(length + tail)
        for (bar in 0 until bars) write(out, bar, bar * barSeconds + 0.1f, random)
        val wet = reverb(out, WET)
        // La fin de la boucle retombe sur son début : aucune couture à l'oreille.
        for (i in 0 until tail) wet[i] += wet[length + i]
        // Toutes les époques au même volume moyen, sans jamais saturer.
        var energy = 0.0
        var peak = 1e-6f
        for (i in 0 until length) { energy += wet[i] * wet[i]; peak = maxOf(peak, abs(wet[i])) }
        val rms = kotlin.math.sqrt(energy / length).toFloat() + 1e-6f
        val gain = min(TARGET_RMS / rms, 0.9f / peak)
        return ShortArray(length) { (wet[it] * gain * Short.MAX_VALUE).roundToInt().coerceIn(-32767, 32767).toShort() }
    }

    // ---------------------------------------------------------------- les époques

    /** La valse lente d'origine : basse, puis accord brisé, parfois une petite mélodie. */
    private fun waltz(out: FloatArray, bar: Int, t0: Float, random: Random) {
        val chord = SATIE[bar % SATIE.size]
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

    /** Arpèges qui montent et redescendent comme l'eau, sur des accords de neuvième. */
    private fun debussy(out: FloatArray, bar: Int, t0: Float, random: Random) {
        val chord = NINTHS[bar % NINTHS.size]
        piano(out, chord[0] - 12, t0, 0.45f, random, length = 6f)
        val up = chord + chord.drop(1).take(2).map { it + 12 }
        val line = up + up.reversed().drop(1).take(3)
        line.forEachIndexed { i, m ->
            piano(out, m, t0 + 0.2f + i * 0.42f + random.nextFloat() * 0.04f, 0.22f + 0.1f * sin(i / 2f), random, bright = 0.7f)
        }
    }

    /** Un koto qui pince quelques notes de la gamme miyako-bushi, et laisse résonner. */
    private fun koto(out: FloatArray, bar: Int, t0: Float, random: Random) {
        val scale = intArrayOf(50, 51, 55, 57, 58, 62, 63, 67, 69, 70)
        pluck(out, if (bar % 2 == 0) 38 else 43, t0, 0.5f, random, damping = 0.997f)
        var t = t0 + 0.5f
        var i = 4 + random.nextInt(3)
        repeat(3 + random.nextInt(3)) {
            pluck(out, scale[i], t, 0.4f + random.nextFloat() * 0.2f, random, damping = 0.996f)
            // Le tremblé de la corde, parfois, juste après la note.
            if (random.nextFloat() < 0.3f) pluck(out, scale[i], t + 0.12f, 0.2f, random, damping = 0.995f)
            t += listOf(0.45f, 0.6f, 0.9f).random(random)
            i = (i + listOf(-2, -1, 1, 1, 2).random(random)).coerceIn(0, scale.size - 1)
        }
    }

    /** Un luth qui égrène des arpèges en mode dorien, basse sur le premier temps. */
    private fun lute(out: FloatArray, bar: Int, t0: Float, random: Random) {
        val chord = DORIAN[bar % DORIAN.size]
        pluck(out, chord[0] - 12, t0, 0.55f, random, damping = 0.994f, bright = 0.45f)
        val pattern = intArrayOf(1, 2, 3, 2, 1, 2, 3, 4)
        pattern.forEachIndexed { i, k ->
            pluck(out, chord[k.coerceAtMost(chord.size - 1)], t0 + 0.25f + i * 0.47f, 0.32f + random.nextFloat() * 0.08f, random, damping = 0.993f, bright = 0.5f)
        }
    }

    /** Un violoncelle qui chante une phrase en mineur sur des accords de piano tenus. */
    private fun romantic(out: FloatArray, bar: Int, t0: Float, random: Random) {
        val chord = MINOR[bar % MINOR.size]
        piano(out, chord[0] - 12, t0, 0.45f, random, length = 6f)
        chord.drop(1).forEachIndexed { i, m -> piano(out, m, t0 + 0.1f + i * 0.06f, 0.25f, random, length = 6f) }
        val melody = MINOR_LINE[bar % MINOR_LINE.size]
        cello(out, melody[0], t0 + 0.3f, 2.6f)
        cello(out, melody[1], t0 + 2.9f, 2.0f)
    }

    /** Un bourdon grave, des notes de piano suspendues et dissonantes, à peine posées. */
    private fun expressionist(out: FloatArray, bar: Int, t0: Float, random: Random) {
        cello(out, if (bar % 2 == 0) 38 else 37, t0, 5f, volume = 0.55f, vibrato = 0.002f)
        val notes = intArrayOf(62, 63, 66, 69, 70, 74)
        repeat(2) { k ->
            piano(out, notes[random.nextInt(notes.size)], t0 + 0.8f + k * (1.6f + random.nextFloat()), 0.22f, random, length = 5f, bright = 0.4f)
        }
    }

    /** Une guitare en picking sur un cantique en majeur. */
    private fun folk(out: FloatArray, bar: Int, t0: Float, random: Random) {
        val chord = HYMN[bar % HYMN.size]
        val pattern = intArrayOf(0, 2, 1, 3, 0, 2, 1, 3)
        pattern.forEachIndexed { i, k ->
            val m = if (k == 0) chord[0] - 12 else chord[k]
            pluck(out, m, t0 + i * 0.5f, if (k == 0) 0.5f else 0.32f, random, damping = 0.995f, bright = 0.55f)
        }
    }

    // ---------------------------------------------------------------- les instruments

    /** Une note de piano feutré : triangle et deux sinus, un filtre qui s'ouvre avec la force. */
    fun piano(out: FloatArray, midi: Int, start: Float, velocity: Float, random: Random, length: Float = 4.3f, bright: Float = 1f) {
        val f = hz(midi)
        val detune = { 2.0.pow((random.nextFloat() - 0.5) * 6 / 1200).toFloat() }
        val partials = listOf(Triple(1f * detune(), 0.7f, true), Triple(2f * detune(), 0.25f, false), Triple(0.5f * detune(), 0.18f, false))
        val lp = LowPass((900f + velocity * 700f) * bright)
        val first = (start * RATE).roundToInt()
        val n = (length * RATE).roundToInt()
        val release = length - 0.9f
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
                else -> ramp(0.06f * velocity, 0.0001f, ((t - 0.9f) / release).coerceAtMost(1f))
            }
            out[k] += lp.next(s) * env * MUSIC_GAIN * OUT_GAIN
        }
    }

    /**
     * Une corde pincée (Karplus-Strong) : koto, luth ou guitare selon l'amortissement
     * [damping] et la brillance [bright] de l'attaque.
     */
    fun pluck(out: FloatArray, midi: Int, start: Float, velocity: Float, random: Random, damping: Float = 0.995f, bright: Float = 0.6f) {
        val period = (RATE / hz(midi)).roundToInt().coerceAtLeast(2)
        val buffer = FloatArray(period) { random.nextFloat() * 2f - 1f }
        // Une attaque adoucie : le bruit de départ est filtré, comme un doigt et non un médiator.
        val soft = LowPass(300f + 1600f * bright)
        for (i in buffer.indices) buffer[i] = soft.next(buffer[i])
        val first = (start * RATE).roundToInt()
        val n = 4 * RATE
        val body = LowPass(1300f)
        var index = 0
        for (i in 0 until n) {
            val k = first + i
            if (k >= out.size) break
            val next = (index + 1) % period
            val y = buffer[index]
            buffer[index] = damping * 0.5f * (y + buffer[next])
            index = next
            val fadeIn = min(1f, i / (0.004f * RATE))
            out[k] += body.next(y) * velocity * fadeIn * 0.55f * MUSIC_GAIN * OUT_GAIN
        }
    }

    /** Un violoncelle doux : dent de scie filtrée, archet lent, léger vibrato. */
    fun cello(out: FloatArray, midi: Int, start: Float, length: Float, volume: Float = 0.4f, vibrato: Float = 0.004f) {
        val f = hz(midi)
        val lp = LowPass(min(1400f, f * 4f))
        val first = (start * RATE).roundToInt()
        val n = ((length + 0.8f) * RATE).roundToInt()
        var phase = 0.0
        for (i in 0 until n) {
            val k = first + i
            if (k >= out.size) break
            val t = i.toFloat() / RATE
            val wobble = 1f + vibrato * sin(2 * PI * 5.2 * t).toFloat() * min(1f, t / 0.6f)
            phase += f * wobble / RATE
            val saw = (2.0 * (phase - floor(phase + 0.5))).toFloat()
            val env = when {
                t < 0.35f -> t / 0.35f
                t < length -> 1f
                else -> (1f - (t - length) / 0.8f).coerceAtLeast(0f)
            }
            out[k] += lp.next(saw) * env * volume * 0.12f * MUSIC_GAIN * OUT_GAIN
        }
    }

    // ---------------------------------------------------------------- traitement

    /** Réverbération de Schroeder : quatre peignes amortis en parallèle, puis deux passe-tout. */
    fun reverb(input: FloatArray, wet: Float): FloatArray {
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

    class LowPass(cutoff: Float) {
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

    val PENTA = intArrayOf(0, 2, 4, 7, 9)

    private val SATIE = listOf(
        intArrayOf(48, 55, 59, 64, 67),
        intArrayOf(45, 52, 55, 60, 64),
        intArrayOf(41, 48, 52, 57, 60),
        intArrayOf(43, 50, 55, 59, 64),
    )
    private val NINTHS = listOf(
        intArrayOf(41, 52, 55, 57, 60, 64), // Fa maj9
        intArrayOf(46, 53, 57, 60, 62, 65), // Si♭ maj9
        intArrayOf(45, 52, 55, 59, 60, 64), // La m9
        intArrayOf(43, 50, 53, 57, 59, 62), // Sol 9
    )
    private val DORIAN = listOf(
        intArrayOf(50, 57, 62, 65, 69), // Ré m
        intArrayOf(48, 55, 60, 64, 67), // Do
        intArrayOf(46, 53, 58, 62, 65), // Si♭
        intArrayOf(45, 52, 57, 61, 64), // La
    )
    private val MINOR = listOf(
        intArrayOf(45, 52, 57, 60), // La m
        intArrayOf(41, 48, 53, 57), // Fa
        intArrayOf(43, 50, 55, 59), // Sol
        intArrayOf(40, 47, 52, 56), // Mi
    )
    private val MINOR_LINE = listOf(intArrayOf(57, 55), intArrayOf(53, 52), intArrayOf(55, 59), intArrayOf(56, 52))
    private val HYMN = listOf(
        intArrayOf(43, 55, 59, 62), // Sol
        intArrayOf(48, 55, 60, 64), // Do
        intArrayOf(43, 55, 59, 62), // Sol
        intArrayOf(50, 57, 62, 66), // Ré
    )

    fun hz(midi: Int) = 440f * 2f.pow((midi - 69) / 12f)

    fun ramp(from: Float, to: Float, x: Float) = from * (to / from).pow(x.coerceIn(0f, 1f))
}
