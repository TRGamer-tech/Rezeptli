package ch.rezeptli.app.presentation.recipedetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.usecase.DeleteRecipeUseCase
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
)

/** Einmalige Ereignisse, die die UI ausloest - Navigation und kurze Rueckmeldungen. */
sealed interface RecipeDetailEvent {
    data object Deleted : RecipeDetailEvent

    data object MarkedAsCooked : RecipeDetailEvent
}

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeRecipe: ObserveRecipeUseCase,
    private val deleteRecipe: DeleteRecipeUseCase,
    private val markRecipeCooked: MarkRecipeCookedUseCase,
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

    fun onMarkCooked() {
        viewModelScope.launch {
            markRecipeCooked(recipeId)
            _events.send(RecipeDetailEvent.MarkedAsCooked)
        }
    }
}
