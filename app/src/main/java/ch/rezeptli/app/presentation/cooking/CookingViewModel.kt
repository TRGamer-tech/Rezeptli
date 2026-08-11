package ch.rezeptli.app.presentation.cooking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.steps.RecipeStep
import ch.rezeptli.app.domain.steps.StepIngredientMatcher
import ch.rezeptli.app.domain.usecase.MarkRecipeCookedUseCase
import ch.rezeptli.app.domain.usecase.ObserveRecipeUseCase
import ch.rezeptli.app.presentation.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Der laufende Timer eines Schritts. */
data class TimerState(
    val stepPosition: Int,
    val totalSeconds: Int,
    val remainingSeconds: Int,
) {
    val isFinished: Boolean get() = remainingSeconds <= 0

    val progress: Float
        get() = if (totalSeconds <= 0) 0f else 1f - remainingSeconds.toFloat() / totalSeconds
}

data class CookingUiState(
    val recipe: Recipe? = null,
    val stepIndex: Int = 0,
    val ingredientsByStep: Map<Int, List<Ingredient>> = emptyMap(),
    val timer: TimerState? = null,
    val isLoading: Boolean = true,
) {
    val steps: List<RecipeStep> get() = recipe?.steps.orEmpty()

    val currentStep: RecipeStep? get() = steps.getOrNull(stepIndex)

    val stepCount: Int get() = steps.size

    val stepNumber: Int get() = stepIndex + 1

    val ingredientsForCurrentStep: List<Ingredient>
        get() = currentStep?.let { ingredientsByStep[it.position] }.orEmpty()

    val hasPrevious: Boolean get() = stepIndex > 0

    val hasNext: Boolean get() = stepIndex < steps.lastIndex

    val isLastStep: Boolean get() = steps.isNotEmpty() && stepIndex == steps.lastIndex

    val hasSteps: Boolean get() = steps.isNotEmpty()

    /** Der Timer laeuft nur fuer den Schritt, zu dem er gestartet wurde. */
    val timerForCurrentStep: TimerState?
        get() = timer?.takeIf { it.stepPosition == currentStep?.position }
}

sealed interface CookingEvent {
    /** Fertig gekocht - zurueck zum Rezept. */
    data object Finished : CookingEvent
}

/**
 * Fuehrt Schritt fuer Schritt durch ein Rezept.
 *
 * Der Timer laeuft im ViewModel und nicht im Bildschirm: So ueberlebt er ein Drehen des
 * Geraets. Er laeuft bewusst nur, solange die App offen ist - eine Benachrichtigung
 * waere eine zusaetzliche Berechtigung, und dafuer ist der Kochmodus nicht gedacht.
 */
@HiltViewModel
class CookingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeRecipe: ObserveRecipeUseCase,
    private val markCooked: MarkRecipeCookedUseCase,
    private val ingredientMatcher: StepIngredientMatcher,
) : ViewModel() {
    private val recipeId: Long = savedStateHandle[Destinations.ARG_RECIPE_ID] ?: 0L

    private val _uiState = MutableStateFlow(CookingUiState())
    val uiState: StateFlow<CookingUiState> = _uiState.asStateFlow()

    private val _events = Channel<CookingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var timerJob: Job? = null

    init {
        observeRecipe(recipeId)
            .onEach { recipe ->
                _uiState.update { state ->
                    state.copy(
                        recipe = recipe,
                        isLoading = false,
                        ingredientsByStep = recipe
                            ?.let { ingredientMatcher.byStep(it.steps, it.ingredients) }
                            .orEmpty(),
                        // Nach einer Aenderung am Rezept darf der Schritt nicht ins Leere zeigen.
                        stepIndex = state.stepIndex.coerceIn(
                            0,
                            (recipe?.steps?.lastIndex ?: 0).coerceAtLeast(0),
                        ),
                    )
                }
            }.launchIn(viewModelScope)
    }

    fun onNextStep() {
        if (!_uiState.value.hasNext) return
        _uiState.update { it.copy(stepIndex = it.stepIndex + 1) }
    }

    fun onPreviousStep() {
        if (!_uiState.value.hasPrevious) return
        _uiState.update { it.copy(stepIndex = it.stepIndex - 1) }
    }

    fun onStartTimer() {
        val step = _uiState.value.currentStep ?: return
        val minutes = step.timerMinutes ?: return

        timerJob?.cancel()
        val totalSeconds = minutes * SECONDS_PER_MINUTE
        _uiState.update {
            it.copy(
                timer = TimerState(
                    stepPosition = step.position,
                    totalSeconds = totalSeconds,
                    remainingSeconds = totalSeconds,
                ),
            )
        }

        timerJob = viewModelScope.launch {
            var remaining = totalSeconds
            while (isActive && remaining > 0) {
                delay(TICK_MS)
                remaining -= 1
                _uiState.update { state ->
                    state.copy(timer = state.timer?.copy(remainingSeconds = remaining))
                }
            }
        }
    }

    fun onStopTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(timer = null) }
    }

    /** Beendet den Kochmodus und vermerkt, dass das Rezept heute gekocht wurde. */
    fun onFinish() {
        timerJob?.cancel()
        viewModelScope.launch {
            _uiState.value.recipe?.let { markCooked(it.id) }
            _events.send(CookingEvent.Finished)
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val SECONDS_PER_MINUTE = 60
        const val TICK_MS = 1_000L
    }
}
