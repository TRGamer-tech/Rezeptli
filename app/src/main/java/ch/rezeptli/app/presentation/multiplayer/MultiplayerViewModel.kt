package ch.rezeptli.app.presentation.multiplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.PairingResult
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedSelectionHolder
import ch.rezeptli.app.domain.multiplayer.SharedSession
import ch.rezeptli.app.domain.multiplayer.SharedSessionState
import ch.rezeptli.app.domain.multiplayer.SharedVote
import ch.rezeptli.app.domain.usecase.CloseSharedSessionUseCase
import ch.rezeptli.app.domain.usecase.JoinSharedSessionUseCase
import ch.rezeptli.app.domain.usecase.ObserveSharedSessionUseCase
import ch.rezeptli.app.domain.usecase.SendSharedVotesUseCase
import ch.rezeptli.app.domain.usecase.StartSharedSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Wo in der gemeinsamen Runde man gerade steht. */
enum class MultiplayerStep {
    /** Eroeffnen oder beitreten? */
    START,

    /** Der Code steht auf dem Bildschirm, es fehlt noch jemand. */
    WARTET_AUF_PERSON,

    /** Beide sind da, es wird gewischt. */
    WISCHEN,

    /** Selbst fertig, die andere Person noch nicht. */
    WARTET_AUF_ENTSCHEIDUNGEN,

    /** Alle fertig - die gemeinsamen Treffer stehen fest. */
    TREFFER,
}

data class MultiplayerUiState(
    val step: MultiplayerStep = MultiplayerStep.START,
    val code: String = "",
    val codeInput: String = "",
    val isHost: Boolean = false,
    val isBusy: Boolean = false,
    val error: PairingError? = null,
    val pool: List<SharedRecipe> = emptyList(),
    val decidedCount: Int = 0,
    val participants: Int = 0,
    val matches: List<SharedRecipe> = emptyList(),
) {
    /** Die noch nicht bewerteten Rezepte, als Karten fuer den bestehenden Stapel. */
    val remainingCards: List<RecipeSummary>
        get() = pool.drop(decidedCount).map { shared ->
            RecipeSummary(
                id = shared.recipeId,
                title = shared.title,
                photoUri = shared.imageUrl,
                prepTimeMinutes = shared.prepTimeMinutes,
            )
        }

    val progressLabel: String get() = "${decidedCount.coerceAtMost(pool.size)}/${pool.size}"

    val hasPool: Boolean get() = pool.isNotEmpty()
}

/**
 * Fuehrt durch eine gemeinsame Runde.
 *
 * Die Entscheidungen werden gesammelt und am Ende in einem Rutsch gesendet, nicht
 * einzeln: Das spart Funkverkehr, und wer zwischendurch das Netz verliert, verliert
 * nicht die halbe Runde. Erneutes Senden ist beim Dienst unschaedlich.
 */
@HiltViewModel
class MultiplayerViewModel @Inject constructor(
    private val startSession: StartSharedSessionUseCase,
    private val selectionHolder: SharedSelectionHolder,
    private val joinSession: JoinSharedSessionUseCase,
    private val sendVotes: SendSharedVotesUseCase,
    private val closeSession: CloseSharedSessionUseCase,
    private val observeSession: ObserveSharedSessionUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MultiplayerUiState())
    val uiState: StateFlow<MultiplayerUiState> = _uiState.asStateFlow()

    private val votes = mutableListOf<SharedVote>()
    private var pollJob: Job? = null

    fun onCodeInputChange(value: String) {
        _uiState.update { it.copy(codeInput = value.uppercase().take(MAX_CODE_LENGTH), error = null) }
    }

    /** Eroeffnet eine Runde aus der eigenen Sammlung. */
    fun onHost(filter: RecipeFilter) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, error = null) }

        viewModelScope.launch {
            // Kommt die Runde aus einem Wischstapel, steht die Auswahl schon fest.
            // Sonst wird aus der eigenen Sammlung geteilt.
            val auswahl = selectionHolder.take()
            val result = if (auswahl.isNotEmpty()) startSession(auswahl) else startSession(filter)

            when (result) {
                is PairingResult.Success -> {
                    applySession(result.value, isHost = true)
                    _uiState.update { it.copy(step = MultiplayerStep.WARTET_AUF_PERSON) }
                    watch(result.value.code)
                }

                is PairingResult.Failure -> fail(result.error)
            }
        }
    }

    fun onJoin() {
        val code = _uiState.value.codeInput
        if (code.isBlank() || _uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, error = null) }

        viewModelScope.launch {
            when (val result = joinSession(code)) {
                is PairingResult.Success -> {
                    applySession(result.value, isHost = false)
                    _uiState.update { it.copy(step = MultiplayerStep.WISCHEN) }
                    watch(result.value.code)
                }

                is PairingResult.Failure -> fail(result.error)
            }
        }
    }

    /** Der Gastgeber wartet nicht ewig - sobald jemand da ist, geht es los. */
    fun onStartSwiping() {
        _uiState.update { it.copy(step = MultiplayerStep.WISCHEN) }
    }

    fun onSwiped(recipeId: Long, liked: Boolean) {
        votes += SharedVote(recipeId = recipeId, liked = liked)
        _uiState.update { it.copy(decidedCount = it.decidedCount + 1) }

        if (_uiState.value.decidedCount >= _uiState.value.pool.size) finishSwiping()
    }

    private fun finishSwiping() {
        val code = _uiState.value.code
        _uiState.update { it.copy(step = MultiplayerStep.WARTET_AUF_ENTSCHEIDUNGEN) }

        viewModelScope.launch {
            when (val result = sendVotes(code, votes.toList(), finished = true)) {
                is PairingResult.Success -> watch(code)
                is PairingResult.Failure -> fail(result.error)
            }
        }
    }

    /** Beendet die Runde. Der Gastgeber loescht sie dabei beim Dienst. */
    fun onLeave() {
        pollJob?.cancel()
        val state = _uiState.value
        if (state.isHost && state.code.isNotBlank()) {
            viewModelScope.launch { closeSession(state.code) }
        }
        votes.clear()
        _uiState.value = MultiplayerUiState()
    }

    fun onDismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun watch(code: String) {
        pollJob?.cancel()
        pollJob = observeSession(code)
            .onEach { state -> applyState(state) }
            .launchIn(viewModelScope)
    }

    private fun applySession(session: SharedSession, isHost: Boolean) {
        votes.clear()
        _uiState.update {
            it.copy(
                code = session.code,
                isHost = isHost,
                pool = session.recipes,
                decidedCount = 0,
                isBusy = false,
                error = null,
            )
        }
    }

    private fun applyState(state: SharedSessionState) {
        _uiState.update { current ->
            current.copy(
                participants = state.participants,
                matches = state.matches,
                step = when {
                    state.allFinished -> MultiplayerStep.TREFFER
                    // Sobald jemand beigetreten ist, darf der Gastgeber loswischen.
                    current.step == MultiplayerStep.WARTET_AUF_PERSON && state.participants >= 2 ->
                        MultiplayerStep.WISCHEN

                    else -> current.step
                },
            )
        }
    }

    private fun fail(error: PairingError) {
        _uiState.update { it.copy(isBusy = false, error = error) }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val MAX_CODE_LENGTH = 8
    }
}
