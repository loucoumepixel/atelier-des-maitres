package app.atelier.maitres

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Chaque musique du dossier `musique` ne contient qu'une piste audio Vorbis. Une image de
 * pochette restée dans le fichier (piste vidéo) empêche le téléphone de jouer la musique.
 */
@RunWith(AndroidJUnit4::class)
class MusiquesTest {

    @Test
    fun chaqueMusiqueEstUnePisteAudioSeule() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val files = context.assets.list("musique").orEmpty().filter { it.endsWith(".ogg") }
        assertTrue("aucune musique trouvée", files.isNotEmpty())
        for (file in files) {
            val extractor = MediaExtractor()
            try {
                context.assets.openFd("musique/$file").use {
                    extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                assertEquals("$file : nombre de pistes", 1, extractor.trackCount)
                val mime = extractor.getTrackFormat(0).getString(MediaFormat.KEY_MIME).orEmpty()
                assertTrue("$file : piste $mime au lieu d'audio", mime.startsWith("audio/"))
            } finally {
                extractor.release()
            }
        }
    }
}
