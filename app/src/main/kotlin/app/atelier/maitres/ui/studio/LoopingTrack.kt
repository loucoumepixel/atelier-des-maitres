package app.atelier.maitres.ui.studio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import app.atelier.maitres.core.Era
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Une musique enregistrée (`assets/musique/…ogg`) jouée en boucle, avec un fondu d'entrée et
 * de sortie. Quand on l'arrête, elle se met en pause : elle reprend là où elle s'était tue.
 *
 * Les fichiers sont réglés au même volume (−23 LUFS) ; [level] est le volume voulu (le réglage
 * du joueur), qu'on peut changer pendant que la musique joue avec [setLevel].
 */
class LoopingTrack private constructor(private val player: MediaPlayer, private var level: Float) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var fade: Job? = null
    private var volume = 0f

    /** Lance la musique (fondu de deux secondes) ou l'arrête (fondu court, puis pause). */
    fun play(on: Boolean) {
        fade?.cancel()
        fade = scope.launch {
            if (on) runCatching { if (!player.isPlaying) player.start() }
            val from = volume
            val to = if (on) 1f else 0f
            val steps = if (on) 20 else 8
            for (k in 1..steps) {
                volume = from + (to - from) * k / steps
                runCatching { player.setVolume(volume * level, volume * level) }
                delay(100)
            }
            if (!on) runCatching { player.pause() }
        }
    }

    /** Change le volume tout de suite, même au milieu d'un fondu. */
    fun setLevel(value: Float) {
        level = value
        runCatching { player.setVolume(volume * level, volume * level) }
    }

    fun release() {
        scope.cancel()
        runCatching { player.release() }
    }

    companion object {
        private const val DIR = "musique"

        /** La musique du musée et de la galerie. */
        const val MUSEUM = "ambiance-musee.ogg"

        /**
         * Le fichier de musique de l'époque [era], s'il existe : `renaissance-1.ogg`,
         * `renaissance-2.ogg`… Quand une époque en a plusieurs, [variant] (stable pour une toile)
         * choisit lequel. Null si l'époque n'a pas encore de musique enregistrée.
         */
        fun eraFile(context: Context, era: Era, variant: Int): String? {
            val prefix = era.name.lowercase() + "-"
            val files = runCatching { context.assets.list(DIR)?.toList() }.getOrNull().orEmpty()
                .filter { it.startsWith(prefix) && it.endsWith(".ogg") }
                .sorted()
            if (files.isEmpty()) return null
            return files[Math.floorMod(variant, files.size)]
        }

        /** Prépare la musique [file] du dossier `musique`. À appeler hors du fil principal. */
        fun open(context: Context, file: String, level: Float): LoopingTrack? = runCatching {
            val player = MediaPlayer()
            try {
                context.assets.openFd("$DIR/$file").use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                player.isLooping = true
                player.setVolume(0f, 0f)
                player.prepare()
            } catch (e: Exception) {
                player.release()
                throw e
            }
            LoopingTrack(player, level)
        }.getOrNull()
    }
}
