package ch.rezeptli.app.domain.timer

import ch.rezeptli.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Ein gestellter Timer - laufend oder abgelaufen. */
data class TimerState(
    /** Zu welchem Rezept er gehoert. Nur dessen Kochmodus darf ihn verwerfen. */
    val recipeId: Long,
    val stepPosition: Int,
    /** Was auf der Benachrichtigung steht, wenn die App nicht zu sehen ist. */
    val title: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    /** Wann die Zeit um ist - in Millisekunden der [MonotonicClock], nicht der Wanduhr. */
    val endsAt: Long = 0L,
    /** Die Zeit ist um, und es klingelt noch. Nach einer Weile verstummt es von selbst. */
    val isRinging: Boolean = false,
) {
    val isFinished: Boolean get() = remainingSeconds <= 0

    val progress: Float
        get() = if (totalSeconds <= 0) 0f else (1f - remainingSeconds.toFloat() / totalSeconds).coerceIn(0f, 1f)

    /** Eine Minute weniger geht nur, solange danach noch Zeit uebrig bleibt. */
    val canShorten: Boolean get() = remainingSeconds > CookingTimer.SECONDS_PER_MINUTE

    /** Eine Minute mehr geht fast immer - auch nach dem Klingeln: "noch eine Minute". */
    val canExtend: Boolean
        get() = remainingSeconds + CookingTimer.SECONDS_PER_MINUTE <= CookingTimer.MAX_SECONDS
}

/** Eine Uhr, die nie zurueckspringt und auch weiterlaeuft, waehrend das Geraet schlaeft. */
fun interface MonotonicClock {
    fun millis(): Long
}

/**
 * Der eine Kochtimer der App.
 *
 * Er lebt ausserhalb des Bildschirms: So laeuft er weiter, wenn das Telefon gesperrt wird
 * oder kurz eine andere App offen ist - der Dienst, der dann klingelt, liest denselben Stand.
 *
 * Gerechnet wird mit dem Zeitpunkt, an dem die Zeit um ist, nicht mit gezaehlten Sekunden.
 * Ein Zaehler ginge nach, sobald das Geraet einen Takt verschlaeft; der Zeitpunkt stimmt
 * immer, und eine Minute mehr oder weniger verschiebt ihn bloss.
 */
@Singleton
class CookingTimer @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val clock: MonotonicClock,
) {
    private val _state = MutableStateFlow<TimerState?>(null)
    val state: StateFlow<TimerState?> = _state.asStateFlow()

    private val lock = Any()
    private var endsAt = 0L
    private var job: Job? = null

    /** Stellt den Timer neu. Ein vorher laufender ist damit ersetzt - es gibt nur einen. */
    fun start(recipeId: Long, stepPosition: Int, title: String, minutes: Int) {
        val seconds = minutes.coerceIn(1, MAX_MINUTES) * SECONDS_PER_MINUTE
        synchronized(lock) {
            endsAt = clock.millis() + seconds * MILLIS
            _state.value = TimerState(
                recipeId = recipeId,
                stepPosition = stepPosition,
                title = title,
                totalSeconds = seconds,
                remainingSeconds = seconds,
                endsAt = endsAt,
            )
            run()
        }
    }

    /**
     * Eine Minute mehr oder weniger ([minutes] = +1 oder -1).
     *
     * Unter null geht es nicht: Wer weniger als eine Minute uebrig hat, stoppt den Timer
     * einfach. Ist die Zeit schon um, heisst "eine Minute mehr": noch einmal eine Minute.
     */
    fun adjust(minutes: Int) {
        synchronized(lock) {
            val current = _state.value ?: return
            val delta = minutes * SECONDS_PER_MINUTE

            if (current.isFinished) {
                if (delta <= 0) return
                endsAt = clock.millis() + delta * MILLIS
                _state.value = current.copy(
                    totalSeconds = delta,
                    remainingSeconds = delta,
                    endsAt = endsAt,
                    isRinging = false,
                )
            } else {
                val left = endsAt - clock.millis() + delta * MILLIS
                if (left <= 0 || left > MAX_SECONDS * MILLIS) return
                endsAt += delta * MILLIS
                _state.value = current.copy(
                    totalSeconds = current.totalSeconds + delta,
                    remainingSeconds = secondsFor(left),
                    endsAt = endsAt,
                )
            }
            run()
        }
    }

    /** Verwirft den Timer - egal, ob er noch laeuft oder schon klingelt. */
    fun stop() {
        synchronized(lock) {
            job?.cancel()
            job = null
            _state.value = null
        }
    }

    /** Wie [stop], aber nur fuer den Timer dieses Rezepts: Ein fremder bleibt unberuehrt. */
    fun stop(recipeId: Long) {
        synchronized(lock) {
            if (_state.value?.recipeId == recipeId) stop()
        }
    }

    /** Laesst die Zeit ablaufen, klingelt und verstummt nach einer Weile von selbst. */
    private fun run() {
        job?.cancel()
        job = scope.launch {
            while (true) {
                val left = synchronized(lock) { endsAt - clock.millis() }
                if (left <= 0) break
                update { it.copy(remainingSeconds = secondsFor(left)) }
                // Bis zur naechsten vollen Sekunde warten, nicht stur eine Sekunde lang.
                delay((left - 1) % MILLIS + 1)
            }
            update { it.copy(remainingSeconds = 0, isRinging = true) }
            delay(RING_LIMIT_MS)
            update { it.copy(isRinging = false) }
        }
    }

    /** Aendert den Stand nur, solange dieser Lauf noch der gueltige ist. */
    private suspend fun update(change: (TimerState) -> TimerState) {
        val own = currentCoroutineContext()[Job]
        synchronized(lock) {
            if (job !== own) return
            _state.value = _state.value?.let(change)
        }
    }

    /** Angefangene Sekunden zaehlen ganz: "0:01" steht da, bis die Zeit wirklich um ist. */
    private fun secondsFor(leftMillis: Long): Int = ((leftMillis + MILLIS - 1) / MILLIS).toInt()

    companion object {
        const val SECONDS_PER_MINUTE = 60

        /** Laenger braucht kein Schritt - und ein Tippfehler bleibt so ohne Folgen. */
        const val MAX_MINUTES = 12 * 60
        const val MAX_SECONDS = MAX_MINUTES * SECONDS_PER_MINUTE

        /** Danach verstummt das Klingeln: Niemand ist da, und der Akku soll es nicht ausbaden. */
        const val RING_LIMIT_MS = 5 * 60 * 1_000L

        private const val MILLIS = 1_000L
    }
}
