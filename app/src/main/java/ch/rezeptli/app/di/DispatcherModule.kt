package ch.rezeptli.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Kennzeichnet den Dispatcher fuer Datenbank- und Datei-Zugriffe.
 * Ueber den Qualifier laesst sich der Dispatcher im Test durch einen
 * TestDispatcher ersetzen.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Kennzeichnet den Bereich fuer Arbeit, die keinen Bildschirm ueberleben muss, sondern
 * die ganze App: etwa den Kochtimer, der weiterlaeuft, waehrend das Telefon gesperrt ist.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /** Ein Fehler in einer Aufgabe darf die anderen nicht mitreissen - deshalb SupervisorJob. */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
