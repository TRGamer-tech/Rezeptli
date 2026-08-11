package ch.rezeptli.app.presentation.websearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.data.web.RecipeSource
import ch.rezeptli.app.data.web.RecipeSourceCatalog
import ch.rezeptli.app.domain.model.WebSearchResult
import ch.rezeptli.app.domain.usecase.SearchWebRecipesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WebSearchUiState(
    val query: String = "",
    val selectedSourceIds: Set<String> = DEFAULT_SOURCES,
    val results: List<WebSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
) {
    val sources: List<RecipeSource> get() = RecipeSourceCatalog.SEARCHABLE

    val canSearch: Boolean
        get() = query.isNotBlank() && selectedSourceIds.isNotEmpty() && !isSearching

    companion object {
        /** Zum Start die drei Quellen mit dem groessten Rezeptbestand. */
        val DEFAULT_SOURCES = setOf("swissmilk", "gutekueche", "bettybossi")
    }
}

/**
 * Sucht Rezepte in den Verzeichnissen der Web-Quellen.
 *
 * Geladen und gespeichert wird ein Treffer nicht hier, sondern im Import-Screen: dort
 * gibt es bereits Vorschau, Korrektur und Speichern - und die Regel, dass nie
 * ungeprueft uebernommen wird, gilt fuer Web-Rezepte genauso wie fuer eingefuegten Text.
 */
@HiltViewModel
class WebSearchViewModel @Inject constructor(
    private val searchWebRecipes: SearchWebRecipesUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WebSearchUiState())
    val uiState: StateFlow<WebSearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun onToggleSource(sourceId: String) {
        _uiState.update { state ->
            val selected = if (sourceId in state.selectedSourceIds) {
                state.selectedSourceIds - sourceId
            } else {
                state.selectedSourceIds + sourceId
            }
            state.copy(selectedSourceIds = selected)
        }
    }

    fun onSearch() {
        val state = _uiState.value
        if (!state.canSearch) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val results = searchWebRecipes(state.query, state.selectedSourceIds)
            _uiState.update {
                it.copy(results = results, isSearching = false, hasSearched = true)
            }
        }
    }
}
