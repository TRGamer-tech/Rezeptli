package ch.rezeptli.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Sammelt einmalige Ereignisse eines ViewModels nur, solange der Screen sichtbar ist.
 *
 * Ohne diese Bindung an den Lebenszyklus koennte eine Snackbar waehrend eines
 * Bildschirmwechsels ins Leere laufen.
 */
@Composable
fun <T> ObserveAsEvents(
    flow: Flow<T>,
    key: Any? = null,
    context: CoroutineContext = EmptyCoroutineContext,
    onEvent: (T) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEvent by rememberUpdatedState(onEvent)

    LaunchedEffect(flow, lifecycleOwner.lifecycle, key) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(context) {
                flow.collect { currentOnEvent(it) }
            }
        }
    }
}
