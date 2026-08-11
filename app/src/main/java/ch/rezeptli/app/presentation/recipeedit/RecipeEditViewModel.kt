package ch.rezeptli.app.presentation.recipeedit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.data.local.PhotoStorage
import ch.rezeptli.app.domain.usecase.ObserveRecipeUseCase
import ch.rezeptli.app.domain.usecase.RecipeValidationError
import ch.rezeptli.app.domain.usecase.SaveRecipeResult
import ch.rezeptli.app.domain.usecase.SaveRecipeUseCase
import ch.rezeptli.app.presentation.common.form.IngredientDraft
import ch.rezeptli.app.presentation.common.form.RecipeFormState
import ch.rezeptli.app.presentation.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecipeEditUiState(
    val form: RecipeFormState = RecipeFormState(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isNewRecipe: Boolean = true,
    val isDiscardDialogVisible: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
)

sealed interface RecipeEditEvent {
    data class Saved(val recipeId: Long) : RecipeEditEvent

    data object Discarded : RecipeEditEvent
}

/**
 * Bearbeitet ein bestehendes Rezept oder legt ein neues an.
 *
 * Das Rezept wird einmalig geladen und danach nur noch lokal im Formular gehalten -
 * ein laufendes Flow-Abo wuerde die Eingaben der Nutzerin ueberschreiben.
 */
@HiltViewModel
class RecipeEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeRecipe: ObserveRecipeUseCase,
    private val saveRecipe: SaveRecipeUseCase,
    private val photoStorage: PhotoStorage,
) : ViewModel() {
    private val recipeId: Long = savedStateHandle.get<Long>(Destinations.ARG_RECIPE_ID) ?: 0L

    private val _uiState = MutableStateFlow(
        RecipeEditUiState(isLoading = recipeId != 0L, isNewRecipe = recipeId == 0L),
    )
    val uiState: StateFlow<RecipeEditUiState> = _uiState.asStateFlow()

    private val _events = Channel<RecipeEditEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        if (recipeId != 0L) {
            viewModelScope.launch {
                val recipe = observeRecipe(recipeId).first()
                _uiState.update { state ->
                    if (recipe == null) {
                        state.copy(isLoading = false)
                    } else {
                        state.copy(form = RecipeFormState.from(recipe), isLoading = false)
                    }
                }
            }
        } else {
            // Ein neues Rezept startet mit einer leeren Zutatenzeile, damit sofort
            // klar ist, wo Zutaten hingehoeren.
            _uiState.update { it.copy(form = it.form.withNewIngredient()) }
        }
    }

    /** Uebernimmt einen Import-Vorschlag als Ausgangspunkt fuer die Bearbeitung. */
    fun applyForm(form: RecipeFormState) {
        _uiState.update { it.copy(form = form, hasUnsavedChanges = true) }
    }

    fun onTitleChange(title: String) = updateForm { it.copy(title = title, titleError = false) }

    /**
     * Wird der Zubereitungstext bearbeitet, verfaellt eine mitgebrachte Gliederung.
     * Sie gehoerte zum alten Text; beim Speichern wird der neue frisch aufgeteilt.
     */
    fun onInstructionsChange(instructions: String) = updateForm {
        it.copy(instructions = instructions, importedSteps = emptyList())
    }

    fun onPrepTimeChange(text: String) = updateForm {
        it.copy(prepTimeText = text.filter { char -> char.isDigit() }, prepTimeError = false)
    }

    fun onTagInputChange(text: String) = updateForm { it.copy(tagInput = text) }

    fun onTagAdd() = updateForm { it.withTagAdded(it.tagInput) }

    fun onTagRemove(tag: String) = updateForm { it.withTagRemoved(tag) }

    fun onIngredientChange(index: Int, draft: IngredientDraft) = updateForm {
        it.withIngredientAt(index) { _ -> draft.copy(needsReview = false) }
    }

    fun onIngredientRemove(index: Int) = updateForm { it.withoutIngredientAt(index) }

    fun onIngredientAdd() = updateForm { it.withNewIngredient() }

    fun onPhotoSelected(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val stored = photoStorage.savePhoto(uri) ?: return@launch
            val previous = _uiState.value.form.photoUri
            updateForm { it.copy(photoUri = stored) }
            photoStorage.deletePhoto(previous)
        }
    }

    fun onPhotoRemove() {
        val previous = _uiState.value.form.photoUri
        updateForm { it.copy(photoUri = null) }
        viewModelScope.launch { photoStorage.deletePhoto(previous) }
    }

    fun onSave() {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = saveRecipe(_uiState.value.form.toRecipe())) {
                is SaveRecipeResult.Saved -> {
                    _uiState.update { it.copy(isSaving = false, hasUnsavedChanges = false) }
                    _events.send(RecipeEditEvent.Saved(result.recipeId))
                }

                is SaveRecipeResult.Invalid -> _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        form = state.form.copy(
                            titleError = RecipeValidationError.TITLE_BLANK in result.errors,
                            prepTimeError = RecipeValidationError.PREP_TIME_INVALID in result.errors,
                        ),
                    )
                }
            }
        }
    }

    fun onBackRequest() {
        if (_uiState.value.hasUnsavedChanges) {
            _uiState.update { it.copy(isDiscardDialogVisible = true) }
        } else {
            viewModelScope.launch { _events.send(RecipeEditEvent.Discarded) }
        }
    }

    fun onDiscardDismiss() {
        _uiState.update { it.copy(isDiscardDialogVisible = false) }
    }

    fun onDiscardConfirm() {
        _uiState.update { it.copy(isDiscardDialogVisible = false) }
        viewModelScope.launch { _events.send(RecipeEditEvent.Discarded) }
    }

    private fun updateForm(transform: (RecipeFormState) -> RecipeFormState) {
        _uiState.update { state ->
            state.copy(form = transform(state.form), hasUnsavedChanges = true)
        }
    }
}
