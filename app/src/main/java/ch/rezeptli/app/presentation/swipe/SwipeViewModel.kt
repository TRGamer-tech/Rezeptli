package ch.rezeptli.app.presentation.swipe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.repository.RecipeRepository
import ch.rezeptli.app.domain.repository.SwipeSessionRepository
import ch.rezeptli.app.domain.swipe.SwipeMatcher
import ch.rezeptli.app.domain.usecase.FinishSwipeSessionUseCase
import ch.rezeptli.app.domain.usecase.RecordSwipeUseCase
import ch.rezeptli.app.domain.usecase.StartSwipeSessionUseCase
import ch.rezeptli.app.domain.usecase.UndoLastSwipeUseCase
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

data class SwipeUiState(
    val isLoading: Boolean = true,
    val sessionId: Long = 0L,
    val cards: List<RecipeSummary> = emptyList(),
    val decidedCount: Int = 0,
    val totalCount: Int = 0,
    val canUndo: Boolean = false,
    val isFinished: Boolean = false,
    val isExitDialogVisible: Boolean = false,
) {
    val topCard: RecipeSummary? get() = cards.firstOrNull()
}

sealed interface SwipeEvent {
    data class ShowResults(val sessionId: Long) : SwipeEvent

    data object UndoDone : SwipeEvent

    data object Exit : SwipeEvent
}

/**
 * Steuert eine laufende Swipe-Session.
 *
 * Aus der Datenbank kommt beim Start nur die Liste der Rezept-IDs. Die Karteninhalte
 * werden in einem kleinen Fenster nachgeladen, damit auch eine Sammlung mit hunderten
 * Rezepten sofort startet und fluessig bleibt.
 *
 * Die Session-ID liegt im [SavedStateHandle]: Ueberlebt der Prozess nicht, wird beim
 * naechsten Start dieselbe Session fortgesetzt statt eine neue begonnen.
 */
@HiltViewModel
class SwipeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val startSwipeSession: StartSwipeSessionUseCase,
    private val recordSwipe: RecordSwipeUseCase,
    private val undoLastSwipe: UndoLastSwipeUseCase,
    private val finishSwipeSession: FinishSwipeSessionUseCase,
    private val recipeRepository: RecipeRepository,
    private val sessionRepository: SwipeSessionRepository,
    private val matcher: SwipeMatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SwipeUiState())
    val uiState: StateFlow<SwipeUiState> = _uiState.asStateFlow()

    private val _events = Channel<SwipeEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Noch offene Rezepte in Swipe-Reihenfolge. */
    private var pendingIds: List<Long> = emptyList()

    /** Bereits geladene Karten, damit dieselbe Karte nicht mehrfach geladen wird. */
    private val cardCache = mutableMapOf<Long, RecipeSummary>()

    init {
        viewModelScope.launch {
            val existingSessionId: Long? = savedStateHandle[KEY_SESSION_ID]
            if (existingSessionId != null) {
                resumeSession(existingSessionId)
            } else {
                startNewSession()
            }
        }
    }

    private suspend fun startNewSession() {
        val filter = Destinations.filterFrom(
            query = savedStateHandle[Destinations.ARG_QUERY],
            tags = savedStateHandle[Destinations.ARG_TAGS],
            maxPrepTime = savedStateHandle[Destinations.ARG_MAX_PREP_TIME],
        )
        val started = startSwipeSession(filter)
        savedStateHandle[KEY_SESSION_ID] = started.sessionId
        savedStateHandle[KEY_RECIPE_IDS] = started.recipeIds.toLongArray()

        pendingIds = started.recipeIds
        _uiState.update {
            it.copy(
                sessionId = started.sessionId,
                totalCount = started.recipeIds.size,
                decidedCount = 0,
                isFinished = started.recipeIds.isEmpty(),
                isLoading = false,
            )
        }
        refreshCards()
    }

    private suspend fun resumeSession(sessionId: Long) {
        val allIds = savedStateHandle.get<LongArray>(KEY_RECIPE_IDS)?.toList() ?: emptyList()
        val decisions = sessionRepository.getDecisions(sessionId)
        pendingIds = matcher.remainingRecipeIds(allIds, decisions)

        _uiState.update {
            it.copy(
                sessionId = sessionId,
                totalCount = allIds.size,
                decidedCount = allIds.size - pendingIds.size,
                canUndo = decisions.isNotEmpty(),
                isFinished = pendingIds.isEmpty(),
                isLoading = false,
            )
        }
        refreshCards()
    }

    fun onSwipe(recipeId: Long, liked: Boolean) {
        viewModelScope.launch {
            val sessionId = _uiState.value.sessionId
            if (sessionId == 0L) return@launch

            recordSwipe(sessionId = sessionId, recipeId = recipeId, liked = liked)
            pendingIds = pendingIds.filterNot { it == recipeId }

            _uiState.update {
                it.copy(
                    decidedCount = it.totalCount - pendingIds.size,
                    canUndo = true,
                    isFinished = pendingIds.isEmpty(),
                )
            }
            refreshCards()

            if (pendingIds.isEmpty()) {
                finishSwipeSession(sessionId)
            }
        }
    }

    fun onUndo() {
        viewModelScope.launch {
            val sessionId = _uiState.value.sessionId
            val recipeId = undoLastSwipe(sessionId) ?: return@launch

            pendingIds = listOf(recipeId) + pendingIds.filterNot { it == recipeId }
            val remainingDecisions = sessionRepository.getDecisions(sessionId)

            _uiState.update {
                it.copy(
                    decidedCount = it.totalCount - pendingIds.size,
                    canUndo = remainingDecisions.isNotEmpty(),
                    isFinished = false,
                )
            }
            refreshCards()
            _events.send(SwipeEvent.UndoDone)
        }
    }

    fun onShowResults() {
        viewModelScope.launch {
            val sessionId = _uiState.value.sessionId
            finishSwipeSession(sessionId)
            _events.send(SwipeEvent.ShowResults(sessionId))
        }
    }

    fun onExitRequest() {
        if (_uiState.value.decidedCount == 0) {
            viewModelScope.launch { _events.send(SwipeEvent.Exit) }
        } else {
            _uiState.update { it.copy(isExitDialogVisible = true) }
        }
    }

    fun onExitDismiss() {
        _uiState.update { it.copy(isExitDialogVisible = false) }
    }

    fun onExitConfirm() {
        _uiState.update { it.copy(isExitDialogVisible = false) }
        viewModelScope.launch {
            finishSwipeSession(_uiState.value.sessionId)
            _events.send(SwipeEvent.Exit)
        }
    }

    /** Haelt das Fenster der naechsten Karten gefuellt. */
    private suspend fun refreshCards() {
        val window = pendingIds.take(CARD_WINDOW)
        val missing = window.filterNot { cardCache.containsKey(it) }
        if (missing.isNotEmpty()) {
            recipeRepository.getSummaries(missing).forEach { cardCache[it.id] = it }
        }
        _uiState.update { state ->
            state.copy(cards = window.mapNotNull { cardCache[it] })
        }
    }

    private companion object {
        /** Sichtbare Karte plus zwei angedeutete Karten dahinter. */
        const val CARD_WINDOW = 3
        const val KEY_SESSION_ID = "activeSessionId"
        const val KEY_RECIPE_IDS = "sessionRecipeIds"
    }
}
