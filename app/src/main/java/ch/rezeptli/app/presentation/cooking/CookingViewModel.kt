package ch.rezeptli.app.presentation.cooking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.steps.RecipeStep
import ch.rezeptli.app.domain.steps.StepIngredientMatcher
import ch.rezeptli.app.domain.timer.CookingTimer
import ch.rezeptli.app.domain.timer.TimerState
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

/** Was nach der Rueckfrage "Timer laeuft noch" geschehen soll. */
enum class CookingExit {
    /** Den Kochmodus nur schliessen. */
    CLOSE,

    /** Fertig gekocht: schliessen und das Rezept als gekocht vermerken. */
    FINISH,
}

data class CookingUiState(
    val recipe: Recipe? = null,
    val stepIndex: Int = 0,
    val ingredientsByStep: Map<Int, List<Ingredient>> = emptyMap(),
    val timer: TimerState? = null,
    /** Minuten, die vor dem Start von Hand verstellt wurden - je Schritt. */
    val plannedMinutes: Map<Int, Int> = emptyMap(),
    /** Gesetzt, solange gefragt wird, ob der laufende Timer wirklich verworfen werden soll. */
    val pendingExit: CookingExit? = null,
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

    /** Wie lange der Timer eines Schritts laufen wuerde: der Vorschlag aus dem Text - oder was eingestellt wurde. */
    fun minutesFor(step: RecipeStep): Int? = plannedMinutes[step.position] ?: step.timerMinutes

    /** Der Timer, wenn er zum Schritt gehoert, der gerade zu sehen ist. */
    val timerForCurrentStep: TimerState?
        get() = timer?.takeIf { it.stepPosition == currentStep?.position }

    /**
     * Der Timer, wenn er zu einem anderen Schritt gehoert. Er bleibt beim Blaettern sichtbar:
     * Wer weiterliest, waehrend die Sauce koechelt, soll sehen, wie lange noch - und das
     * Klingeln abstellen koennen, ohne den Schritt zu suchen.
     */
    val timerElsewhere: TimerState?
        get() = timer?.takeIf { it.stepPosition != currentStep?.position }

    /** Die Nummer des Schritts, zu dem der Timer gehoert - fuer "Schritt 3". */
    val timerStepNumber: Int?
        get() = timer
            ?.let { running -> steps.indexOfFirst { it.position == running.stepPosition } }
            ?.takeIf { it >= 0 }
            ?.plus(1)
}

sealed interface CookingEvent {
    /** Der Kochmodus ist zu Ende - zurueck zum Rezept. */
    data object Finished : CookingEvent
}

/**
 * Fuehrt Schritt fuer Schritt durch ein Rezept.
 *
 * Der Timer selbst gehoert nicht dem Bildschirm, sondern der App ([CookingTimer]): Er laeuft
 * weiter und klingelt, auch wenn das Telefon gesperrt wird oder kurz eine andere App offen
 * ist. Mit dem Kochmodus endet er aber - ein Timer, den man nirgends mehr sieht, waere eine
 * Falle. Deshalb fragt der Kochmodus nach, bevor er einen laufenden Timer verwirft.
 */
@HiltViewModel
class CookingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeRecipe: ObserveRecipeUseCase,
    private val markCooked: MarkRecipeCookedUseCase,
    private val ingredientMatcher: StepIngredientMatcher,
    private val cookingTimer: CookingTimer,
) : ViewModel() {
    private val recipeId: Long = savedStateHandle[Destinations.ARG_RECIPE_ID] ?: 0L

    private val _uiState = MutableStateFlow(CookingUiState())
    val uiState: StateFlow<CookingUiState> = _uiState.asStateFlow()

    private val _events = Channel<CookingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

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

        cookingTimer.state
            // Ein Timer aus einem anderen Rezept geht diesen Kochmodus nichts an.
            .onEach { timer -> _uiState.update { it.copy(timer = timer?.takeIf { own -> own.recipeId == recipeId }) } }
            .launchIn(viewModelScope)
    }

    fun onNextStep() {
        if (!_uiState.value.hasNext) return
        _uiState.update { it.copy(stepIndex = it.stepIndex + 1) }
    }

    fun onPreviousStep() {
        if (!_uiState.value.hasPrevious) return
        _uiState.update { it.copy(stepIndex = it.stepIndex - 1) }
    }

    /** Blaettert zu dem Schritt, zu dem der laufende Timer gehoert. */
    fun onShowTimerStep() {
        val state = _uiState.value
        val index = state.steps.indexOfFirst { it.position == state.timer?.stepPosition }
        if (index >= 0) _uiState.update { it.copy(stepIndex = index) }
    }

    /** Eine Minute mehr oder weniger, bevor der Timer gestartet ist ([delta] = +1 oder -1). */
    fun onPlannedMinutesChange(delta: Int) {
        _uiState.update { state ->
            val step = state.currentStep ?: return
            val minutes = state.minutesFor(step) ?: return
            val changed = (minutes + delta).coerceIn(1, CookingTimer.MAX_MINUTES)
            state.copy(plannedMinutes = state.plannedMinutes + (step.position to changed))
        }
    }

    fun onStartTimer() {
        val state = _uiState.value
        val step = state.currentStep ?: return
        val minutes = state.minutesFor(step) ?: return

        cookingTimer.start(
            recipeId = recipeId,
            stepPosition = step.position,
            title = state.recipe?.title.orEmpty(),
            minutes = minutes,
        )
    }

    /** Eine Minute mehr oder weniger, waehrend der Timer laeuft ([delta] = +1 oder -1). */
    fun onAdjustTimer(delta: Int) {
        if (_uiState.value.timer != null) cookingTimer.adjust(delta)
    }

    fun onStopTimer() {
        cookingTimer.stop(recipeId)
    }

    /** Schliessen ueber das Kreuz oder die Zurueck-Taste. */
    fun onCloseRequest() = leave(CookingExit.CLOSE)

    /** Beendet den Kochmodus und vermerkt, dass das Rezept heute gekocht wurde. */
    fun onFinish() = leave(CookingExit.FINISH)

    /** Die Rueckfrage ist beantwortet: Der Timer wird verworfen, der Kochmodus endet. */
    fun onExitConfirm() {
        val exit = _uiState.value.pendingExit ?: return
        _uiState.update { it.copy(pendingExit = null) }
        perform(exit)
    }

    fun onExitDismiss() {
        _uiState.update { it.copy(pendingExit = null) }
    }

    private fun leave(exit: CookingExit) {
        // Ein abgelaufener Timer hat seinen Dienst getan - nur ein laufender braucht die Rueckfrage.
        if (_uiState.value.timer?.isFinished == false) {
            _uiState.update { it.copy(pendingExit = exit) }
        } else {
            perform(exit)
        }
    }

    private fun perform(exit: CookingExit) {
        cookingTimer.stop(recipeId)
        viewModelScope.launch {
            if (exit == CookingExit.FINISH) _uiState.value.recipe?.let { markCooked(it.id) }
            _events.send(CookingEvent.Finished)
        }
    }

    override fun onCleared() {
        cookingTimer.stop(recipeId)
        super.onCleared()
    }
}
