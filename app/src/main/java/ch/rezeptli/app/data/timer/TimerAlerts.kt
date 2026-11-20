package ch.rezeptli.app.data.timer

import android.app.NotificationManager
import android.content.Context
import ch.rezeptli.app.di.ApplicationScope
import ch.rezeptli.app.domain.timer.CookingTimer
import ch.rezeptli.app.domain.timer.TimerState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sorgt dafuer, dass ein Kochtimer nicht ueberhoert wird.
 *
 * Beobachtet den einen Timer der App und tut, was ausserhalb des Bildschirms noetig ist:
 * Laeuft er, startet der Dienst, der die App wach haelt. Ist die Zeit um, klingelt es.
 * Ist er weg, ist Ruhe und die Mitteilung verschwindet.
 *
 * Das Klingeln haengt bewusst nicht am Dienst: Verweigert Android dessen Start, klingelt der
 * Timer trotzdem, solange die App lebt.
 */
@Singleton
class TimerAlerts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val timer: CookingTimer,
    private val ringer: AlarmRinger,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private enum class Mode { NONE, RUNNING, RINGING, SILENT }

    private fun TimerState?.mode(): Mode = when {
        this == null -> Mode.NONE
        !isFinished -> Mode.RUNNING
        isRinging -> Mode.RINGING
        else -> Mode.SILENT
    }

    /** Einmal beim Start der App aufrufen. */
    fun watch() {
        scope.launch {
            timer.state
                .map { it.mode() }
                .distinctUntilChanged()
                .collect { mode ->
                    when (mode) {
                        Mode.RUNNING -> {
                            ringer.stop()
                            // Gestellt wird ein Timer immer von Hand, die App ist also zu sehen -
                            // nur dann erlaubt Android, den Dienst zu starten.
                            runCatching { CookingTimerService.start(context) }
                        }

                        Mode.RINGING -> ringer.start()

                        Mode.SILENT -> ringer.stop()

                        Mode.NONE -> {
                            ringer.stop()
                            // Auch die stehen gebliebene Mitteilung eines verstummten Timers raeumen.
                            context
                                .getSystemService(NotificationManager::class.java)
                                ?.cancel(CookingTimerService.NOTIFICATION_ID)
                        }
                    }
                }
        }
    }
}
