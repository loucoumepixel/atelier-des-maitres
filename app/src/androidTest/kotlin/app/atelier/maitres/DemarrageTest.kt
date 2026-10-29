package app.atelier.maitres

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** L'application se lance comme depuis l'icône : intro du studio, puis accueil, sans planter. */
@RunWith(AndroidJUnit4::class)
class DemarrageTest {

    @Test
    fun lApplicationDemarre() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(8_000)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
