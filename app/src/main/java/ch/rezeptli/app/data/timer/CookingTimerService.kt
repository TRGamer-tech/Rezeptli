package ch.rezeptli.app.data.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.timer.CookingTimer
import ch.rezeptli.app.domain.timer.TimerState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Haelt den Kochtimer am Leben, solange die App nicht zu sehen ist.
 *
 * Ohne diesen Dienst duerfte Android die App im Hintergrund jederzeit anhalten - der Timer
 * wuerde dann zu spaet oder gar nicht klingeln. Der Dienst zeigt dafuer eine Mitteilung mit
 * der Restzeit; ueber sie laesst sich der Timer auch vom Sperrbildschirm aus stoppen.
 *
 * Er rechnet nichts selbst: Den Stand liefert [CookingTimer], geklingelt wird von
 * [TimerAlerts]. Ist der Timer weg, beendet sich der Dienst.
 */
@AndroidEntryPoint
class CookingTimerService : Service() {
    @Inject
    lateinit var timer: CookingTimer

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var isForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels(this)

        scope.launch {
            timer.state
                // Die Restzeit zaehlt die Mitteilung selbst herunter - neu gezeichnet wird nur,
                // wenn sich wirklich etwas aendert: verstellt, abgelaufen, verstummt, verworfen.
                .map { it?.copy(remainingSeconds = 0, totalSeconds = 0) }
                .distinctUntilChanged()
                .collect { show(timer.state.value) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) timer.stop()
        show(timer.state.value)
        // Stirbt die App, ist auch der Timer weg - ein neu gestarteter Dienst haette nichts zu zeigen.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun show(state: TimerState?) {
        val notification = notification(this, state)

        // Android verlangt nach dem Start innert Sekunden eine Mitteilung - auch dann, wenn
        // der Timer in der Zwischenzeit schon wieder gestoppt wurde.
        if (!isForeground) {
            isForeground = enterForeground(notification)
        } else if (state != null) {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
        }

        when {
            state == null -> leave(keepNotification = false)

            // Abgelaufen und verstummt: Die Mitteilung bleibt stehen, der Dienst darf gehen.
            state.isFinished && !state.isRinging -> leave(keepNotification = true)

            else -> stayAwake(state)
        }
    }

    private fun enterForeground(notification: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (_: IllegalStateException) {
        // Android laesst den Start gerade nicht zu (die App ist nicht im Vordergrund). Der
        // Timer laeuft trotzdem weiter, solange die App lebt - nur eben ohne diese Absicherung.
        false
    } catch (_: SecurityException) {
        false
    }

    private fun leave(keepNotification: Boolean) {
        releaseWakeLock()
        if (isForeground) {
            stopForeground(if (keepNotification) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        stopSelf()
    }

    /**
     * Haelt den Prozessor wach, bis die Zeit um und das Klingeln vorbei ist.
     *
     * Bei ausgeschaltetem Bildschirm legt sich das Telefon sonst schlafen, und der Timer
     * meldet sich erst beim naechsten Aufwachen. Die Sperre hat ein Ablaufdatum - sie kann
     * also nie haengen bleiben, was auch geschieht.
     */
    private fun stayAwake(state: TimerState) {
        val left = (state.endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        releaseWakeLock()
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            ?.apply {
                setReferenceCounted(false)
                acquire(left + CookingTimer.RING_LIMIT_MS + WAKE_MARGIN_MS)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock -> if (lock.isHeld) lock.release() }
        wakeLock = null
    }

    companion object {
        const val NOTIFICATION_ID = 4_711
        const val ACTION_STOP = "ch.rezeptli.app.timer.STOPPEN"

        /** Fuer den laufenden Timer: still und unaufdringlich. */
        private const val CHANNEL_RUNNING = "kochtimer_laeuft"

        /** Fuer den abgelaufenen: Er soll sich vordraengen - den Ton liefert die App selbst. */
        private const val CHANNEL_DONE = "kochtimer_um"

        private const val WAKE_LOCK_TAG = "rezeptli:kochtimer"
        private const val WAKE_MARGIN_MS = 10_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, CookingTimerService::class.java))
        }

        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val running = NotificationChannel(
                CHANNEL_RUNNING,
                context.getString(R.string.timer_kanal_laeuft),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.timer_kanal_laeuft_beschreibung)
                setShowBadge(false)
            }
            val done = NotificationChannel(
                CHANNEL_DONE,
                context.getString(R.string.timer_kanal_um),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.timer_kanal_um_beschreibung)
                // Geklingelt und vibriert wird von der App, mit dem Weckton - nicht noch einmal von hier.
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannels(listOf(running, done))
        }

        /** Die Mitteilung zum Stand [state] - ohne Timer eine leere, die gleich wieder verschwindet. */
        fun notification(context: Context, state: TimerState?): Notification {
            val finished = state?.isFinished == true
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            // Wie ein Tipp auf das App-Symbol: Die App kommt so nach vorn, wie sie verlassen wurde.
            val open = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.let { PendingIntent.getActivity(context, 0, it, flags) }
            val stopIntent = Intent(context, CookingTimerService::class.java).setAction(ACTION_STOP)
            val stop = PendingIntent.getService(context, 0, stopIntent, flags)

            val builder = Notification
                .Builder(context, if (finished) CHANNEL_DONE else CHANNEL_RUNNING)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentTitle(
                    context.getString(if (finished) R.string.timer_meldung_um else R.string.timer_meldung_laeuft),
                ).setContentText(state?.title.orEmpty())
                .setContentIntent(open)
                .setCategory(if (finished) Notification.CATEGORY_ALARM else Notification.CATEGORY_PROGRESS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                // Vordraengen soll sie sich genau einmal: in dem Moment, in dem die Zeit um ist.
                .setOnlyAlertOnce(!(finished && state?.isRinging == true))
                // Solange es laeuft oder klingelt, laesst sie sich nicht wegwischen - danach schon.
                .setOngoing(state != null && (!finished || state.isRinging))
                .setAutoCancel(finished)

            if (state != null && !finished) {
                val left = (state.endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                builder
                    .setWhen(System.currentTimeMillis() + left)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
            }
            if (state != null && (!finished || state.isRinging)) {
                builder.addAction(
                    Notification.Action
                        .Builder(
                            Icon.createWithResource(context, R.drawable.ic_stat_timer),
                            context.getString(R.string.timer_meldung_stoppen),
                            stop,
                        ).build(),
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Sofort zeigen: Android wuerde die Mitteilung eines Dienstes sonst zehn Sekunden zurueckhalten.
                builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            }
            return builder.build()
        }
    }
}
