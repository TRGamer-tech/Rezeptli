package ch.rezeptli.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Ersetzt den Main-Dispatcher fuer ViewModel-Tests durch einen TestDispatcher.
 *
 * ViewModels starten ihre Coroutinen im viewModelScope, der auf Dispatchers.Main laeuft -
 * ohne diesen Ersatz wuerden die Tests auf einer JVM ohne Android-Looper scheitern.
 *
 * Bewusst ein UnconfinedTestDispatcher: Er hat einen eigenen Scheduler, unabhaengig von dem
 * in `runTest`. Coroutinen des ViewModels laufen deshalb sofort los, statt auf ein
 * `advanceUntilIdle` des Test-Schedulers zu warten, das sie gar nicht erreichen wuerde.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext?) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun afterEach(context: ExtensionContext?) {
        Dispatchers.resetMain()
    }
}
