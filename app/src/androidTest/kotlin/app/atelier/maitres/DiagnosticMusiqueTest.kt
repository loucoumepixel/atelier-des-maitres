package app.atelier.maitres

import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/** DIAGNOSTIC TEMPORAIRE : à retirer avant fusion. Échoue toujours pour afficher son rapport. */
@RunWith(AndroidJUnit4::class)
class DiagnosticMusiqueTest {

    @Test
    fun rapport() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val app = inst.targetContext
        val test = inst.context
        val out = StringBuilder("RAPPORT MUSIQUE\n")
        val jobs = app.assets.list("musique").orEmpty().map { "app:musique/$it" } +
            test.assets.list("temoins").orEmpty().map { "test:temoins/$it" }
        for (name in jobs) {
            val (src, path) = name.split(":", limit = 2)
            val assets = if (src == "app") app.assets else test.assets
            out.append(name).append(" | ")
            out.append(runCatching { assets.openFd(path).use { extract(it) } }.getOrElse { "extract ERREUR $it" }).append(" | ")
            out.append(runCatching { assets.openFd(path).use { decode(it) } }.getOrElse { "decode ERREUR $it" }).append(" | ")
            out.append(runCatching { play(assets.openFd(path)) }.getOrElse { "player ERREUR $it" }).append('\n')
        }
        fail(out.toString())
    }

    private fun extract(fd: AssetFileDescriptor): String {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            return (0 until ex.trackCount).joinToString(prefix = "pistes=", separator = ",") {
                ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty()
            }
        } finally { ex.release() }
    }

    /** Décode 8 secondes avec le décodeur du téléphone et mesure le niveau (RMS). */
    private fun decode(fd: AssetFileDescriptor): String {
        val ex = MediaExtractor()
        ex.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        val t = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") }
        ex.selectTrack(t)
        val fmt = ex.getTrackFormat(t)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var sum = 0.0; var n = 0L; var inputDone = false; var lastUs = 0L
        val rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); val ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        while (n < 8L * rate * ch) {
            if (!inputDone) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val size = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                    if (size < 0) { codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                    else { codec.queueInputBuffer(i, 0, size, ex.sampleTime, 0); ex.advance() }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            if (o >= 0) {
                val b = codec.getOutputBuffer(o)!!.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                while (b.hasRemaining()) { val s = b.get() / 32768.0; sum += s * s; n++ }
                lastUs = info.presentationTimeUs
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
        codec.stop(); codec.release(); ex.release()
        return "decode echantillons=$n rms=${"%.4f".format(sqrt(sum / maxOf(1, n)))} t=${lastUs / 1000}ms"
    }

    /** Joue avec MediaPlayer comme le jeu, 3 secondes, et regarde si la lecture avance. */
    private fun play(fd: AssetFileDescriptor): String {
        val thread = HandlerThread("lecteur").apply { start() }
        val done = CountDownLatch(1)
        var result = "?"
        Handler(thread.looper).post {
            val p = MediaPlayer()
            val errors = StringBuilder()
            p.setOnErrorListener { _, w, e -> errors.append("onError($w,$e) "); true }
            try {
                fd.use { p.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                p.isLooping = true
                p.prepare()
                val dur = p.duration
                p.start()
                Handler(thread.looper).postDelayed({
                    result = "player duree=${dur}ms position=${p.currentPosition}ms joue=${p.isPlaying} $errors"
                    p.release(); done.countDown()
                }, 3000)
            } catch (e: Exception) {
                result = "player EXCEPTION $e $errors"; p.release(); done.countDown()
            }
        }
        done.await(15, TimeUnit.SECONDS)
        thread.quitSafely()
        return result
    }
}
