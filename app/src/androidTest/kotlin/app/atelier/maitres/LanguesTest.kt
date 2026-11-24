package app.atelier.maitres

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.atelier.maitres.ui.studio.guideVoiceLocales
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Le jeu parle la langue du téléphone : chaque traduction est bien choisie par Android, n'a
 * oublié aucun texte, et le guide demande à la voix du téléphone la langue du texte qu'il lit.
 */
@RunWith(AndroidJUnit4::class)
class LanguesTest {

    private val base = InstrumentationRegistry.getInstrumentation().targetContext

    private fun contextIn(tag: String): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(config)
    }

    @Test
    fun chaqueLangueEstChoisieParAndroid() {
        val attendu = mapOf(
            "fr-FR" to "fr", "en-US" to "en", "es-ES" to "es", "es-MX" to "es", "de-DE" to "de",
            "de-AT" to "de", "it-IT" to "it", "pt-BR" to "pt", "pt-PT" to "pt", "nl-NL" to "en",
        )
        for ((tag, langue) in attendu) {
            assertEquals(tag, langue, contextIn(tag).getString(R.string.guide_language))
        }
    }

    @Test
    fun chaqueTraductionEstComplete() {
        val anglais = contextIn("en-US").resources
        val strings = R.string::class.java.fields.map { it.getInt(null) }
        val tableaux = R.array::class.java.fields.map { it.getInt(null) }
        for (tag in listOf("fr-FR", "es-ES", "de-DE", "it-IT", "pt-BR")) {
            val res = contextIn(tag).resources
            // Les récits du guide sont traduits (pas restés en anglais).
            assertNotEquals(tag, anglais.getString(R.string.guide_nuit_etoilee), res.getString(R.string.guide_nuit_etoilee))
            for (id in strings) assertTrue("$tag : ${res.getResourceEntryName(id)} vide", res.getString(id).isNotBlank())
            for (id in tableaux) {
                assertEquals("$tag : ${res.getResourceEntryName(id)}", anglais.getStringArray(id).size, res.getStringArray(id).size)
            }
        }
    }

    @Test
    fun laVoixDuGuideParleLaLangueDuTexte() {
        assertEquals(Locale.forLanguageTag("es-MX"), guideVoiceLocales("es", Locale.forLanguageTag("es-MX")).first())
        assertEquals(Locale.GERMANY, guideVoiceLocales("de", Locale.GERMAN).first())
        assertEquals(Locale.forLanguageTag("pt-BR"), guideVoiceLocales("pt", null).first())
        assertEquals(Locale.forLanguageTag("pt-PT"), guideVoiceLocales("pt", Locale.forLanguageTag("pt-PT")).first())
        assertEquals(Locale.ITALY, guideVoiceLocales("it", null).first())
        assertEquals(Locale.FRANCE, guideVoiceLocales("fr", Locale.CANADA_FRENCH).first())
        assertEquals(Locale.UK, guideVoiceLocales("en", Locale.US).first())
        for (langue in listOf("es", "de", "it", "pt")) {
            assertTrue(langue, guideVoiceLocales(langue, null).all { it.language == langue })
        }
    }
}
