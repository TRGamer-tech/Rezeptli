package ch.rezeptli.app.presentation.recipedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.repository.UserProfileRepository
import ch.rezeptli.app.domain.usecase.AddRecipesToShoppingListUseCase
import ch.rezeptli.app.domain.usecase.DeleteRecipeUseCase
import ch.rezeptli.app.domain.usecase.KeepRecipeUseCase
import ch.rezeptli.app.domain.usecase.MarkRecipeCookedUseCase
import ch.rezeptli.app.domain.usecase.ObserveRecipeUseCase
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

data class RecipeDetailUiState(
    val isLoading: Boolean = true,
    val recipe: Recipe? = null,
    val isDeleteDialogVisible: Boolean = false,
    val isCookedDialogVisible: Boolean = false,
    /** Fuer so viele wird gekocht - Einkaufsliste und Bring rechnen die Zutaten darauf um. */
    val persons: Int = 2,
    /** Laeuft gerade das Uebernehmen der Zutaten auf die Einkaufsliste? */
    val isAddingToList: Boolean = false,
)

/** Einmalige Ereignisse, die die UI ausloest - Navigation und kurze Rueckmeldungen. */
sealed interface RecipeDetailEvent {
    data object Deleted : RecipeDetailEvent

    data object MarkedAsCooked : RecipeDetailEvent

    data object CookedCleared : RecipeDetailEvent

    /** Das Rezept gehoert jetzt zur Sammlung. */
    data object Kept : RecipeDetailEvent

    /** Die Zutaten stehen auf der Einkaufsliste - [openItems] Posten sind dort noch offen. */
    data class AddedToShoppingList(val persons: Int) : RecipeDetailEvent
}

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeRecipe: ObserveRecipeUseCase,
    private val deleteRecipe: DeleteRecipeUseCase,
    private val markRecipeCooked: MarkRecipeCookedUseCase,
    private val keepRecipe: KeepRecipeUseCase,
    private val addToShoppingList: AddRecipesToShoppingListUseCase,
    profileRepository: UserProfileRepository,
) : ViewModel() {
    private val recipeId: Long = savedStateHandle.get<Long>(Destinations.ARG_RECIPE_ID) ?: 0L

    private val _uiState = MutableStateFlow(RecipeDetailUiState())
    val uiState: StateFlow<RecipeDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<RecipeDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        observeRecipe(recipeId)
            .onEach { recipe -> _uiState.update { it.copy(recipe = recipe, isLoading = false) } }
            .launchIn(viewModelScope)
        profileRepository.profile
            .onEach { profile -> _uiState.update { it.copy(persons = profile.persons) } }
            .launchIn(viewModelScope)
    }

    fun onDeleteRequest() {
        _uiState.update { it.copy(isDeleteDialogVisible = true) }
    }

    fun onDeleteDismiss() {
        _uiState.update { it.copy(isDeleteDialogVisible = false) }
    }

    fun onDeleteConfirm() {
        viewModelScope.launch {
            deleteRecipe(recipeId)
            _uiState.update { it.copy(isDeleteDialogVisible = false) }
            _events.send(RecipeDetailEvent.Deleted)
        }
    }

    fun onCookedRequest() {
        _uiState.update { it.copy(isCookedDialogVisible = true) }
    }

    fun onCookedDismiss() {
        _uiState.update { it.copy(isCookedDialogVisible = false) }
    }

    fun onCookedConfirm() {
        _uiState.update { it.copy(isCookedDialogVisible = false) }
        viewModelScope.launch {
            markRecipeCooked(recipeId)
            _events.send(RecipeDetailEvent.MarkedAsCooked)
        }
    }

    fun onCookedClear() {
        _uiState.update { it.copy(isCookedDialogVisible = false) }
        viewModelScope.launch {
            markRecipeCooked(recipeId, cookedAt = null)
            _events.send(RecipeDetailEvent.CookedCleared)
        }
    }

    /** Ein Rezept aus dem Wochenplan wird bewusst in die Sammlung genommen. */
    fun onKeep() {
        viewModelScope.launch {
            keepRecipe(recipeId)
            _events.send(RecipeDetailEvent.Kept)
        }
    }

    /**
     * Legt die Zutaten auf die Einkaufsliste der App - umgerechnet auf den eigenen Haushalt,
     * wenn das Rezept sagt, fuer wie viele es gedacht ist.
     */
    fun onAddToShoppingList() {
        if (_uiState.value.isAddingToList) return
        val persons = _uiState.value.persons
        _uiState.update { it.copy(isAddingToList = true) }
        viewModelScope.launch {
            addToShoppingList(listOf(recipeId), persons = persons)
            _uiState.update { it.copy(isAddingToList = false) }
            _events.send(RecipeDetailEvent.AddedToShoppingList(persons))
        }
    }
}
