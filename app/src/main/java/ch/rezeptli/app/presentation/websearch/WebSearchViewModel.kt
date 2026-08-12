package ch.rezeptli.app.presentation.websearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.data.web.RecipeSource
import ch.rezeptli.app.data.web.RecipeSourceCatalog
import ch.rezeptli.app.domain.model.WebSearchResult
import ch.rezeptli.app.domain.usecase.SearchWebRecipesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** Wie viele der befragten Quellen schon geantwortet haben. */
    val finishedSources: Int = 0,
    val totalSources: Int = 0,
) {
    val sources: List<RecipeSource> get() = RecipeSourceCatalog.SEARCHABLE

    val canSearch: Boolean
        get() = query.isNotBlank() && selectedSourceIds.isNotEmpty() && !isSearching

    /** Es laeuft noch etwas, aber es ist schon etwas da - dann Platzhalter anzeigen. */
    val showsSkeleton: Boolean get() = isSearching

    /** Erst wenn alle Quellen durch sind, ist "nichts gefunden" auch wirklich wahr. */
    val isEmptyResult: Boolean get() = hasSearched && !isSearching && results.isEmpty()

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

    private var searchJob: Job? = null
    private var tippJob: Job? = null

    /**
     * Sucht von selbst, sobald das Tippen kurz aussetzt.
     *
     * Nach jedem Zeichen zu suchen hiesse, bei "Risotto" siebenmal loszulaufen und
     * sechs Ergebnisse gleich wieder wegzuwerfen. Eine kurze Pause reicht, um
     * abzuwarten, ob noch etwas kommt - und ist kurz genug, dass es sich anfuehlt,
     * als suche die App waehrend des Tippens.
     */
    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }

        tippJob?.cancel()
        if (query.isBlank()) {
            // Ein geleertes Feld beendet auch eine laufende Suche.
            searchJob?.cancel()
            _uiState.update { it.copy(isSearching = false, results = emptyList(), hasSearched = false) }
            return
        }

        tippJob = viewModelScope.launch {
            delay(TIPP_PAUSE_MS)
            onSearch()
        }
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

    /**
     * Laedt die Verzeichnisse der voreingestellten Quellen im Hintergrund.
     *
     * Das ist die eigentliche Antwort auf die lange erste Suche: Nicht die Suche wird
     * schneller, sondern die Arbeit passiert, bevor jemand danach fragt.
     */
    init {
        viewModelScope.launch {
            searchWebRecipes.warmUp(_uiState.value.selectedSourceIds)
        }
    }

    fun onSearch() {
        val state = _uiState.value
        if (state.query.isBlank() || state.selectedSourceIds.isEmpty()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearching = true,
                    hasSearched = true,
                    results = emptyList(),
                    finishedSources = 0,
                    totalSources = 0,
                )
            }

            searchWebRecipes(state.query, state.selectedSourceIds).collect { update ->
                _uiState.update {
                    it.copy(
                        results = update.results,
                        finishedSources = update.finishedSources,
                        totalSources = update.totalSources,
                    )
                }
            }

            _uiState.update { it.copy(isSearching = false) }
        }
    }

    private companion object {
        /** So lange wartet die Suche auf das naechste Zeichen. */
        const val TIPP_PAUSE_MS = 200L
    }
}
