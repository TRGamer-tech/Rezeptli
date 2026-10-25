package ch.rezeptli.app.presentation.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.plan.GeneratePlanUseCase
import ch.rezeptli.app.domain.plan.PlanFailure
import ch.rezeptli.app.domain.plan.PlanOutcome
import ch.rezeptli.app.domain.plan.PlanStage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlanBuildUiState(
    /** Welcher der drei Schritte gerade laeuft; davor liegende gelten als erledigt. */
    val stage: PlanStage = PlanStage.FINDING,
    val failure: PlanFailure? = null,
)

sealed interface PlanBuildEvent {
    data object Built : PlanBuildEvent
}

/**
 * Baut den Plan und zeigt dabei, in welchem Schritt er gerade ist.
 *
 * Die drei Schritte sind keine Animation: Sie entsprechen den Abschnitten des
 * Use-Cases und rutschen erst weiter, wenn dort wirklich der naechste beginnt.
 */
@HiltViewModel
class PlanBuildViewModel @Inject constructor(
    private val generatePlan: GeneratePlanUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlanBuildUiState())
    val uiState: StateFlow<PlanBuildUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlanBuildEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        build()
    }

    fun onRetry() = build()

    private fun build() {
        _uiState.value = PlanBuildUiState()
        viewModelScope.launch {
            val outcome = generatePlan { stage -> _uiState.update { it.copy(stage = stage) } }
            when (outcome) {
                is PlanOutcome.Built -> _events.send(PlanBuildEvent.Built)
                is PlanOutcome.Failed -> _uiState.update { it.copy(failure = outcome.reason) }
            }
        }
    }
}
