package ch.rezeptli.app.presentation.multiplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.deck.CuratedDeckBuilder
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.PairingResult
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedSelectionHolder
import ch.rezeptli.app.domain.multiplayer.SharedSession
import ch.rezeptli.app.domain.multiplayer.SharedSessionState
import ch.rezeptli.app.domain.multiplayer.SharedVote
import ch.rezeptli.app.domain.usecase.AddRecipesToShoppingListUseCase
import ch.rezeptli.app.domain.usecase.BuildSwipeDeckUseCase
import ch.rezeptli.app.domain.usecase.CloseSharedSessionUseCase
import ch.rezeptli.app.domain.usecase.JoinSharedSessionUseCase
import ch.rezeptli.app.domain.usecase.LoadWebRecipeUseCase
import ch.rezeptli.app.domain.usecase.ObserveSharedSessionUseCase
import ch.rezeptli.app.domain.usecase.SaveRecipeResult
import ch.rezeptli.app.domain.usecase.SaveRecipeUseCase
import ch.rezeptli.app.domain.usecase.SendSharedVotesUseCase
import ch.rezeptli.app.domain.usecase.StartSharedSessionUseCase
import ch.rezeptli.app.domain.usecase.WebImportOutcome
import ch.rezeptli.app.presentation.deck.DEFAULT_TARGET
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

/** Wo in einer gemeinsamen Runde man gerade steht. */
enum class MultiplayerStep {
    /** Wie viele Gerichte sollen es werden? Bestimmt, wie gross der eigene Vorschlag ist. */
    ANZAHL,

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
    val step: MultiplayerStep = MultiplayerStep.ANZAHL,
    val target: Int = DEFAULT_TARGET,
    val code: String = "",
    val codeInput: String = "",
    val isHost: Boolean = false,
    val isBusy: Boolean = false,
    val error: PairingError? = null,
    val pool: List<SharedRecipe> = emptyList(),
    val decidedCount: Int = 0,
    val participants: Int = 0,
    val matches: List<SharedRecipe> = emptyList(),
    /** Laeuft gerade das Uebernehmen der Treffer? */
    val isKeeping: Boolean = false,
    /** Wie viele Treffer schon in der Sammlung sind. */
    val keptRecipes: Int = 0,
    /** Wie viele Zutaten dabei auf der Einkaufsliste gelandet sind, sobald fertig. */
    val addedItems: Int? = null,
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
 * Beide Seiten bringen einen eigenen, frisch aus dem Verzeichnis gezogenen Vorschlag
 * mit - denselben Weg wie der Alleingang, nur zu zweit. Der Dienst mischt beide Listen
 * zu einem gemeinsamen Topf, ueber den dann beide abstimmen. Das war vorher anders: Nur
 * der Gastgeber brachte Rezepte mit, die andere Person stimmte bloss darueber ab. Eine
 * Ausnahme bleibt: Kommt die Runde aus einer eigenen Wischrunde (siehe [SharedSelectionHolder]),
 * steht die Auswahl der gastgebenden Person schon fest, und die Frage nach der Anzahl
 * entfaellt fuer sie - sie wurde ja gerade erst beantwortet.
 *
 * Die Entscheidungen werden gesammelt und am Ende in einem Rutsch gesendet, nicht
 * einzeln: Das spart Funkverkehr, und wer zwischendurch das Netz verliert, verliert
 * nicht die halbe Runde. Erneutes Senden ist beim Dienst unschaedlich.
 */
@HiltViewModel
class MultiplayerViewModel @Inject constructor(
    private val startSession: StartSharedSessionUseCase,
    private val selectionHolder: SharedSelectionHolder,
    private val buildDeck: BuildSwipeDeckUseCase,
    private val loadWebRecipe: LoadWebRecipeUseCase,
    private val saveRecipe: SaveRecipeUseCase,
    private val addToShoppingList: AddRecipesToShoppingListUseCase,
    private val joinSession: JoinSharedSessionUseCase,
    private val sendVotes: SendSharedVotesUseCase,
    private val closeSession: CloseSharedSessionUseCase,
    private val observeSession: ObserveSharedSessionUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<MultiplayerUiState> = _uiState.asStateFlow()

    private val votes = mutableListOf<SharedVote>()
    private var pollJob: Job? = null

    /** Kommt die Runde schon aus einer Wischrunde, steht die Anzahl schon fest. */
    private fun initialState(): MultiplayerUiState = if (selectionHolder.peek().isNotEmpty()) {
        MultiplayerUiState(step = MultiplayerStep.START)
    } else {
        MultiplayerUiState()
    }

    fun onTargetChange(value: Int) {
        _uiState.update { it.copy(target = value.coerceAtLeast(1)) }
    }

    fun onContinueFromTarget() {
        _uiState.update { it.copy(step = MultiplayerStep.START) }
    }

    fun onCodeInputChange(value: String) {
        _uiState.update { it.copy(codeInput = value.uppercase().take(MAX_CODE_LENGTH), error = null) }
    }

    /** Eroeffnet eine Runde. */
    fun onHost() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, error = null) }

        viewModelScope.launch {
            // Kommt die Runde aus einem Wischstapel, steht die Auswahl schon fest.
            // Sonst wird frisch aus dem Verzeichnis gezogen - derselbe Weg wie beim
            // Alleingang.
            val auswahl = selectionHolder.take()
            val eigene = auswahl.ifEmpty { eigenenVorschlagZiehen() }
            if (eigene.isEmpty()) {
                fail(PairingError.NO_RECIPES)
                return@launch
            }

            when (val result = startSession(eigene)) {
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
            val eigene = eigenenVorschlagZiehen()
            when (val result = joinSession(code, eigene)) {
                is PairingResult.Success -> {
                    applySession(result.value, isHost = false)
                    _uiState.update { it.copy(step = MultiplayerStep.WISCHEN) }
                    watch(result.value.code)
                }

                is PairingResult.Failure -> fail(result.error)
            }
        }
    }

    /**
     * Zieht einen eigenen Vorschlag aus dem Verzeichnis - genau wie der Wischstapel im
     * Alleingang, nur ohne Karten ohne Bild: Fuer die gemeinsame Runde gibt es kein
     * Nachladen im Hintergrund, also muss das Bild schon da sein.
     */
    private suspend fun eigenenVorschlagZiehen(): List<SharedRecipe> {
        val ziel = CuratedDeckBuilder.deckSizeFor(_uiState.value.target)
        val gezogen = buildDeck(size = ziel * ZIEHUNGS_PUFFER)
            .filter { it.imageUrl != null }
            .take(ziel)

        return gezogen.map { eintrag ->
            SharedRecipe(
                recipeId = eintrag.url.hashCode().toLong(),
                title = eintrag.title,
                sourceUrl = eintrag.url,
                imageUrl = eintrag.imageUrl,
            )
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
        _uiState.value = initialState()
    }

    /**
     * Uebernimmt die gemeinsamen Treffer: in die Sammlung und auf die Einkaufsliste.
     *
     * Bis hierhin waren die Treffer nur Titel auf einem Bildschirm - wer danach
     * einkaufen wollte, musste jedes Rezept von Hand suchen. Rezepte aus einem
     * Wischstapel werden dafuer jetzt geholt und gespeichert; Rezepte aus der eigenen
     * Sammlung sind schon da und wandern direkt auf die Liste.
     */
    fun onKeepMatches() {
        val treffer = _uiState.value.matches
        if (treffer.isEmpty() || _uiState.value.isKeeping) return

        _uiState.update { it.copy(isKeeping = true, keptRecipes = 0, addedItems = null) }

        viewModelScope.launch {
            val kennungen = mutableListOf<Long>()

            treffer.forEach { rezept ->
                val quelle = rezept.sourceUrl
                if (quelle.isNullOrBlank()) {
                    // Stammt aus der eigenen Sammlung - schon gespeichert.
                    kennungen += rezept.recipeId
                } else {
                    when (val geladen = loadWebRecipe(quelle)) {
                        is WebImportOutcome.Loaded ->
                            when (val gespeichert = saveRecipe(geladen.recipe)) {
                                is SaveRecipeResult.Saved -> kennungen += gespeichert.recipeId
                                else -> Unit
                            }

                        is WebImportOutcome.Failed -> Unit
                    }
                }
                _uiState.update { it.copy(keptRecipes = kennungen.size) }
            }

            val zutaten = if (kennungen.isEmpty()) 0 else addToShoppingList(kennungen)
            _uiState.update { it.copy(isKeeping = false, addedItems = zutaten) }
        }
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
            // Der Topf darf nur wachsen, waehrend noch niemand wischt - sonst
            // verschoebe sich, welche Karte hinter welcher Zahl an entschiedenen
            // Karten steckt, mitten in der Runde.
            val topf = if (current.step == MultiplayerStep.WARTET_AUF_PERSON && state.pool.isNotEmpty()) {
                state.pool
            } else {
                current.pool
            }

            current.copy(
                participants = state.participants,
                matches = state.matches,
                pool = topf,
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

        /**
         * Es wird mehr gezogen als gebraucht, weil Karten ohne Bild aussortiert
         * werden - anders als im Alleingang gibt es hier kein Nachladen im
         * Hintergrund, das eine fehlende Karte spaeter ersetzen wuerde.
         */
        const val ZIEHUNGS_PUFFER = 2
    }
}
