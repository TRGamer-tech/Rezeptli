package ch.rezeptli.app.timer

import android.content.Context
import android.media.MediaPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ch.rezeptli.app.R
import ch.rezeptli.app.data.timer.AlarmRinger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Klingelt der Kochtimer auf einem echten Android wirklich?
 *
 * Aufgefallen war, dass der Timer stumm ablief. Ob ein Ton laeuft, laesst sich nur auf einem
 * Geraet pruefen - hier spielt derselbe Code wie in der App.
 */
@RunWith(AndroidJUnit4::class)
class AlarmRingerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ringer = AlarmRinger(context)

    @After
    fun ruhe() {
        ringer.stop()
    }

    @Test
    fun derTimerKlingeltBisErGestopptWird() {
        val ton = ringer.start()

        // Der Weckton des Geraets oder der mitgelieferte - Hauptsache, es ist etwas zu hoeren.
        assertNotEquals("Es laeuft kein Ton", AlarmRinger.Sound.NONE, ton)
        assertTrue("Der Ton $ton spielt nicht", ringer.isAudible)
        assertEquals(ton, ringer.playing)

        // Noch einmal starten aendert nichts: Es klingelt nicht doppelt.
        assertEquals(ton, ringer.start())
        assertTrue(ringer.isAudible)

        ringer.stop()
        assertFalse(ringer.isAudible)
        assertNull(ringer.playing)

        // Und nach dem Stoppen laesst er sich wieder starten - fuer den naechsten Timer.
        assertNotEquals(AlarmRinger.Sound.NONE, ringer.start())
        assertTrue(ringer.isAudible)
    }

    @Test
    fun derMitgelieferteTonIstEinAbspielbarerTon() {
        // Er springt ein, wenn das Telefon keinen Weckton hat - dann muss er auch taugen.
        val player = MediaPlayer.create(context, R.raw.timer_klingeln)

        assertNotNull("Der mitgelieferte Ton laesst sich nicht oeffnen", player)
        try {
            assertTrue("Unerwartete Laenge: ${player.duration} ms", player.duration in 1_000..3_000)
        } finally {
            player.release()
        }
    }
}
