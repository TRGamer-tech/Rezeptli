package ch.rezeptli.app.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.usecase.ObserveOpenShoppingCountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Was die untere Leiste wissen muss, egal welcher Bildschirm gerade offen ist: wie viele
 * Posten auf der Einkaufsliste noch offen sind.
 */
@HiltViewModel
class NavShellViewModel @Inject constructor(
    observeOpenShoppingCount: ObserveOpenShoppingCountUseCase,
) : ViewModel() {
    val openShoppingItems: StateFlow<Int> = observeOpenShoppingCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = 0,
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
