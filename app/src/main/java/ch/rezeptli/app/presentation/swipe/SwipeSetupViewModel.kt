package ch.rezeptli.app.presentation.swipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.usecase.CountMatchingRecipesUseCase
import ch.rezeptli.app.domain.usecase.ObserveTagsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SwipeSetupUiState(
    val availableTags: List<String> = emptyList(),
    val selectedTags: Set<String> = emptySet(),
    val maxPrepTimeMinutes: Int? = null,
    val matchingRecipeCount: Int = 0,
    val isCounting: Boolean = true,
) {
    val filter: RecipeFilter
        get() = RecipeFilter(tags = selectedTags, maxPrepTimeMinutes = maxPrepTimeMinutes)

    val canStart: Boolean get() = matchingRecipeCount > 0
}

/** Vorab-Filter fuer eine Swipe-Session: was kommt ueberhaupt auf den Stapel? */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SwipeSetupViewModel @Inject constructor(
    observeTags: ObserveTagsUseCase,
    countMatchingRecipes: CountMatchingRecipesUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SwipeSetupUiState())
    val uiState: StateFlow<SwipeSetupUiState> = _uiState.asStateFlow()

    init {
        observeTags()
            .onEach { tags -> _uiState.update { it.copy(availableTags = tags) } }
            .launchIn(viewModelScope)

        _uiState
            .map { it.filter }
            .distinctUntilChanged()
            .onEach { _uiState.update { state -> state.copy(isCounting = true) } }
            .mapLatest { filter -> countMatchingRecipes(filter) }
            .onEach { count ->
                _uiState.update { it.copy(matchingRecipeCount = count, isCounting = false) }
            }.launchIn(viewModelScope)
    }

    fun onToggleTag(tag: String) {
        _uiState.update { state ->
            val tags = if (tag in state.selectedTags) state.selectedTags - tag else state.selectedTags + tag
            state.copy(selectedTags = tags)
        }
    }

    fun onMaxPrepTimeChange(minutes: Int?) {
        _uiState.update { it.copy(maxPrepTimeMinutes = minutes) }
    }

    fun onResetFilter() {
        _uiState.update { it.copy(selectedTags = emptySet(), maxPrepTimeMinutes = null) }
    }
}
