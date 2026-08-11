package ch.rezeptli.app.presentation.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.usecase.AddRecipesToShoppingListUseCase
import ch.rezeptli.app.domain.usecase.ObserveSwipeMatchesUseCase
import ch.rezeptli.app.presentation.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ResultsUiState(
    val isLoading: Boolean = true,
    val matches: List<RecipeSummary> = emptyList(),
    val isAddingToShoppingList: Boolean = false,
)

sealed interface ResultsEvent {
    /** Die Zutaten sind auf der Einkaufsliste, die jetzt [itemCount] Posten hat. */
    data class AddedToShoppingList(val itemCount: Int) : ResultsEvent
}

/**
 * Die Treffer einer Swipe-Session.
 *
 * Die Auswertung kommt aus dem SwipeMatcher: im Solo-Modus sind das alle "Ja"-Rezepte,
 * sobald zwei Personen mitswipen nur noch die gemeinsamen.
 */
@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeSwipeMatches: ObserveSwipeMatchesUseCase,
    private val addRecipesToShoppingList: AddRecipesToShoppingListUseCase,
) : ViewModel() {
    private val sessionId: Long = savedStateHandle.get<Long>(Destinations.ARG_SESSION_ID) ?: 0L

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    private val _events = Channel<ResultsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        observeSwipeMatches(sessionId)
            .onEach { matches -> _uiState.update { it.copy(matches = matches, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    /** Uebernimmt die Zutaten aller Treffer in die Einkaufsliste. */
    fun onAddToShoppingList() {
        if (_uiState.value.isAddingToShoppingList) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingToShoppingList = true) }
            val itemCount = addRecipesToShoppingList(_uiState.value.matches.map { it.id })
            _uiState.update { it.copy(isAddingToShoppingList = false) }
            _events.send(ResultsEvent.AddedToShoppingList(itemCount))
        }
    }
}
