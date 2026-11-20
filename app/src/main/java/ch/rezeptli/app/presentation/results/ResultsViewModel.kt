package ch.rezeptli.app.presentation.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.repository.UserProfileRepository
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
    /** Fuer so viele wird ueblicherweise gekocht - die Vorgabe in der Frage "fuer wie viele?". */
    val persons: Int = 2,
    val isPortionDialogVisible: Boolean = false,
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
    profileRepository: UserProfileRepository,
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
        profileRepository.profile
            .onEach { profile -> _uiState.update { it.copy(persons = profile.persons) } }
            .launchIn(viewModelScope)
    }

    /** Vor dem Uebernehmen steht die Frage, fuer wie viele eingekauft wird. */
    fun onAddToShoppingListRequest() {
        if (_uiState.value.isAddingToShoppingList) return
        _uiState.update { it.copy(isPortionDialogVisible = true) }
    }

    fun onAddToShoppingListDismiss() {
        _uiState.update { it.copy(isPortionDialogVisible = false) }
    }

    /** Uebernimmt die Zutaten aller Treffer in die Einkaufsliste - umgerechnet auf [persons]. */
    fun onAddToShoppingList(persons: Int) {
        if (_uiState.value.isAddingToShoppingList) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAddingToShoppingList = true, isPortionDialogVisible = false) }
            val itemCount = addRecipesToShoppingList(_uiState.value.matches.map { it.id }, persons = persons)
            _uiState.update { it.copy(isAddingToShoppingList = false) }
            _events.send(ResultsEvent.AddedToShoppingList(itemCount))
        }
    }
}
