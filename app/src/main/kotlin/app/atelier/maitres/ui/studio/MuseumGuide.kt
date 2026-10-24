package app.atelier.maitres.ui.studio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.atelier.maitres.data.Painting
import java.util.Locale

/**
 * Le guide de musée : à la fin d'une toile, une voix douce raconte l'histoire du tableau.
 *
 * La narration enregistrée (`assets/guide/<langue>/<toile>.mp3`) est jouée si elle existe ;
 * sinon, la voix de synthèse du téléphone lit le même texte, un peu plus lentement et plus
 * grave que d'ordinaire. [onPlaying] signale le début et la fin de la lecture.
 */
class MuseumGuide(private val context: Context, private val onPlaying: (Boolean) -> Unit) {
    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: Painting? = null

    private val language: String
        get() = if (context.resources.configuration.locales[0].language == "fr") "fr" else "en"

    /** Vrai si cette toile a quelque chose à raconter. */
    fun hasStory(painting: Painting): Boolean = !painting.guide.isNullOrBlank()

    fun play(painting: Painting) {
        stop()
        val path = "guide/$language/${painting.id}.mp3"
        val recorded = runCatching { context.assets.openFd(path) }.getOrNull()
        if (recorded != null) {
            val mp = MediaPlayer()
            runCatching {
                mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                recorded.use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                mp.setOnCompletionListener { onPlaying(false) }
                mp.prepare()
                mp.start()
                player = mp
                onPlaying(true)
            }.onFailure {
                mp.release()
                speak(painting)
            }
            return
        }
        speak(painting)
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        tts?.stop()
        pending = null
        onPlaying(false)
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
    }

    private fun speak(painting: Painting) {
        val text = painting.guide ?: return
        val engine = tts
        if (engine == null) {
            pending = painting
            tts = TextToSpeech(context.applicationContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                val waiting = pending
                pending = null
                if (ttsReady && waiting != null) speak(waiting)
            }.apply {
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = onPlaying(true)
                    override fun onDone(utteranceId: String?) = onPlaying(false)
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = onPlaying(false)
                })
            }
            return
        }
        if (!ttsReady) {
            pending = painting
            return
        }
        engine.language = if (language == "fr") Locale.FRANCE else Locale.UK
        engine.setSpeechRate(0.9f)
        engine.setPitch(0.92f)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "guide_${painting.id}")
    }
}
