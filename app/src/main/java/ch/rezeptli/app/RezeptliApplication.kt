package ch.rezeptli.app

import android.app.Application
import ch.rezeptli.app.data.timer.TimerAlerts
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RezeptliApplication : Application() {
    @Inject
    lateinit var timerAlerts: TimerAlerts

    override fun onCreate() {
        super.onCreate()
        // Der Kochtimer gehoert der App, nicht einem Bildschirm - deshalb wird hier gewacht.
        timerAlerts.watch()
    }
}
