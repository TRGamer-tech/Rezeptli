package ch.rezeptli.app.timer

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ch.rezeptli.app.data.timer.CookingTimerService
import ch.rezeptli.app.di.TimerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Laeuft der Kochtimer auf einem echten Android auch ausserhalb des Bildschirms?
 *
 * Dafuer muss ein Dienst mit einer Mitteilung starten - und genau das verweigert Android
 * stillschweigend, wenn im Manifest eine Berechtigung oder die Art des Dienstes fehlt.
 * Der Test benutzt den Timer der App selbst, nicht einen nachgebauten.
 */
@RunWith(AndroidJUnit4::class)
class CookingTimerServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val timer = EntryPointAccessors.fromApplication(context, TimerEntryPoint::class.java).cookingTimer()
    private val notifications = context.getSystemService(NotificationManager::class.java)

    @Before
    fun mitteilungenErlauben() {
        // Auf dem Telefon fragt die App danach, sobald jemand einen Timer startet.
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun aufraeumen() {
        timer.stop()
    }

    private fun mitteilung(): StatusBarNotification? =
        notifications.activeNotifications.firstOrNull { it.id == CookingTimerService.NOTIFICATION_ID }

    private fun <T : Any> warteAuf(was: String, pruefe: () -> T?): T {
        val schluss = System.currentTimeMillis() + WAIT_MS
        while (System.currentTimeMillis() < schluss) {
            pruefe()?.let { return it }
            Thread.sleep(STEP_MS)
        }
        return checkNotNull(pruefe()) { "Nach $WAIT_MS ms immer noch nicht: $was" }
    }

    @Test
    fun einLaufenderTimerZeigtSeineMitteilungUndRaeumtSieBeimStoppenWeg() {
        timer.start(recipeId = 1L, stepPosition = 0, title = "Risotto", minutes = 5)

        val meldung = warteAuf("Die Mitteilung des laufenden Timers erscheint") { mitteilung() }
        val inhalt = meldung.notification

        assertEquals("Risotto", inhalt.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        assertTrue("Sie laesst sich nicht wegwischen, solange der Timer laeuft", meldung.isOngoing)
        // Android fuehrt sie als Mitteilung eines Vordergrund-Dienstes: Der Start wurde nicht verweigert.
        assertTrue(inhalt.flags and Notification.FLAG_FOREGROUND_SERVICE != 0)
        // Ueber "Stoppen" laesst sich der Timer auch vom Sperrbildschirm aus beenden.
        assertEquals(1, inhalt.actions?.size)
        // Die Restzeit zaehlt die Mitteilung selbst herunter.
        assertTrue(inhalt.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        val rest = inhalt.`when` - System.currentTimeMillis()
        assertTrue("Unerwartete Restzeit: $rest ms", rest in 4 * 60_000L..5 * 60_000L)

        // Eine Minute mehr verschiebt auch das Ende in der Mitteilung.
        timer.adjust(+1)
        warteAuf("Die Mitteilung zeigt die verlaengerte Zeit") {
            mitteilung()?.takeIf { it.notification.`when` - System.currentTimeMillis() > 5 * 60_000L }
        }

        timer.stop()
        warteAuf("Die Mitteilung verschwindet mit dem Timer") { if (mitteilung() == null) Unit else null }
    }

    @Test
    fun stoppenUeberDieMitteilungBeendetDenTimer() {
        timer.start(recipeId = 1L, stepPosition = 0, title = "Risotto", minutes = 5)
        val meldung = warteAuf("Die Mitteilung des laufenden Timers erscheint") { mitteilung() }

        val stoppen = meldung.notification.actions.single()
        assertNotNull(stoppen.actionIntent)
        stoppen.actionIntent.send()

        warteAuf("Der Timer ist gestoppt") { if (timer.state.value == null) Unit else null }
        warteAuf("Die Mitteilung verschwindet mit dem Timer") { if (mitteilung() == null) Unit else null }
    }

    private companion object {
        const val WAIT_MS = 10_000L
        const val STEP_MS = 100L
    }
}
