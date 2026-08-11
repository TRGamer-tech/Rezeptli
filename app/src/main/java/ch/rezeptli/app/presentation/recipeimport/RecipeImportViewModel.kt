package ch.rezeptli.app.presentation.recipeimport

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.repository.WebImportError
import ch.rezeptli.app.domain.usecase.LoadWebRecipeUseCase
import ch.rezeptli.app.domain.usecase.ParseRecipeTextUseCase
import ch.rezeptli.app.domain.usecase.RecipeValidationError
import ch.rezeptli.app.domain.usecase.SaveRecipeResult
import ch.rezeptli.app.domain.usecase.SaveRecipeUseCase
import ch.rezeptli.app.domain.usecase.WebImportOutcome
import ch.rezeptli.app.presentation.common.form.IngredientDraft
import ch.rezeptli.app.presentation.common.form.RecipeFormState
import ch.rezeptli.app.presentation.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Zustand des Imports.
 *
 * [form] ist `null`, solange nur der Rohtext eingegeben wird. Sobald der Parser gelaufen
 * ist, enthaelt es den Vorschlag - und ab da arbeitet der Import mit demselben Formular
 * wie das normale Bearbeiten.
 */
data class RecipeImportUiState(
    val rawText: String = "",
    val form: RecipeFormState? = null,
    val isSaving: Boolean = false,
    val nothingFound: Boolean = false,
    val isLoadingFromWeb: Boolean = false,
    val webError: WebImportError? = null,
) {
    val isPreviewVisible: Boolean get() = form != null

    val recognisedIngredientCount: Int get() = form?.ingredients?.size ?: 0

    val hasUncertainLines: Boolean get() = form?.hasUncertainIngredients == true

    /** Der eingefuegte Text ist ein Link - dann wird geladen statt geparst. */
    val looksLikeUrl: Boolean
        get() = rawText.trim().let { it.startsWith("http://") || it.startsWith("https://") }
}

sealed interface RecipeImportEvent {
    data class Saved(val recipeId: Long) : RecipeImportEvent
}

@HiltViewModel
class RecipeImportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val parseRecipeText: ParseRecipeTextUseCase,
    private val loadWebRecipe: LoadWebRecipeUseCase,
    private val saveRecipe: SaveRecipeUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecipeImportUiState())
    val uiState: StateFlow<RecipeImportUiState> = _uiState.asStateFlow()

    private val _events = Channel<RecipeImportEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // Aus der Web-Suche oder ueber "Teilen" kommt eine Adresse mit - dann wird
        // direkt geladen, statt erst nach einem Text zu fragen.
        val sharedUrl = savedStateHandle.get<String>(Destinations.ARG_URL).orEmpty()
        if (sharedUrl.isNotBlank()) {
            _uiState.update { it.copy(rawText = sharedUrl) }
            loadFromUrl(sharedUrl)
        }
    }

    fun onTextChange(text: String) {
        _uiState.update { it.copy(rawText = text, nothingFound = false) }
    }

    fun onClearText() {
        _uiState.update { RecipeImportUiState() }
    }

    /**
     * Wertet den eingefuegten Inhalt aus - je nachdem als Link oder als Text.
     *
     * Das Ergebnis wird in beiden Faellen nur angezeigt, nie direkt gespeichert: Erst
     * die Bestaetigung der Nutzerin legt das Rezept an.
     */
    fun onAnalyse() {
        if (_uiState.value.looksLikeUrl) {
            loadFromUrl(_uiState.value.rawText.trim())
        } else {
            onParse()
        }
    }

    private fun loadFromUrl(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFromWeb = true, webError = null, nothingFound = false) }
            when (val outcome = loadWebRecipe(url)) {
                is WebImportOutcome.Loaded -> _uiState.update {
                    it.copy(
                        form = RecipeFormState.from(outcome.recipe),
                        isLoadingFromWeb = false,
                    )
                }

                is WebImportOutcome.Failed -> _uiState.update {
                    it.copy(isLoadingFromWeb = false, webError = outcome.error)
                }
            }
        }
    }

    fun onParse() {
        val parsed = parseRecipeText(_uiState.value.rawText)
        if (parsed.isEmpty) {
            _uiState.update { it.copy(nothingFound = true, form = null) }
            return
        }
        val form = RecipeFormState.fromParsed(parsed)
        _uiState.update {
            it.copy(
                form = if (form.ingredients.isEmpty()) form.withNewIngredient() else form,
                nothingFound = false,
            )
        }
    }

    fun onBackToText() {
        _uiState.update { it.copy(form = null, webError = null) }
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

    /** Eine korrigierte Zeile gilt als geprueft und wird nicht mehr hervorgehoben. */
    fun onIngredientChange(index: Int, draft: IngredientDraft) = updateForm {
        it.withIngredientAt(index) { _ -> draft.copy(needsReview = false) }
    }

    fun onIngredientRemove(index: Int) = updateForm { it.withoutIngredientAt(index) }

    fun onIngredientAdd() = updateForm { it.withNewIngredient() }

    fun onSave() {
        val form = _uiState.value.form ?: return
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = saveRecipe(form.toRecipe())) {
                is SaveRecipeResult.Saved -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(RecipeImportEvent.Saved(result.recipeId))
                }

                is SaveRecipeResult.Invalid -> _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        form = state.form?.copy(
                            titleError = RecipeValidationError.TITLE_BLANK in result.errors,
                            prepTimeError = RecipeValidationError.PREP_TIME_INVALID in result.errors,
                        ),
                    )
                }
            }
        }
    }

    private fun updateForm(transform: (RecipeFormState) -> RecipeFormState) {
        _uiState.update { state ->
            state.copy(form = state.form?.let(transform))
        }
    }
}
