package ch.rezeptli.app.presentation.multiplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.deck.CuratedDeckBuilder
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.multiplayer.ActiveRound
import ch.rezeptli.app.domain.multiplayer.ActiveRoundRepository
import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.PairingResult
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedSelectionHolder
import ch.rezeptli.app.domain.multiplayer.SharedSession
import ch.rezeptli.app.domain.multiplayer.SharedSessionState
import ch.rezeptli.app.domain.multiplayer.SharedVote
import ch.rezeptli.app.domain.plan.AddToPlanUseCase
import ch.rezeptli.app.domain.plan.CheckRecipeCardUseCase
import ch.rezeptli.app.domain.plan.DietaryFilter
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.LanguageRegion
import ch.rezeptli.app.domain.repository.UserProfileRepository
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    /** Laeuft gerade das Uebernehmen in den Wochenplan? */
    val isPlanning: Boolean = false,
    /** Wie viele Treffer im Wochenplan gelandet sind, sobald fertig. */
    val plannedMeals: Int? = null,
    /** Ernaehrung und Allergien fuer diese Runde - vorbelegt aus den Vorlieben. */
    val filter: DietaryFilter = DietaryFilter(),
    /** Ergebnis der Pruefung je Karte: erlaubt oder nicht. Nur bei aktivem Filter gebraucht. */
    val verdicts: Map<Long, Boolean> = emptyMap(),
    /** Eine unterbrochene Runde, die sich fortsetzen laesst. */
    val resumableCode: String? = null,
) {
    /**
     * Die noch nicht bewerteten Rezepte, als Karten fuer den bestehenden Stapel.
     *
     * Bei aktivem Filter nur so weit, wie schon geprueft ist: Eine Karte erscheint erst,
     * wenn feststeht, dass sie passt. Die Reihenfolge bleibt die des gemeinsamen Stapels.
     */
    val remainingCards: List<RecipeSummary>
        get() = pool
            .drop(decidedCount)
            .let { offen -> if (filter.isActive) offen.takeWhile { verdicts[it.recipeId] == true } else offen }
            .map { shared ->
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
    private val addToPlan: AddToPlanUseCase,
    private val joinSession: JoinSharedSessionUseCase,
    private val sendVotes: SendSharedVotesUseCase,
    private val closeSession: CloseSharedSessionUseCase,
    private val observeSession: ObserveSharedSessionUseCase,
    private val activeRounds: ActiveRoundRepository,
    private val profileRepository: UserProfileRepository,
    private val checkCard: CheckRecipeCardUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<MultiplayerUiState> = _uiState.asStateFlow()

    private val votes = mutableListOf<SharedVote>()
    private var pollJob: Job? = null
    private var expiresAt: Long = 0L

    /** Karten, deren Pruefung schon laeuft oder lief. */
    private val pruefungVersucht = mutableSetOf<Long>()
    private val pruefSchranke = Semaphore(PARALLEL_CHECKS)

    init {
        viewModelScope.launch {
            val filter = DietaryFilter.of(profileRepository.profile.first())
            val runde = activeRounds.load()?.takeUnless { it.isExpired(System.currentTimeMillis()) }
            if (runde == null) activeRounds.clear()
            _uiState.update { it.copy(filter = filter, resumableCode = runde?.code) }
        }
    }

    /** Kommt die Runde schon aus einer Wischrunde, steht die Anzahl schon fest. */
    private fun initialState(): MultiplayerUiState = if (selectionHolder.peek().isNotEmpty()) {
        MultiplayerUiState(step = MultiplayerStep.START)
    } else {
        MultiplayerUiState()
    }

    fun onTargetChange(value: Int) {
        _uiState.update { it.copy(target = value.coerceAtLeast(1)) }
    }

    fun onFilterDietToggle(diet: Diet) {
        _uiState.update { state ->
            val diets = if (diet in state.filter.diets) state.filter.diets - diet else state.filter.diets + diet
            state.copy(filter = state.filter.copy(diets = diets))
        }
    }

    fun onFilterIntoleranceToggle(intolerance: Intolerance) {
        _uiState.update { state ->
            val gewaehlt = state.filter.intolerances
            val neu = if (intolerance in gewaehlt) gewaehlt - intolerance else gewaehlt + intolerance
            state.copy(filter = state.filter.copy(intolerances = neu))
        }
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
                    // Ein frisch gezogener Vorschlag ist schon geprueft - so muss er nicht noch
                    // einmal geladen werden. Eine Auswahl aus dem Wischstapel wird beim Wischen geprueft.
                    if (auswahl.isEmpty()) markApproved(eigene)
                    remember()
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
                    markApproved(eigene)
                    remember()
                    _uiState.update { it.copy(step = MultiplayerStep.WISCHEN) }
                    watch(result.value.code)
                    weiterPruefen()
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
        val filter = _uiState.value.filter
        val mitBild = buildDeck(
            size = ziel * ZIEHUNGS_PUFFER * (if (filter.isActive) FILTER_PUFFER else 1),
            onlyRegion = if (filter.isActive) LanguageRegion.DEUTSCH else null,
        ).filter { it.imageUrl != null }

        // Mit Filter wird der eigene Beitrag vor dem Teilen geprueft - sonst kaemen Karten
        // in den gemeinsamen Stapel, die man selbst gar nicht essen kann.
        val gezogen = if (filter.isActive) {
            coroutineScope {
                mitBild
                    .map { eintrag ->
                        async { eintrag to pruefSchranke.withPermit { checkCard(eintrag.url, filter).allowed } }
                    }.awaitAll()
            }.filter { (_, erlaubt) -> erlaubt }.map { it.first }.take(ziel)
        } else {
            mitBild.take(ziel)
        }

        return gezogen.map { eintrag ->
            SharedRecipe(
                recipeId = eintrag.url.hashCode().toLong(),
                title = eintrag.title,
                sourceUrl = eintrag.url,
                imageUrl = eintrag.imageUrl,
            )
        }
    }

    fun onSwiped(recipeId: Long, liked: Boolean) {
        entscheiden(recipeId, liked)
        weiterPruefen()
    }

    private fun entscheiden(recipeId: Long, liked: Boolean) {
        if (_uiState.value.step != MultiplayerStep.WISCHEN) return
        votes += SharedVote(recipeId = recipeId, liked = liked)
        _uiState.update { it.copy(decidedCount = it.decidedCount + 1) }
        remember()

        if (_uiState.value.decidedCount >= _uiState.value.pool.size) finishSwiping()
    }

    /**
     * Prueft die naechsten Karten des gemeinsamen Stapels und ueberspringt, was nicht passt.
     *
     * Beide sehen denselben Stapel in derselben Reihenfolge. Was jemand nicht essen kann,
     * wird auf dessen Geraet still mit "nein" beantwortet - so kann es nie ein Treffer
     * werden, und die Reihenfolge fuer die andere Person bleibt unberuehrt.
     */
    private fun weiterPruefen() {
        val state = _uiState.value
        if (!state.filter.isActive || state.step != MultiplayerStep.WISCHEN) return

        ueberspringen()

        val offen = _uiState.value.pool
            .drop(_uiState.value.decidedCount)
            .filter { it.recipeId !in _uiState.value.verdicts && it.recipeId !in pruefungVersucht }
            .take(PRUEF_VORAUS)
        pruefungVersucht += offen.map { it.recipeId }

        offen.forEach { karte ->
            viewModelScope.launch {
                val url = karte.sourceUrl
                val erlaubt = url != null && pruefSchranke.withPermit { checkCard(url, state.filter).allowed }
                _uiState.update { it.copy(verdicts = it.verdicts + (karte.recipeId to erlaubt)) }
                weiterPruefen()
            }
        }
    }

    /** Beantwortet die naechsten Karten mit "nein", solange sie als unpassend feststehen. */
    private fun ueberspringen() {
        while (true) {
            val state = _uiState.value
            if (state.step != MultiplayerStep.WISCHEN) return
            val naechste = state.pool.getOrNull(state.decidedCount) ?: return
            if (state.verdicts[naechste.recipeId] != false) return
            entscheiden(naechste.recipeId, liked = false)
        }
    }

    private fun markApproved(recipes: List<SharedRecipe>) {
        if (!_uiState.value.filter.isActive) return
        _uiState.update { state -> state.copy(verdicts = state.verdicts + recipes.associate { it.recipeId to true }) }
    }

    /** Merkt sich die Runde auf dem Geraet, damit sie sich nach einem Unterbruch fortsetzen laesst. */
    private fun remember() {
        val state = _uiState.value
        if (state.code.isBlank()) return
        val runde = ActiveRound(code = state.code, isHost = state.isHost, votes = votes.toList(), expiresAt = expiresAt)
        viewModelScope.launch { activeRounds.save(runde) }
    }

    /**
     * Setzt eine unterbrochene Runde fort - mit demselben Stapel und den bisherigen Entscheidungen.
     *
     * Der Dienst kennt das Geraet schon und gibt den eingefrorenen Stapel zurueck, ohne
     * etwas dazuzumischen.
     */
    fun onResume() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, error = null) }

        viewModelScope.launch {
            val runde = activeRounds.load()
            if (runde == null) {
                _uiState.update { it.copy(isBusy = false, resumableCode = null) }
                return@launch
            }

            when (val result = joinSession(runde.code, emptyList())) {
                is PairingResult.Success -> {
                    applySession(result.value, isHost = runde.isHost)
                    val bekannt = result.value.recipes
                        .map { it.recipeId }
                        .toSet()
                    votes += runde.votes.filter { it.recipeId in bekannt }
                    _uiState.update {
                        it.copy(
                            decidedCount = votes.size,
                            resumableCode = null,
                            step = if (runde.isHost) MultiplayerStep.WARTET_AUF_PERSON else MultiplayerStep.WISCHEN,
                        )
                    }
                    watch(result.value.code)
                    if (votes.size >= result.value.recipes.size) finishSwiping() else weiterPruefen()
                }

                is PairingResult.Failure -> {
                    // Abgelaufen oder beendet: Fortsetzen ist nicht mehr moeglich.
                    if (result.error == PairingError.UNKNOWN_CODE || result.error == PairingError.EXPIRED) {
                        activeRounds.clear()
                        _uiState.update { it.copy(resumableCode = null) }
                    }
                    fail(result.error)
                }
            }
        }
    }

    private fun finishSwiping() {
        val code = _uiState.value.code
        if (_uiState.value.step == MultiplayerStep.TREFFER) return
        _uiState.update { it.copy(step = MultiplayerStep.WARTET_AUF_ENTSCHEIDUNGEN) }

        viewModelScope.launch {
            when (val result = sendVotes(code, votes.toList(), finished = true)) {
                is PairingResult.Success -> watch(code)
                is PairingResult.Failure -> fail(result.error)
            }
        }
    }

    /**
     * Verlaesst den Bildschirm. Eine laufende Runde bleibt bestehen und laesst sich
     * fortsetzen; ist sie schon zu Ende, wird sie vergessen.
     */
    fun onLeave() {
        pollJob?.cancel()
        val state = _uiState.value
        if (state.step == MultiplayerStep.TREFFER) {
            viewModelScope.launch { activeRounds.clear() }
        }
        votes.clear()
        _uiState.value = initialState().copy(filter = state.filter, resumableCode = laufenderCode(state))
    }

    /** Beendet die Runde endgueltig. Der Gastgeber loescht sie dabei beim Dienst. */
    fun onEndRound() {
        pollJob?.cancel()
        val state = _uiState.value
        if (state.isHost && state.code.isNotBlank()) {
            viewModelScope.launch { closeSession(state.code) }
        }
        viewModelScope.launch { activeRounds.clear() }
        votes.clear()
        _uiState.value = initialState().copy(filter = state.filter)
    }

    private fun laufenderCode(state: MultiplayerUiState): String? = when (state.step) {
        MultiplayerStep.WARTET_AUF_PERSON,
        MultiplayerStep.WISCHEN,
        MultiplayerStep.WARTET_AUF_ENTSCHEIDUNGEN,
        -> state.code.takeIf { it.isNotBlank() }
        else -> state.resumableCode
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

    /**
     * Legt die gemeinsamen Treffer auf freie Tage des Wochenplans.
     *
     * Das ist der eigentliche Zweck der gemeinsamen Runde: Am Ende steht nicht eine Liste,
     * sondern eine Woche, auf die sich beide geeinigt haben.
     */
    fun onAddToPlan() {
        val treffer = _uiState.value.matches
        if (treffer.isEmpty() || _uiState.value.isPlanning) return

        _uiState.update { it.copy(isPlanning = true, plannedMeals = null) }
        viewModelScope.launch {
            val anzahl = addToPlan(treffer)
            _uiState.update { it.copy(isPlanning = false, plannedMeals = anzahl) }
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
        pruefungVersucht.clear()
        expiresAt = session.expiresAt
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
        val vorher = _uiState.value.step
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
        if (_uiState.value.step == MultiplayerStep.TREFFER) viewModelScope.launch { activeRounds.clear() }
        if (vorher != MultiplayerStep.WISCHEN && _uiState.value.step == MultiplayerStep.WISCHEN) {
            if (_uiState.value.decidedCount >= _uiState.value.pool.size && _uiState.value.pool.isNotEmpty()) {
                finishSwiping()
            } else {
                weiterPruefen()
            }
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

        /** Mit Filter faellt ein Teil heraus - darum wird noch mehr gezogen. */
        const val FILTER_PUFFER = 2

        /** So viele Karten werden im Voraus geprueft. */
        const val PRUEF_VORAUS = 6

        /** So viele Rezeptseiten werden gleichzeitig geladen. */
        const val PARALLEL_CHECKS = 3
    }
}
