package ch.rezeptli.app.presentation.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.usecase.ObserveSwipeMatchesUseCase
import ch.rezeptli.app.presentation.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ResultsUiState(
    val isLoading: Boolean = true,
    val matches: List<RecipeSummary> = emptyList(),
)

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
) : ViewModel() {
    private val sessionId: Long = savedStateHandle.get<Long>(Destinations.ARG_SESSION_ID) ?: 0L

    private val _uiState = MutableStateFlow(ResultsUiState())
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    init {
        observeSwipeMatches(sessionId)
            .onEach { matches -> _uiState.update { it.copy(matches = matches, isLoading = false) } }
            .launchIn(viewModelScope)
    }
}
