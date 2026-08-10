package ch.rezeptli.app.presentation.recipelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.usecase.ObservePagedRecipesUseCase
import ch.rezeptli.app.domain.usecase.ObserveRecipeCountUseCase
import ch.rezeptli.app.domain.usecase.ObserveTagsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** Zustand der Rezeptliste. */
data class RecipeListUiState(
    val query: String = "",
    val selectedTags: Set<String> = emptySet(),
    val maxPrepTimeMinutes: Int? = null,
    val availableTags: List<String> = emptyList(),
    val totalRecipeCount: Int = 0,
    val isFilterVisible: Boolean = false,
) {
    val filter: RecipeFilter
        get() = RecipeFilter(
            query = query,
            tags = selectedTags,
            maxPrepTimeMinutes = maxPrepTimeMinutes,
        )

    /** Ob ueberhaupt Rezepte existieren - unabhaengig vom aktuellen Filter. */
    val hasAnyRecipes: Boolean get() = totalRecipeCount > 0

    val isFilterActive: Boolean get() = filter.isActive
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class RecipeListViewModel @Inject constructor(
    observePagedRecipes: ObservePagedRecipesUseCase,
    observeRecipeCount: ObserveRecipeCountUseCase,
    observeTags: ObserveTagsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecipeListUiState())
    val uiState: StateFlow<RecipeListUiState> = _uiState.asStateFlow()

    /**
     * Die Rezepte zum aktuellen Filter, seitenweise geladen.
     *
     * Die Suche wird kurz entprellt, damit nicht bei jedem Tastendruck eine neue
     * Datenbankabfrage startet. `cachedIn` haelt die geladenen Seiten ueber
     * Konfigurationswechsel hinweg.
     */
    val recipes: Flow<PagingData<RecipeSummary>> = _uiState
        .map { it.filter }
        .distinctUntilChanged()
        .debounce { filter -> if (filter.query.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
        .flatMapLatest { filter -> observePagedRecipes(filter) }
        .cachedIn(viewModelScope)

    init {
        observeRecipeCount()
            .onEach { count -> _uiState.update { it.copy(totalRecipeCount = count) } }
            .launchIn(viewModelScope)

        observeTags()
            .onEach { tags -> _uiState.update { it.copy(availableTags = tags) } }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
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

    fun onFilterVisibilityChange(visible: Boolean) {
        _uiState.update { it.copy(isFilterVisible = visible) }
    }

    fun onResetFilter() {
        _uiState.update {
            it.copy(query = "", selectedTags = emptySet(), maxPrepTimeMinutes = null)
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
