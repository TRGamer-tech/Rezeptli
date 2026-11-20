package ch.rezeptli.app.presentation.recipelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.repository.UserProfileRepository
import ch.rezeptli.app.domain.usecase.DeleteRecipeUseCase
import ch.rezeptli.app.domain.usecase.MarkRecipeCookedUseCase
import ch.rezeptli.app.domain.usecase.ObservePagedRecipesUseCase
import ch.rezeptli.app.domain.usecase.ObserveRecipeCountUseCase
import ch.rezeptli.app.domain.usecase.ObserveTagsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zustand der Rezeptliste. */
data class RecipeListUiState(
    val query: String = "",
    val selectedTags: Set<String> = emptySet(),
    val maxPrepTimeMinutes: Int? = null,
    val availableTags: List<String> = emptyList(),
    val totalRecipeCount: Int = 0,
    val firstName: String = "",
    val isFilterVisible: Boolean = false,
    /** `true` zeigt nur Gekochtes, `false` nur noch nie Gekochtes, `null` alles. */
    val cooked: Boolean? = null,
    /** Das Rezept, das nach einem Wisch nach links entfernt werden soll - erst nach Rueckfrage. */
    val pendingRemoval: RecipeSummary? = null,
    /** Das Rezept, das nach einem Wisch nach rechts als gekocht vermerkt werden soll. */
    val pendingCooked: RecipeSummary? = null,
) {
    val filter: RecipeFilter
        get() = RecipeFilter(
            query = query,
            tags = selectedTags,
            maxPrepTimeMinutes = maxPrepTimeMinutes,
            cooked = cooked,
        )

    /** Ob ueberhaupt Rezepte existieren - unabhaengig vom aktuellen Filter. */
    val hasAnyRecipes: Boolean get() = totalRecipeCount > 0

    val isFilterActive: Boolean get() = filter.isActive
}

/** Kurze Rueckmeldungen nach einer Aktion in der Liste. */
sealed interface RecipeListEvent {
    data class Removed(val title: String) : RecipeListEvent

    data class MarkedCooked(val title: String) : RecipeListEvent

    data class CookedCleared(val title: String) : RecipeListEvent
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class RecipeListViewModel @Inject constructor(
    observePagedRecipes: ObservePagedRecipesUseCase,
    observeRecipeCount: ObserveRecipeCountUseCase,
    observeTags: ObserveTagsUseCase,
    profileRepository: UserProfileRepository,
    private val deleteRecipe: DeleteRecipeUseCase,
    private val markRecipeCooked: MarkRecipeCookedUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecipeListUiState())
    val uiState: StateFlow<RecipeListUiState> = _uiState.asStateFlow()

    private val _events = Channel<RecipeListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

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

        profileRepository.profile
            .map { it.firstName }
            .distinctUntilChanged()
            .onEach { name -> _uiState.update { it.copy(firstName = name) } }
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

    fun onCookedFilterChange(cooked: Boolean?) {
        _uiState.update { it.copy(cooked = cooked) }
    }

    fun onResetFilter() {
        _uiState.update {
            it.copy(query = "", selectedTags = emptySet(), maxPrepTimeMinutes = null, cooked = null)
        }
    }

    /** Nach links gewischt: erst nachfragen - ein Wisch ist schnell passiert. */
    fun onRemoveRequest(recipe: RecipeSummary) {
        _uiState.update { it.copy(pendingRemoval = recipe, pendingCooked = null) }
    }

    /** Nach rechts gewischt: erst nachfragen. */
    fun onCookedRequest(recipe: RecipeSummary) {
        _uiState.update { it.copy(pendingCooked = recipe, pendingRemoval = null) }
    }

    fun onDialogDismiss() {
        _uiState.update { it.copy(pendingRemoval = null, pendingCooked = null) }
    }

    fun onRemoveConfirm() {
        val recipe = _uiState.value.pendingRemoval ?: return
        _uiState.update { it.copy(pendingRemoval = null) }
        viewModelScope.launch {
            deleteRecipe(recipe.id)
            _events.send(RecipeListEvent.Removed(recipe.title))
        }
    }

    fun onCookedConfirm() {
        val recipe = _uiState.value.pendingCooked ?: return
        _uiState.update { it.copy(pendingCooked = null) }
        viewModelScope.launch {
            markRecipeCooked(recipe.id)
            _events.send(RecipeListEvent.MarkedCooked(recipe.title))
        }
    }

    /** Nimmt den Vermerk "gekocht" wieder weg - fuer den Fall, dass er ein Versehen war. */
    fun onCookedClear() {
        val recipe = _uiState.value.pendingCooked ?: return
        _uiState.update { it.copy(pendingCooked = null) }
        viewModelScope.launch {
            markRecipeCooked(recipe.id, cookedAt = null)
            _events.send(RecipeListEvent.CookedCleared(recipe.title))
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
