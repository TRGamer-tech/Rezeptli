package ch.rezeptli.app.di

import android.os.SystemClock
import ch.rezeptli.app.domain.timer.CookingTimer
import ch.rezeptli.app.domain.timer.MonotonicClock
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TimerModule {
    /**
     * Die Uhr des Kochtimers: Zeit seit dem Einschalten des Geraets, Schlaf eingerechnet.
     *
     * Die Wanduhr taugt dafuer nicht - sie springt, wenn sie sich mit dem Netz abgleicht
     * oder jemand sie verstellt, und ein Timer waere dann ploetzlich zu frueh oder zu spaet.
     */
    @Provides
    @Singleton
    fun provideMonotonicClock(): MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() }
}

/**
 * Zugang zum Kochtimer fuer Stellen, die Hilt nicht selbst versorgt - etwa der Geraetetest,
 * der prueft, ob der Dienst auf einem echten Android wirklich startet.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TimerEntryPoint {
    fun cookingTimer(): CookingTimer
}
