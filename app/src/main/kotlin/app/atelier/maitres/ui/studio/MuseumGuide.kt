package app.atelier.maitres.ui.studio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.atelier.maitres.R
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

    /**
     * La langue des textes réellement affichés (fr, en, es, de, it, pt) : chaque fichier de traduction
     * la donne lui-même, donc elle suit toujours le choix d'Android, même avec plusieurs langues réglées.
     */
    private val language: String
        get() = context.getString(R.string.guide_language)

    /** La langue du joueur qui correspond aux textes (pour garder son accent : es-MX, pt-PT…), s'il en a une. */
    private val playerLocale: Locale?
        get() {
            val locales = context.resources.configuration.locales
            return (0 until locales.size()).map { locales[it] }.firstOrNull { it.language == language }
        }

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
        // La voix doit parler la langue du texte : on essaie l'accent du joueur (es-MX, pt-PT…),
        // puis celui par défaut de la langue, puis la langue seule.
        // Si aucune n'est installée, on demande quand même la langue : le téléphone peut la télécharger.
        val voices = guideVoiceLocales(language, playerLocale)
        engine.language = voices.firstOrNull { engine.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE } ?: voices.first()
        engine.setSpeechRate(0.9f)
        engine.setPitch(0.92f)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "guide_${painting.id}")
    }
}

/**
 * Les voix à essayer, dans l'ordre, pour lire le texte du guide avec la synthèse du téléphone.
 * Le français et l'anglais gardent leur voix habituelle (France, Royaume-Uni).
 */
fun guideVoiceLocales(language: String, player: Locale?): List<Locale> {
    val default = when (language) {
        "fr" -> Locale.FRANCE
        "es" -> Locale.forLanguageTag("es-ES")
        "de" -> Locale.GERMANY
        "it" -> Locale.ITALY
        "pt" -> Locale.forLanguageTag("pt-BR")
        else -> Locale.UK
    }
    val accent = player?.takeIf { it.language == default.language && it.country.isNotEmpty() && default.language !in setOf("fr", "en") }
    return listOfNotNull(
        accent,
        default,
        Locale.forLanguageTag(default.language),
    ).distinct()
}
