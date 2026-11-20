package ch.rezeptli.app.domain.timer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Der Kochtimer: laeuft nach der Uhr, laesst sich um Minuten verschieben und klingelt.
 *
 * Aufgefallen war, dass er beim Ablaufen stumm blieb und sich nicht nachstellen liess.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CookingTimerTest {
    /** Zeit, die vergeht, ohne dass die App etwas davon mitbekommt - das Geraet schlaeft. */
    private var verschlafen = 0L

    private fun TestScope.timer() = CookingTimer(backgroundScope) { testScheduler.currentTime + verschlafen }

    private fun TestScope.warte(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun CookingTimer.stelle(minutes: Int, recipeId: Long = 7L, step: Int = 2) =
        start(recipeId = recipeId, stepPosition = step, title = "Risotto", minutes = minutes)

    private val CookingTimer.jetzt: TimerState get() = state.value!!

    @Test
    fun `ein gestellter Timer zaehlt die Sekunden herunter`() = runTest {
        val timer = timer()

        timer.stelle(10)
        assertEquals(600, timer.jetzt.remainingSeconds)
        assertEquals(600, timer.jetzt.totalSeconds)
        assertEquals(2, timer.jetzt.stepPosition)
        assertEquals("Risotto", timer.jetzt.title)
        assertFalse(timer.jetzt.isFinished)

        warte(1_000)
        assertEquals(599, timer.jetzt.remainingSeconds)

        warte(89_000)
        assertEquals(510, timer.jetzt.remainingSeconds)
        assertEquals(0.15f, timer.jetzt.progress, 0.001f)
    }

    @Test
    fun `eine angefangene Sekunde zaehlt ganz`() = runTest {
        val timer = timer()
        timer.stelle(1)

        warte(59_400)

        // 0.6 Sekunden sind noch uebrig - es steht 0:01 da, nicht schon 0:00.
        assertEquals(1, timer.jetzt.remainingSeconds)
        assertFalse(timer.jetzt.isFinished)
    }

    @Test
    fun `verschlaeft das Geraet einen Takt, stimmt die Zeit danach trotzdem`() = runTest {
        val timer = timer()
        timer.stelle(10)
        warte(1_000)

        // Eine halbe Minute vergeht, ohne dass die App rechnen durfte.
        verschlafen = 30_000
        warte(1_000)

        assertEquals(600 - 32, timer.jetzt.remainingSeconds)
    }

    @Test
    fun `ist das Geraet ueber das Ende hinaus eingeschlafen, klingelt es beim Aufwachen sofort`() = runTest {
        val timer = timer()
        timer.stelle(1)
        warte(1_000)

        verschlafen = 5 * 60_000
        warte(1_000)

        assertTrue(timer.jetzt.isFinished)
        assertTrue(timer.jetzt.isRinging)
    }

    @Test
    fun `eine Minute mehr verschiebt das Ende und laesst den Fortschritt stehen`() = runTest {
        val timer = timer()
        timer.stelle(10)
        warte(120_000)
        assertEquals(480, timer.jetzt.remainingSeconds)

        timer.adjust(+1)

        assertEquals(540, timer.jetzt.remainingSeconds)
        assertEquals(660, timer.jetzt.totalSeconds)
        // Zwei Minuten sind gelaufen - daran aendert eine Minute mehr nichts.
        assertEquals(120, timer.jetzt.totalSeconds - timer.jetzt.remainingSeconds)

        warte(540_000)
        assertTrue(timer.jetzt.isFinished)
    }

    @Test
    fun `eine Minute weniger laesst ihn frueher klingeln`() = runTest {
        val timer = timer()
        timer.stelle(3)
        warte(30_000)

        timer.adjust(-1)

        assertEquals(90, timer.jetzt.remainingSeconds)
        assertEquals(120, timer.jetzt.totalSeconds)

        warte(89_000)
        assertFalse(timer.jetzt.isFinished)
        warte(1_000)
        assertTrue(timer.jetzt.isFinished)
    }

    @Test
    fun `mitten in einer Sekunde verstellt, laeuft die Anzeige im Takt weiter`() = runTest {
        val timer = timer()
        timer.stelle(5)
        warte(10_300)

        timer.adjust(+1)
        assertEquals(350, timer.jetzt.remainingSeconds)

        // Die laufende Sekunde endet nach 0.7 Sekunden, nicht erst nach einer ganzen.
        warte(700)
        assertEquals(349, timer.jetzt.remainingSeconds)
    }

    @Test
    fun `unter eine Minute laesst er sich nicht kuerzen`() = runTest {
        val timer = timer()
        timer.stelle(1)
        assertFalse(timer.jetzt.canShorten)

        timer.adjust(-1)

        assertEquals(60, timer.jetzt.remainingSeconds)
        assertFalse(timer.jetzt.isFinished)

        timer.stelle(2)
        assertTrue(timer.jetzt.canShorten)
        warte(60_000)
        // Genau eine Minute ist uebrig: Eine weniger waere null.
        assertFalse(timer.jetzt.canShorten)
        timer.adjust(-1)
        assertEquals(60, timer.jetzt.remainingSeconds)
    }

    @Test
    fun `ist die Zeit um, klingelt es - und verstummt nach einer Weile von selbst`() = runTest {
        val timer = timer()
        timer.stelle(1)

        warte(60_000)
        assertTrue(timer.jetzt.isFinished)
        assertTrue(timer.jetzt.isRinging)
        assertEquals(0, timer.jetzt.remainingSeconds)
        assertEquals(1f, timer.jetzt.progress)

        warte(CookingTimer.RING_LIMIT_MS - 1_000)
        assertTrue(timer.jetzt.isRinging)

        warte(1_000)
        // Niemand hat reagiert: Es ist still, aber der Timer steht weiter als abgelaufen da.
        assertFalse(timer.jetzt.isRinging)
        assertTrue(timer.jetzt.isFinished)
    }

    @Test
    fun `noch eine Minute nach dem Klingeln stellt ihn neu und macht Ruhe`() = runTest {
        val timer = timer()
        timer.stelle(1)
        warte(60_000)
        assertTrue(timer.jetzt.isRinging)

        timer.adjust(+1)

        assertFalse(timer.jetzt.isRinging)
        assertFalse(timer.jetzt.isFinished)
        assertEquals(60, timer.jetzt.remainingSeconds)
        assertEquals(60, timer.jetzt.totalSeconds)

        // Das alte Verstummen darf den neuen Lauf nicht stoeren - er klingelt wieder voll.
        warte(60_000)
        assertTrue(timer.jetzt.isRinging)
        warte(CookingTimer.RING_LIMIT_MS - 1_000)
        assertTrue(timer.jetzt.isRinging)
    }

    @Test
    fun `eine Minute weniger nach dem Klingeln tut nichts`() = runTest {
        val timer = timer()
        timer.stelle(1)
        warte(60_000)

        timer.adjust(-1)

        assertTrue(timer.jetzt.isFinished)
        assertTrue(timer.jetzt.isRinging)
    }

    @Test
    fun `stoppen verwirft ihn ganz`() = runTest {
        val timer = timer()
        timer.stelle(1)
        warte(10_000)

        timer.stop()
        assertNull(timer.state.value)

        // Auch spaeter meldet sich der gestoppte Lauf nicht mehr.
        warte(120_000)
        assertNull(timer.state.value)

        // Und ohne Timer gibt es nichts zu verstellen.
        timer.adjust(+1)
        assertNull(timer.state.value)
    }

    @Test
    fun `stoppen waehrend es klingelt macht sofort Ruhe`() = runTest {
        val timer = timer()
        timer.stelle(1)
        warte(60_000)
        assertTrue(timer.jetzt.isRinging)

        timer.stop()

        assertNull(timer.state.value)
    }

    @Test
    fun `nur der Kochmodus des eigenen Rezepts verwirft den Timer`() = runTest {
        val timer = timer()
        timer.stelle(5, recipeId = 7L)

        timer.stop(recipeId = 8L)
        assertEquals(7L, timer.jetzt.recipeId)

        timer.stop(recipeId = 7L)
        assertNull(timer.state.value)
    }

    @Test
    fun `ein neu gestellter Timer ersetzt den alten vollstaendig`() = runTest {
        val timer = timer()
        timer.stelle(1, step = 1)
        warte(30_000)

        timer.stelle(5, step = 4)
        assertEquals(4, timer.jetzt.stepPosition)
        assertEquals(300, timer.jetzt.remainingSeconds)

        // Der alte waere jetzt abgelaufen - er darf nicht dazwischenklingeln.
        warte(31_000)
        assertFalse(timer.jetzt.isFinished)
        assertEquals(269, timer.jetzt.remainingSeconds)
    }

    @Test
    fun `unsinnige Zeiten werden auf das Moegliche begrenzt`() = runTest {
        val timer = timer()

        timer.stelle(0)
        assertEquals(60, timer.jetzt.remainingSeconds)

        timer.stelle(100_000)
        assertEquals(CookingTimer.MAX_SECONDS, timer.jetzt.remainingSeconds)
        assertFalse(timer.jetzt.canExtend)

        timer.adjust(+1)
        assertEquals(CookingTimer.MAX_SECONDS, timer.jetzt.remainingSeconds)
    }
}
