package ch.rezeptli.app.presentation.multiplayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.multiplayer.ActiveRound
import ch.rezeptli.app.domain.multiplayer.ActiveRoundRepository
import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.PairingResult
import ch.rezeptli.app.domain.multiplayer.RoundEnd
import ch.rezeptli.app.domain.multiplayer.RoundOffer
import ch.rezeptli.app.domain.multiplayer.RoundPhase
import ch.rezeptli.app.domain.multiplayer.RoundPoll
import ch.rezeptli.app.domain.multiplayer.RoundSource
import ch.rezeptli.app.domain.multiplayer.RoundState
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedSelectionHolder
import ch.rezeptli.app.domain.multiplayer.SharedVote
import ch.rezeptli.app.domain.nutrition.RecipeNutrition
import ch.rezeptli.app.domain.plan.AddToPlanUseCase
import ch.rezeptli.app.domain.plan.CourseChoice
import ch.rezeptli.app.domain.plan.DietaryFilter
import ch.rezeptli.app.domain.plan.OverwritePlanUseCase
import ch.rezeptli.app.domain.plan.PlacedRecipe
import ch.rezeptli.app.domain.plan.PlanAddMode
import ch.rezeptli.app.domain.plan.PlanAddition
import ch.rezeptli.app.domain.plan.PlanPlace
import ch.rezeptli.app.domain.plan.ReadRecipeCardUseCase
import ch.rezeptli.app.domain.plan.WeekAssignment
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.ProfileEdits
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.domain.repository.PairingRepository
import ch.rezeptli.app.domain.repository.PairingRepository.Companion.NO_ROUND
import ch.rezeptli.app.domain.repository.RecipeRepository
import ch.rezeptli.app.domain.repository.UserProfileRepository
import ch.rezeptli.app.domain.usecase.AddRecipesToShoppingListUseCase
import ch.rezeptli.app.domain.usecase.BuildRoundContributionUseCase
import ch.rezeptli.app.domain.usecase.FetchMatchesUseCase
import ch.rezeptli.app.domain.usecase.FetchedMatch
import ch.rezeptli.app.domain.usecase.KeepMatchesUseCase
import ch.rezeptli.app.domain.usecase.ObserveRoundUseCase
import ch.rezeptli.app.domain.usecase.ShareOwnMatchesUseCase
import ch.rezeptli.app.presentation.swipe.CardInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Wo in einer gemeinsamen Runde man gerade steht. */
enum class MultiplayerStep {
    /** Eroeffnen oder beitreten - und was man selbst nicht auf dem Tisch will. */
    START,

    /** Der Code steht. Wer eroeffnet hat, legt fest, was gesucht wird; die andere Person sieht es mit. */
    LOBBY,

    /** Beide Geraete steuern Karten bei, der Stapel wird gemischt. */
    SAMMELN,

    WISCHEN,

    /** Selbst durch, die andere Person wischt noch. */
    WARTET,

    /** Der Stapel ist durch, das Ziel nicht erreicht: weitermachen oder aufhoeren? */
    FRAGE,

    /** Die Runde ist zu Ende - die gemeinsamen Treffer stehen fest. */
    TREFFER,
}

data class MultiplayerUiState(
    val step: MultiplayerStep = MultiplayerStep.START,
    val codeInput: String = "",
    val isBusy: Boolean = false,
    val error: PairingError? = null,
    /**
     * Was man selbst in die Runde mitbringt: Ernaehrung, Unvertraeglichkeiten, Abneigungen.
     * Vorbelegt aus den Vorlieben und nur fuer diese Runde aenderbar - die Vorlieben selbst
     * bleiben, wie sie sind.
     */
    val own: UserProfile = UserProfile.EMPTY,
    /** Karten aus einer eigenen Wischrunde, die mit in den Stapel kommen. */
    val selectionCount: Int = 0,
    /** Eine unterbrochene Runde, die sich fortsetzen laesst. */
    val resumableCode: String? = null,
    val code: String = "",
    val isHost: Boolean = false,
    val participants: Int = 1,
    /** Wie viele gemeinsame Gerichte gesucht werden. Legt fest, wer eroeffnet hat. */
    val target: Int = RoundState.DEFAULT_TARGET,
    val course: CourseChoice = CourseChoice.ESSEN,
    val source: RoundSource = RoundSource.GESPEICHERT,
    /** Was fuer diese Runde gilt - die Angaben beider, vereinigt. */
    val restrictions: DietaryFilter = DietaryFilter(),
    /** Der gemeinsame Stapel der laufenden Runde - fuer beide derselbe, in derselben Reihenfolge. */
    val stack: List<SharedRecipe> = emptyList(),
    /** Die Karten des Stapels, die man selbst schon entschieden hat. */
    val decided: Set<Long> = emptySet(),
    val canUndo: Boolean = false,
    val matchCount: Int = 0,
    /** Hat die andere Person den Stapel schon durch? */
    val partnerDone: Boolean = false,
    /** Die eigenen Karten sind abgegeben; es fehlen noch die der anderen Person. */
    val hasContributed: Boolean = false,
    val end: RoundEnd? = null,
    val matches: List<SharedRecipe> = emptyList(),
    /** Der Dienst ist gerade nicht erreichbar - es wird weiter versucht. */
    val isOffline: Boolean = false,
    /** Die Angaben zur angetippten Karte, solange das Blatt offen ist. */
    val info: CardInfo? = null,
    /**
     * Wie viele Sekunden der Dienst noch auf fehlende Karten wartet - `null`, solange keine
     * Frist laeuft. Danach beginnt die Runde mit dem, was da ist.
     */
    val waitSeconds: Int? = null,
    /** Was mit den Treffern geschehen ist und gerade geschieht. */
    val import: MatchImport = MatchImport(),
) {
    /** Was man selbst mitbringt, in der Form, in der es an den Dienst geht. */
    val ownRestrictions: DietaryFilter get() = DietaryFilter.of(own)

    /** Die noch nicht entschiedenen Karten, in der Reihenfolge des gemeinsamen Stapels. */
    val remainingCards: List<RecipeSummary>
        get() = stack
            .filter { it.recipeId !in decided }
            .map { shared ->
                RecipeSummary(
                    id = shared.recipeId,
                    title = shared.title,
                    photoUri = shared.imageUrl,
                    prepTimeMinutes = shared.prepTimeMinutes,
                )
            }

    /** Es fehlt noch jemand - der Einladungscode sollte also noch sichtbar sein. */
    val isAlone: Boolean get() = participants < 2

    /** Wie viel vom Ziel geschafft ist - fuer den Fortschrittsbalken. */
    val progress: Float get() = if (target <= 0) 0f else (matchCount.toFloat() / target).coerceIn(0f, 1f)

    /** Laeuft eine Runde, die "Zurueck" nicht einfach beenden soll? */
    val isInRound: Boolean get() = step != MultiplayerStep.START && step != MultiplayerStep.TREFFER
}

/** Was sich mit den Treffern anfangen laesst. */
enum class MatchAction {
    /** In die eigene Sammlung nehmen. */
    KEEP,

    /** Auf Plaetze des Wochenplans legen. */
    PLAN,

    /** Den Wochenplan durch die Treffer ersetzen - jeder an dem Tag, der fuer ihn gewaehlt wurde. */
    OVERWRITE,
}

/** Was beim Einplanen der Treffer herauskam - ueber alle Anlaeufe zusammengezaehlt. */
data class PlanSummary(
    val added: Int = 0,
    val replaced: Int = 0,
    val alreadyPlanned: Int = 0,
    /** So viele Treffer stehen im Plan, seit sie die Woche ueberschrieben haben. */
    val overwritten: Int = 0,
) {
    val placed: Int get() = added + replaced + overwritten

    operator fun plus(more: PlanAddition): PlanSummary = copy(
        added = added + more.added,
        replaced = replaced + more.replaced,
        alreadyPlanned = alreadyPlanned + more.alreadyPlanned,
    )
}

/**
 * Der Stand rund ums Uebernehmen der Treffer.
 *
 * Frueher gab es dafuer zwei Zahlen und eine Meldung, die drei verschiedene Ursachen in einen
 * Satz packte ("die Woche ist voll oder die Rezepte liessen sich nicht laden"). Hier steht
 * jede Ursache fuer sich - nur so laesst sich sagen, was zu tun ist.
 */
data class MatchImport(
    /** Laeuft gerade etwas? */
    val running: MatchAction? = null,
    /** Wie viele Treffer beim laufenden Holen schon erledigt sind. */
    val progress: Int = 0,
    /** Wie viele Treffer der laufende Anlauf holt - beim zweiten Versuch nur noch die fehlenden. */
    val total: Int = 0,
    /** Treffer in der Sammlung: Karte (Nummer des Dienstes) zu Rezept (Nummer auf diesem Geraet). */
    val kept: Map<Long, Long> = emptyMap(),
    /** Wurde "behalten" schon einmal versucht? */
    val keepTried: Boolean = false,
    /** Was bisher im Wochenplan gelandet ist - `null`, solange es niemand versucht hat. */
    val plan: PlanSummary? = null,
    /** So viele Treffer haben keinen freien Platz in der Woche - die Rueckfrage steht offen. */
    val askToReplace: Int = 0,
    /** So viele Treffer blieben draussen, weil die Woche voll ist. */
    val noRoom: Int = 0,
    /** Beim letzten Anlauf nicht geladen: Die Rezeptseite antwortete nicht. */
    val notLoaded: Int = 0,
    /** Beim letzten Anlauf noch nicht da: selbst erfasste Rezepte, die das andere Telefon erst schicken muss. */
    val notYetShared: Int = 0,
    /** Was "Erneut versuchen" wiederholt. */
    val retry: MatchAction? = null,
    /** Steht die Frage "fuer wie viele?" offen? */
    val askPortions: Boolean = false,
    /** Wie viele Posten die Einkaufsliste hat, nachdem die Zutaten der behaltenen Treffer dazukamen. */
    val listItems: Int? = null,
    /**
     * Die offene Tageswahl von "Woche ueberschreiben": welcher Treffer an welchem Tag auf den
     * Tisch kommt. `null`, solange das Blatt zu ist.
     */
    val week: WeekAssignment? = null,
) {
    val isBusy: Boolean get() = running != null

    val hasProblems: Boolean get() = notLoaded + notYetShared > 0
}

/**
 * Fuehrt durch eine gemeinsame Runde.
 *
 * Der Ablauf folgt dem Dienst (siehe `pairing/README.md`): Wer eroeffnet, bekommt sofort
 * einen Code und legt in der Lobby fest, wie viele Gerichte gesucht werden und ob es um
 * Essen, Getraenke oder beides geht - die eingeladene Person sieht das mit, kann es aber
 * nicht aendern. Dann steuern beide Geraete Karten bei: zuerst die gespeicherten Rezepte
 * beider, auf Wunsch danach neue.
 *
 * Drei Dinge sind anders als frueher, und alle drei hatten dieselbe Wurzel:
 *
 * - Was eine Person nicht auf dem Tisch will, gilt fuer beide. Der Dienst legt die Angaben
 *   zusammen, und jedes Geraet steuert nur bei, was dazu passt. Frueher filterte jedes
 *   Telefon fuer sich - "vegan nicht" der einen Person kam bei der anderen nie an.
 * - Beide sehen denselben Stapel ab derselben Karte. Frueher uebersprang ein Telefon still,
 *   was nicht zu seinem Filter passte, und stand damit an einer anderen Karte als das andere.
 * - Jede Entscheidung geht sofort an den Dienst. So kann er die Runde beenden, sobald genug
 *   gemeinsame Treffer da sind, statt beide den ganzen Stapel durchwischen zu lassen.
 */
@HiltViewModel
class MultiplayerViewModel @Inject constructor(
    private val pairing: PairingRepository,
    private val observeRound: ObserveRoundUseCase,
    private val buildContribution: BuildRoundContributionUseCase,
    private val selectionHolder: SharedSelectionHolder,
    private val activeRounds: ActiveRoundRepository,
    private val profileRepository: UserProfileRepository,
    private val recipeRepository: RecipeRepository,
    private val readCard: ReadRecipeCardUseCase,
    private val fetchMatches: FetchMatchesUseCase,
    private val keepMatches: KeepMatchesUseCase,
    private val shareOwnMatches: ShareOwnMatchesUseCase,
    private val addToShoppingList: AddRecipesToShoppingListUseCase,
    private val addToPlan: AddToPlanUseCase,
    private val overwritePlan: OverwritePlanUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MultiplayerUiState(selectionCount = selectionHolder.peek().size))
    val uiState: StateFlow<MultiplayerUiState> = _uiState.asStateFlow()

    /** Der letzte Stand, den der Dienst genannt hat. */
    private var lastState: RoundState? = null

    /** Die Runde, deren Stapel dieses Geraet haelt. */
    private var stackRound = NO_ROUND

    /** Die eigenen Entscheidungen im laufenden Stapel, in ihrer Reihenfolge - die letzte laesst sich zuruecknehmen. */
    private val history = ArrayDeque<Long>()

    /** Entscheidungen, die noch nicht beim Dienst angekommen sind. */
    private val pending = mutableListOf<Queued>()
    private var nextSequence = 0L
    private var isSending = false

    /** Die Runde, fuer die gerade Karten zusammengestellt werden. */
    private var contributingRound = NO_ROUND

    /** Karten aus einer eigenen Wischrunde - sie kommen mit dem ersten Beitrag in den Stapel. */
    private var selection: List<SharedRecipe> = emptyList()

    /** Unbeantwortete Aenderungen an den Einstellungen - solange gilt, was hier getippt wurde. */
    private var settingsInFlight = 0

    /**
     * Zaehlt jedes Betreten und Verlassen einer Runde. Eine Antwort, die fuer eine fruehere
     * Runde unterwegs war, erkennt daran, dass sie zu spaet kommt.
     */
    private var visit = 0
    private var pollJob: Job? = null

    /** Zaehlt die Frist herunter, bis die Runde auch ohne die fehlenden Karten beginnt. */
    private var waitJob: Job? = null

    /** Neue Karten, die schon vorbereitet werden, waehrend noch gespeicherte Rezepte dran sind. */
    private var prepared: Prepared? = null

    /** Eigene selbst erfasste Treffer, deren Inhalt schon beim Dienst liegt. */
    private val shared = mutableSetOf<Long>()
    private var sharingJob: Job? = null

    /** Treffer, die sich beim letzten Anlauf nicht holen liessen - "Erneut versuchen" nimmt genau diese. */
    private var failedMatches: List<SharedRecipe> = emptyList()

    /** Geholte Treffer, fuer die die Woche keinen freien Platz hatte - sie warten auf die Antwort. */
    private var waitingForRoom: List<Recipe> = emptyList()

    /** Die zuletzt gewaehlte Verteilung - wer das Blatt noch einmal oeffnet, faengt nicht von vorn an. */
    private var chosenWeek: WeekAssignment? = null

    /** Die bestaetigte Verteilung, nach der gerade ueberschrieben wird: Karte zu Platz. */
    private var overwritePlaces: Map<Long, PlanPlace> = emptyMap()

    /**
     * Hat diese Verteilung den alten Plan schon ersetzt? Dann raeumt ein zweiter Anlauf (fuer
     * ein Rezept, das sich nicht laden liess) die Woche nicht noch einmal leer.
     */
    private var weekReplaced = false

    private data class Prepared(val round: Int, val offers: Deferred<List<RoundOffer>>)

    private data class Queued(val sequence: Long, val vote: SharedVote)

    init {
        viewModelScope.launch {
            val profile = profileRepository.profile.first()
            val runde = activeRounds.load()?.takeUnless { it.isExpired(System.currentTimeMillis()) }
            if (runde == null) activeRounds.clear()
            _uiState.update { it.copy(own = profile, resumableCode = runde?.code) }
        }
    }

    // ------------------------------------------------------------------ vor der Runde

    fun onCodeInputChange(value: String) {
        _uiState.update { it.copy(codeInput = value.uppercase().take(MAX_CODE_LENGTH), error = null) }
    }

    fun onOwnMainDiet(diet: Diet?) = editOwn { ProfileEdits.mainDiet(it, diet) }

    fun onOwnDietToggle(diet: Diet) = editOwn { ProfileEdits.toggleDiet(it, diet) }

    fun onOwnIntoleranceToggle(intolerance: Intolerance) = editOwn { ProfileEdits.toggleIntolerance(it, intolerance) }

    fun onOwnDislikeToggle(dislike: Dislike) = editOwn { ProfileEdits.toggleDislike(it, dislike) }

    fun onOwnCustomDislikeAdd(text: String) = editOwn { ProfileEdits.addCustomDislike(it, text) }

    fun onOwnCustomDislikeRemove(text: String) = editOwn { ProfileEdits.removeCustomDislike(it, text) }

    /** Aendert die eigenen Angaben nur fuer diese Runde - gespeichert wird davon nichts. */
    private fun editOwn(change: (UserProfile) -> UserProfile) {
        if (_uiState.value.step != MultiplayerStep.START) return
        _uiState.update { it.copy(own = change(it.own)) }
    }

    /** Eroeffnet eine Runde. Der Code steht sofort - festgelegt wird danach, in der Lobby. */
    fun onHost() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, error = null) }

        viewModelScope.launch {
            when (val result = pairing.createRound(_uiState.value.ownRestrictions)) {
                is PairingResult.Success -> enter(result.value)
                is PairingResult.Failure -> failBeforeRound(result.error)
            }
        }
    }

    fun onJoin() {
        val state = _uiState.value
        if (state.codeInput.isBlank() || state.isBusy) return
        _uiState.update { it.copy(isBusy = true, error = null) }

        viewModelScope.launch {
            when (val result = pairing.joinRound(state.codeInput, state.ownRestrictions)) {
                is PairingResult.Success -> enter(result.value)
                is PairingResult.Failure -> failBeforeRound(result.error)
            }
        }
    }

    /**
     * Setzt eine unterbrochene Runde fort. Der Dienst kennt das Geraet schon und gibt Stand,
     * Stapel und die eigenen bisherigen Entscheidungen zurueck.
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

            when (val result = pairing.joinRound(runde.code, _uiState.value.ownRestrictions)) {
                is PairingResult.Success -> enter(result.value)

                is PairingResult.Failure -> {
                    // Abgelaufen, geloescht oder inzwischen voll: Fortsetzen ist nicht mehr moeglich.
                    if (result.error != PairingError.NO_CONNECTION && result.error != PairingError.UNKNOWN) {
                        activeRounds.clear()
                        _uiState.update { it.copy(resumableCode = null) }
                    }
                    failBeforeRound(result.error)
                }
            }
        }
    }

    private fun failBeforeRound(error: PairingError) {
        _uiState.update { it.copy(isBusy = false, error = error) }
    }

    /** Betritt eine Runde: merkt sie sich, uebernimmt den Stand und fragt ab jetzt regelmaessig nach. */
    private fun enter(state: RoundState) {
        visit += 1
        forgetStack()
        lastState = null
        contributingRound = NO_ROUND
        settingsInFlight = 0
        chosenWeek = null
        overwritePlaces = emptyMap()
        weekReplaced = false
        selection = selectionHolder.take()

        viewModelScope.launch {
            activeRounds.save(ActiveRound(code = state.code, isHost = state.isHost, expiresAt = state.expiresAt))
        }
        _uiState.update {
            it.copy(
                codeInput = "",
                resumableCode = null,
                selectionCount = selection.size,
                matches = emptyList(),
                end = null,
                import = MatchImport(),
            )
        }
        apply(state)
        watch(state.code)
    }

    // ------------------------------------------------------------------ Lobby

    fun onTargetChange(value: Int) = configure { it.copy(target = value.coerceIn(1, RoundState.MAX_TARGET)) }

    fun onCourseChange(course: CourseChoice) = configure { it.copy(course = course) }

    fun onSourceChange(source: RoundSource) = configure { it.copy(source = source) }

    /**
     * Aendert, was gesucht wird. Nur wer eroeffnet hat, und nur vor dem Start.
     *
     * Die Auswahl gilt auf dem eigenen Bildschirm sofort; der Dienst erfaehrt sie im
     * Hintergrund und reicht sie an die andere Person weiter.
     */
    private fun configure(change: (MultiplayerUiState) -> MultiplayerUiState) {
        val before = _uiState.value
        if (!before.isHost || before.step != MultiplayerStep.LOBBY) return
        val after = change(before)
        if (after == before) return
        _uiState.value = after

        val besuch = visit
        settingsInFlight += 1
        viewModelScope.launch {
            val result = pairing.configureRound(after.code, after.target, after.course, after.source)
            if (besuch != visit) return@launch
            settingsInFlight -= 1
            handle(result)
        }
    }

    /** Schliesst die Lobby: Ab jetzt steuern beide ihre Karten bei. */
    fun onStart() {
        val state = _uiState.value
        if (!state.isHost || state.step != MultiplayerStep.LOBBY || state.isAlone || state.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        act { pairing.startRound(state.code) }
    }

    // ------------------------------------------------------------------ Wischen

    fun onSwiped(recipeId: Long, liked: Boolean) {
        val state = _uiState.value
        if (state.step != MultiplayerStep.WISCHEN || recipeId in state.decided) return

        history.addLast(recipeId)
        enqueue(SharedVote(recipeId = recipeId, liked = liked))
        _uiState.update {
            val entschieden = it.decided + recipeId
            it.copy(
                decided = entschieden,
                canUndo = true,
                step = if (it.stack.all { karte -> karte.recipeId in entschieden }) MultiplayerStep.WARTET else it.step,
            )
        }
        flush()
        lastState?.let(::prepareNextRound)
    }

    /**
     * Nimmt die letzte Entscheidung zurueck: Die Karte liegt wieder oben, und beim Dienst
     * gilt sie wieder als unentschieden - ein Treffer, der daran hing, ist damit auch weg.
     */
    fun onUndo() {
        val state = _uiState.value
        if (state.step != MultiplayerStep.WISCHEN && state.step != MultiplayerStep.WARTET) return
        val letzte = history.removeLastOrNull() ?: return

        enqueue(SharedVote(recipeId = letzte, liked = null))
        _uiState.update {
            it.copy(decided = it.decided - letzte, canUndo = history.isNotEmpty(), step = MultiplayerStep.WISCHEN)
        }
        flush()
    }

    private fun enqueue(vote: SharedVote) {
        pending += Queued(sequence = nextSequence, vote = vote)
        nextSequence += 1
    }

    /**
     * Schickt, was noch nicht beim Dienst ist - der Reihe nach und nie zwei Sendungen
     * gleichzeitig, damit ein "Ja" nicht nach seinem eigenen "zurueckgenommen" ankommt.
     *
     * Scheitert es am Netz, bleibt alles in der Warteschlange; der naechste Wisch oder das
     * naechste Nachfragen versucht es wieder.
     */
    private fun flush() {
        if (isSending || pending.isEmpty()) return
        val code = _uiState.value.code
        val sendung = pending.toList()
        val besuch = visit

        isSending = true
        viewModelScope.launch {
            val result = pairing.sendVotes(code, sendung.map { it.vote }, stackRound)
            if (besuch != visit) return@launch
            isSending = false
            if (result is PairingResult.Success) {
                val bis = sendung.last().sequence
                pending.removeAll { it.sequence <= bis }
            }
            handle(result)
            if (result is PairingResult.Success) flush()
        }
    }

    // ------------------------------------------------------------------ Frage und Ende

    /** Der Stapel ist durch: mit neuen Rezepten weitermachen. Nur wer eroeffnet hat. */
    fun onContinue() {
        val state = _uiState.value
        if (!state.isHost || state.step != MultiplayerStep.FRAGE || state.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        act { pairing.continueRound(state.code, stackRound) }
    }

    /**
     * Hoert auf und behaelt, was gefunden wurde. Die Runde endet damit fuer beide, und beide
     * sehen die Treffer.
     *
     * Aus der Lobby heraus gibt es nichts zu behalten: Dann wird die Runde vergessen - und,
     * wenn noch niemand beigetreten ist, beim Dienst gleich geloescht.
     */
    fun onEndRound() {
        val state = _uiState.value
        if (state.code.isBlank() || state.step == MultiplayerStep.TREFFER) return

        if (state.step == MultiplayerStep.LOBBY) {
            viewModelScope.launch {
                if (state.isHost && state.isAlone) pairing.closeRound(state.code) else pairing.endRound(state.code)
            }
            leaveRound(resumable = null)
            return
        }

        _uiState.update { it.copy(isBusy = true) }
        act { pairing.endRound(state.code) }
    }

    /**
     * Verlaesst den Bildschirm. Eine laufende Runde bleibt beim Dienst bestehen und laesst
     * sich fortsetzen; ist sie schon zu Ende, wird sie vergessen.
     */
    fun onLeave() {
        val state = _uiState.value
        leaveRound(resumable = state.code.takeIf { state.isInRound && it.isNotBlank() } ?: state.resumableCode)
    }

    /** Nach dem Ende: zurueck an den Anfang, fuer eine neue Runde. */
    fun onNewRound() {
        leaveRound(resumable = null)
    }

    private fun leaveRound(resumable: String?) {
        visit += 1
        pollJob?.cancel()
        waitJob?.cancel()
        sharingJob?.cancel()
        dropPrepared()
        forgetStack()
        lastState = null
        contributingRound = NO_ROUND
        settingsInFlight = 0
        isSending = false
        shared.clear()
        failedMatches = emptyList()
        waitingForRoom = emptyList()
        chosenWeek = null
        overwritePlaces = emptyMap()
        weekReplaced = false
        if (resumable == null) viewModelScope.launch { activeRounds.clear() }

        _uiState.update {
            MultiplayerUiState(
                own = it.own,
                selectionCount = selectionHolder.peek().size,
                resumableCode = resumable,
            )
        }
    }

    /** Die Runde gibt es nicht mehr (abgelaufen, geloescht) oder die App ist zu alt dafuer. */
    private fun roundLost(error: PairingError) {
        leaveRound(resumable = null)
        _uiState.update { it.copy(error = error) }
    }

    // ------------------------------------------------------------------ Stand uebernehmen

    /** Fuehrt eine Aktion beim Dienst aus und uebernimmt den Stand, mit dem er antwortet. */
    private fun act(call: suspend () -> PairingResult<RoundState>) {
        val besuch = visit
        viewModelScope.launch {
            val result = call()
            if (besuch == visit) handle(result)
        }
    }

    private fun handle(result: PairingResult<RoundState>) {
        when (result) {
            is PairingResult.Success -> apply(result.value)
            is PairingResult.Failure -> fail(result.error)
        }
    }

    /** Ein Fehler waehrend der Runde. Fehlt nur das Netz, laeuft sie weiter - es wird erneut versucht. */
    private fun fail(error: PairingError) {
        when (error) {
            PairingError.UNKNOWN_CODE, PairingError.EXPIRED, PairingError.OUTDATED -> roundLost(error)
            PairingError.NO_CONNECTION -> _uiState.update { it.copy(isBusy = false, isOffline = true) }
            PairingError.FULL, PairingError.UNKNOWN -> _uiState.update { it.copy(isBusy = false, error = error) }
        }
    }

    private fun watch(code: String) {
        pollJob?.cancel()
        pollJob = observeRound(code) {
            val stand = lastState
            // Fehlt der Stapel der laufenden Runde, muss der Dienst den ganzen Stand schicken -
            // auch wenn sich seit dem letzten Mal nichts geaendert hat.
            val brauchtStapel = stand != null && stand.phase == RoundPhase.LAEUFT && stackRound != stand.round
            ObserveRoundUseCase.Known(revision = stand?.revision.takeUnless { brauchtStapel }, round = stackRound)
        }.onEach { poll ->
            when (poll) {
                is RoundPoll.Changed -> apply(poll.state)
                RoundPoll.Quiet -> _uiState.update { it.copy(isOffline = false) }
                is RoundPoll.Problem -> when (poll.error) {
                    PairingError.UNKNOWN_CODE, PairingError.EXPIRED, PairingError.OUTDATED -> roundLost(poll.error)
                    // Beim Nachfragen ist jeder andere Fehler dasselbe: gerade nicht erreichbar.
                    else -> _uiState.update { it.copy(isOffline = true) }
                }
            }
            // Was beim letzten Mal am Netz gescheitert ist, bekommt hier seine naechste Gelegenheit.
            if (poll !is RoundPoll.Problem) retry()
        }.launchIn(viewModelScope)
    }

    private fun retry() {
        flush()
        lastState?.let(::contributeIfNeeded)
    }

    /**
     * Uebernimmt einen Stand des Dienstes.
     *
     * Ein aelterer Stand - eine Antwort, die unterwegs von einer neueren ueberholt wurde -
     * dreht die Uhr nicht zurueck.
     */
    private fun apply(state: RoundState) {
        val previous = lastState
        if (previous != null && previous.code == state.code && state.revision < previous.revision) return
        lastState = state

        val stack = state.stack
        when {
            state.phase == RoundPhase.LAEUFT && stack != null -> adoptStack(state, stack)
            // Eine neue Runde hat begonnen: Der alte Stapel ist Geschichte.
            state.round != stackRound -> forgetStack()
            // Es wird nicht mehr gewischt - was noch in der Warteschlange lag, zaehlt nicht mehr.
            state.phase != RoundPhase.LAEUFT -> pending.clear()
        }
        val haelt = stackRound == state.round

        _uiState.update { current ->
            val karten = when {
                stack != null && haelt -> stack
                haelt -> current.stack
                else -> emptyList()
            }
            val entschieden = if (haelt) history.toSet() else emptySet()
            val eigeneEinstellungen = settingsInFlight > 0
            current.copy(
                step = stepFor(state, haelt, karten, entschieden),
                code = state.code,
                isHost = state.isHost,
                participants = state.participants,
                target = if (eigeneEinstellungen) current.target else state.target,
                course = if (eigeneEinstellungen) current.course else state.course,
                source = if (eigeneEinstellungen) current.source else state.source,
                restrictions = state.restrictions,
                stack = karten,
                decided = entschieden,
                canUndo = haelt && state.phase == RoundPhase.LAEUFT && history.isNotEmpty(),
                matchCount = state.matchCount,
                partnerDone = state.othersDone,
                hasContributed = state.hasContributed,
                end = state.end,
                matches = state.matches ?: current.matches,
                isBusy = false,
                isOffline = false,
                // Die Karte, zu der ein Blatt offen war, gibt es nach dem Wischen nicht mehr.
                info = current.info.takeIf { state.phase == RoundPhase.LAEUFT },
            )
        }

        trackDeadline(state)
        shareOwn(state)
        prepareNextRound(state)

        when (state.phase) {
            RoundPhase.SAMMELN -> contributeIfNeeded(state)

            RoundPhase.BEENDET -> {
                pollJob?.cancel()
                dropPrepared()
                viewModelScope.launch { activeRounds.clear() }
            }

            else -> Unit
        }
    }

    /**
     * Zeigt, wie lange noch auf fehlende Karten gewartet wird.
     *
     * Der Dienst nennt den Rest, nicht die Uhrzeit - die Uhren zweier Telefone gehen nie
     * gleich. Heruntergezaehlt wird hier; ist die Frist um, laesst das naechste Nachfragen
     * die Runde beginnen.
     */
    private fun trackDeadline(state: RoundState) {
        waitJob?.cancel()
        val rest = state.contributionWaitMs?.takeIf { state.phase == RoundPhase.SAMMELN }
        if (rest == null) {
            _uiState.update { it.copy(waitSeconds = null) }
            return
        }
        waitJob = viewModelScope.launch {
            var sekunden = ((rest + MILLIS - 1) / MILLIS).toInt()
            while (true) {
                _uiState.update { it.copy(waitSeconds = sekunden) }
                if (sekunden <= 0) break
                delay(MILLIS)
                sekunden -= 1
            }
        }
    }

    /**
     * Hinterlegt den Inhalt der eigenen selbst erfassten Rezepte, die Treffer geworden sind.
     *
     * Die andere Person kann sie sonst nicht uebernehmen. Mehrere Anlaeufe, weil die App nach
     * dem Ende der Runde nicht mehr regelmaessig nachfragt - es gaebe sonst keine zweite
     * Gelegenheit.
     */
    private fun shareOwn(state: RoundState) {
        val matches = state.matches ?: return
        val offen = shareOwnMatches.wanted(matches) - shared
        if (offen.isEmpty() || sharingJob?.isActive == true) return

        val besuch = visit
        sharingJob = viewModelScope.launch {
            repeat(SHARE_ATTEMPTS) { anlauf ->
                if (besuch != visit) return@launch
                shared += shareOwnMatches(state.code, matches.filter { it.recipeId in offen && it.recipeId !in shared })
                if (shared.containsAll(offen)) return@launch
                delay(SHARE_RETRY_MS * (anlauf + 1))
            }
        }
    }

    /**
     * Bereitet neue Karten vor, solange noch gespeicherte Rezepte dran sind.
     *
     * Neue Karten muessen an ihrer Rezeptseite geprueft werden, sobald jemand etwas nicht
     * isst - das dauerte nach "weiter mit neuen Rezepten" eine Viertelminute. Zeichnet sich
     * ab, dass die gespeicherten nicht reichen (der halbe Stapel ist durch, das Ziel fehlt
     * noch), beginnt die Vorbereitung schon jetzt; beim Weitermachen liegt sie dann bereit.
     */
    private fun prepareNextRound(state: RoundState) {
        val naechste = state.round + 1
        if (prepared?.round == naechste) return

        val unterwegs = state.phase == RoundPhase.LAEUFT &&
            stackRound == state.round &&
            history.size * 2 >= _uiState.value.stack.size
        val lohnt = state.source == RoundSource.GESPEICHERT &&
            state.matchCount < state.target &&
            (state.phase == RoundPhase.FRAGE || unterwegs)
        if (!lohnt) return

        dropPrepared()
        val kommende = state.copy(round = naechste, source = RoundSource.NEU)
        prepared = Prepared(naechste, viewModelScope.async { buildContribution(kommende) })
    }

    private fun dropPrepared() {
        prepared?.offers?.cancel()
        prepared = null
    }

    private fun stepFor(
        state: RoundState,
        holdsStack: Boolean,
        stack: List<SharedRecipe>,
        decided: Set<Long>,
    ): MultiplayerStep = when (state.phase) {
        RoundPhase.LOBBY -> MultiplayerStep.LOBBY
        RoundPhase.SAMMELN -> MultiplayerStep.SAMMELN
        RoundPhase.LAEUFT -> when {
            // Der Stapel ist beim Dienst fertig, aber noch nicht hier - gleich kommt er.
            !holdsStack -> MultiplayerStep.SAMMELN
            stack.all { it.recipeId in decided } -> MultiplayerStep.WARTET
            else -> MultiplayerStep.WISCHEN
        }
        RoundPhase.FRAGE -> MultiplayerStep.FRAGE
        RoundPhase.BEENDET -> MultiplayerStep.TREFFER
    }

    /**
     * Uebernimmt den Stapel einer Runde samt dem, was der Dienst an eigenen Entscheidungen
     * schon kennt - und legt darueber, was hier entschieden, aber noch nicht angekommen ist.
     */
    private fun adoptStack(state: RoundState, stack: List<SharedRecipe>) {
        val karten = stack.mapTo(HashSet()) { it.recipeId }
        history.clear()
        state.votes.orEmpty().forEach { vote ->
            if (vote.liked != null &&
                vote.recipeId in karten
            ) {
                history.addLast(vote.recipeId)
            }
        }

        pending.removeAll { it.vote.recipeId !in karten }
        pending.forEach { queued ->
            val kennung = queued.vote.recipeId
            history.remove(kennung)
            if (queued.vote.liked != null) history.addLast(kennung)
        }
        stackRound = state.round
    }

    private fun forgetStack() {
        stackRound = NO_ROUND
        history.clear()
        pending.clear()
    }

    /**
     * Stellt die eigenen Karten fuer die laufende Runde zusammen und gibt sie ab.
     *
     * Das kann einen Moment dauern (neue Rezepte werden an ihrer Seite geprueft) und laeuft
     * darum im Hintergrund. Scheitert das Abgeben am Netz, versucht es das naechste Nachfragen
     * erneut.
     */
    private fun contributeIfNeeded(state: RoundState) {
        if (state.phase != RoundPhase.SAMMELN || state.hasContributed || contributingRound == state.round) return
        contributingRound = state.round
        val besuch = visit

        viewModelScope.launch {
            val vorbereitet = prepared?.takeIf { it.round == state.round && state.source == RoundSource.NEU }
            val offers = vorbereitet?.offers?.await() ?: buildContribution(state, selection)
            if (besuch != visit) return@launch

            val result = pairing.contribute(state.code, state.round, offers, stackRound)
            if (besuch != visit) return@launch
            if (result is PairingResult.Success) {
                selection = emptyList()
                _uiState.update { it.copy(selectionCount = 0) }
            } else {
                contributingRound = NO_ROUND
            }
            handle(result)
        }
    }

    // ------------------------------------------------------------------ mehr zur Karte

    /**
     * Zeigt, woher das Rezept einer Karte kommt und was hineingehoert.
     *
     * Liegt es in der eigenen Sammlung, kommen die Zutaten von dort. Sonst von der
     * Rezeptseite. Ein selbst erfasstes Rezept der anderen Person hat weder das eine noch das
     * andere - geteilt wurden davon nur Titel und Bild.
     */
    fun onInfoRequest(recipeId: Long) {
        val karte = _uiState.value.stack.firstOrNull { it.recipeId == recipeId } ?: return
        val anfang = CardInfo(
            title = karte.title,
            sourceUrl = karte.sourceUrl,
            minutes = karte.prepTimeMinutes,
            isLoading = true,
        )
        _uiState.update { it.copy(info = anfang) }

        viewModelScope.launch {
            val eigenes = ownIdOf(karte)?.let { recipeRepository.getRecipe(it) }
            val url = karte.sourceUrl
            val info = when {
                eigenes != null -> anfang.copy(
                    isLoading = false,
                    sourceName = eigenes.sourceName,
                    minutes = eigenes.prepTimeMinutes ?: karte.prepTimeMinutes,
                    servings = eigenes.servings?.toString(),
                    ingredients = eigenes.ingredients,
                    nutrition = RecipeNutrition.of(eigenes),
                )

                url != null -> when (val seite = readCard(url)) {
                    null -> anfang.copy(isLoading = false, failed = true)
                    else -> anfang.copy(
                        isLoading = false,
                        sourceName = seite.sourceName,
                        minutes = seite.totalMinutes ?: karte.prepTimeMinutes,
                        servings = seite.servings,
                        lines = seite.ingredientLines,
                        isForeign = seite.isForeign,
                        nutrition = seite.nutrition,
                    )
                }

                else -> anfang.copy(isLoading = false, isPartnersOwn = true)
            }
            // Das Blatt kann inzwischen geschlossen oder fuer eine andere Karte offen sein.
            _uiState.update { state -> if (state.info == anfang) state.copy(info = info) else state }
        }
    }

    fun onInfoDismiss() {
        _uiState.update { it.copy(info = null) }
    }

    /**
     * Die Nummer, unter der eine Karte in der eigenen Sammlung liegt - falls sie dort liegt.
     *
     * Die Nummer des Dienstes sagt darueber nichts; wiedererkannt wird an der Adresse oder,
     * bei einem selbst erfassten Rezept, am Titel.
     */
    private suspend fun ownIdOf(card: SharedRecipe): Long? = recipeRepository.findExisting(card.sourceUrl, card.title)

    // ------------------------------------------------------------------ Treffer uebernehmen

    /**
     * Nimmt die gemeinsamen Treffer in die eigene Sammlung.
     *
     * Die Einkaufsliste bleibt dabei, wie sie ist: Ein Rezept zu behalten heisst nicht, es
     * diese Woche zu kochen. Wer die Zutaten will, sagt es danach ausdruecklich.
     */
    fun onKeepMatches() = importMatches(MatchAction.KEEP, _uiState.value.matches)

    /**
     * Legt die gemeinsamen Treffer auf freie Plaetze des Wochenplans.
     *
     * Das ist der eigentliche Zweck der gemeinsamen Runde: Am Ende steht nicht eine Liste,
     * sondern eine Woche, auf die sich beide geeinigt haben. Ist die Woche voll, wird
     * gefragt, statt still nichts zu tun.
     */
    fun onAddToPlan() = importMatches(MatchAction.PLAN, _uiState.value.matches)

    /** Versucht es noch einmal - nur mit den Treffern, die beim letzten Mal nicht zu holen waren. */
    fun onRetryImport() {
        val aktion = _uiState.value.import.retry ?: return
        importMatches(aktion, failedMatches)
    }

    /**
     * Holt die Treffer als ganze Rezepte und fuehrt [action] damit aus.
     *
     * Was sich nicht holen laesst, bleibt in [failedMatches] - mit dem Grund: Die Rezeptseite
     * antwortete nicht, oder das Telefon der anderen Person hat ein selbst erfasstes Rezept
     * noch nicht geschickt. Beides laesst sich wiederholen.
     */
    private fun importMatches(action: MatchAction, targets: List<SharedRecipe>) {
        val state = _uiState.value
        if (state.import.isBusy) return
        // Was schon in der Sammlung liegt, wird nicht ein zweites Mal geholt und gespeichert.
        val offen = if (action == MatchAction.KEEP) targets.filter { it.recipeId !in state.import.kept } else targets
        if (offen.isEmpty()) return

        val besuch = visit
        _uiState.update {
            it.copy(
                import = it.import.copy(
                    running = action,
                    progress = 0,
                    total = offen.size,
                    retry = null,
                    notLoaded = 0,
                    notYetShared = 0,
                ),
            )
        }
        viewModelScope.launch {
            val geholt = fetchMatches(state.code, offen) { erledigt ->
                _uiState.update { it.copy(import = it.import.copy(progress = erledigt)) }
            }
            if (besuch != visit) return@launch

            val bereit = geholt.filterIsInstance<FetchedMatch.Ready>()
            failedMatches = geholt.filterNot { it is FetchedMatch.Ready }.map { it.match }
            val probleme = MatchProblems(
                notLoaded = geholt.count { it is FetchedMatch.NotLoaded },
                notYetShared = geholt.count { it is FetchedMatch.NotYetShared },
            )

            when (action) {
                MatchAction.KEEP -> keep(bereit, probleme)
                MatchAction.PLAN -> plan(bereit, probleme)
                MatchAction.OVERWRITE -> overwrite(bereit, probleme, besuch)
            }
        }
    }

    private data class MatchProblems(val notLoaded: Int, val notYetShared: Int) {
        val any: Boolean get() = notLoaded + notYetShared > 0
    }

    private suspend fun keep(ready: List<FetchedMatch.Ready>, problems: MatchProblems) {
        val gespeichert = keepMatches(ready)
        _uiState.update {
            it.copy(
                import = it.import.copy(
                    running = null,
                    kept = it.import.kept + gespeichert,
                    keepTried = true,
                    notLoaded = problems.notLoaded,
                    notYetShared = problems.notYetShared,
                    retry = MatchAction.KEEP.takeIf { problems.any },
                ),
            )
        }
    }

    private suspend fun plan(ready: List<FetchedMatch.Ready>, problems: MatchProblems) {
        // Ein Treffer, der schon in der Sammlung liegt, kommt von dort - mit seiner Nummer,
        // sonst stuende er als zweite Kopie im Plan.
        val behalten = _uiState.value.import.kept
        val rezepte = ready.map { treffer ->
            behalten[treffer.match.recipeId]?.let { recipeRepository.getRecipe(it) } ?: treffer.recipe
        }
        val ergebnis = addToPlan(rezepte)
        waitingForRoom = ergebnis.noRoom
        _uiState.update {
            it.copy(
                import = it.import.copy(
                    running = null,
                    plan = (it.import.plan ?: PlanSummary()) + ergebnis,
                    askToReplace = ergebnis.noRoom.size,
                    noRoom = 0,
                    notLoaded = problems.notLoaded,
                    notYetShared = problems.notYetShared,
                    retry = MatchAction.PLAN.takeIf { problems.any },
                ),
            )
        }
    }

    /** Die Woche ist voll: Die Treffer ersetzen geplante Mahlzeiten, die noch nicht gekocht sind. */
    fun onReplaceInPlan() {
        val wartend = waitingForRoom
        if (wartend.isEmpty() || _uiState.value.import.isBusy) return
        waitingForRoom = emptyList()

        val besuch = visit
        _uiState.update { it.copy(import = it.import.copy(running = MatchAction.PLAN, askToReplace = 0)) }
        viewModelScope.launch {
            val ergebnis = addToPlan(wartend, PlanAddMode.REPLACE_UNCOOKED)
            if (besuch != visit) return@launch
            _uiState.update {
                it.copy(
                    import = it.import.copy(
                        running = null,
                        plan = (it.import.plan ?: PlanSummary()) + ergebnis,
                        // Auch nach dem Ersetzen kann etwas uebrig bleiben: wenn schon alles gekocht ist.
                        noRoom = ergebnis.noRoom.size,
                    ),
                )
            }
        }
    }

    /** Die Woche bleibt, wie sie ist - was keinen Platz fand, bleibt draussen. */
    fun onKeepPlanAsIs() {
        val uebrig = waitingForRoom.size
        waitingForRoom = emptyList()
        _uiState.update { it.copy(import = it.import.copy(askToReplace = 0, noRoom = uebrig)) }
    }

    // ------------------------------------------------------------------ Woche ueberschreiben

    /**
     * Oeffnet die Tageswahl: Die Treffer sollen den Wochenplan ersetzen, und fuer jeden
     * laesst sich sagen, an welchem Tag er auf den Tisch kommt.
     *
     * Das ist die Antwort auf eine volle Woche, die [onAddToPlan] nicht geben kann: Dort wird
     * hoechstens ersetzt, was gerade im Weg steht - hier ist die Woche danach die der Runde.
     * Vorgeschlagen wird eine Verteilung ab heute; wer schon einmal gewaehlt hat, bekommt
     * seine Wahl zurueck.
     */
    fun onOverwriteRequest() {
        val state = _uiState.value
        if (state.import.isBusy || state.matches.isEmpty() || state.import.week != null) return

        val besuch = visit
        viewModelScope.launch {
            val karten = state.matches.map { it.recipeId }
            val woche = chosenWeek ?: WeekAssignment.suggest(karten, profileRepository.profile.first(), Weekday.today())
            if (besuch != visit) return@launch
            _uiState.update { it.copy(import = it.import.copy(week = woche)) }
        }
    }

    /** Ein Treffer bekommt seinen Tag - oder verliert ihn, wenn es derselbe noch einmal ist. */
    fun onOverwriteDay(recipeId: Long, day: Weekday) = editWeek { it.toggleDay(recipeId, day) }

    fun onOverwriteSlot(recipeId: Long, slot: MealSlot) = editWeek { it.withSlot(recipeId, slot) }

    private fun editWeek(change: (WeekAssignment) -> WeekAssignment) {
        _uiState.update { state ->
            val woche = state.import.week ?: return@update state
            state.copy(import = state.import.copy(week = change(woche)))
        }
    }

    /** Schliesst die Tageswahl, ohne etwas zu aendern. Was gewaehlt war, bleibt fuer das naechste Mal. */
    fun onOverwriteDismiss() {
        chosenWeek = _uiState.value.import.week ?: chosenWeek
        _uiState.update { it.copy(import = it.import.copy(week = null)) }
    }

    /**
     * Ersetzt den Wochenplan durch die verteilten Treffer.
     *
     * Erst werden die Rezepte geholt, dann wird ueberschrieben - mit dem, was sich holen
     * liess. Liess sich gar nichts holen, bleibt die Woche, wie sie ist: Ein leerer Plan ist
     * kein Ersatz fuer einen vollen.
     */
    fun onOverwriteConfirm() {
        val state = _uiState.value
        val woche = state.import.week ?: return
        if (state.import.isBusy || woche.places.isEmpty()) return

        chosenWeek = woche
        overwritePlaces = woche.places
        weekReplaced = false
        _uiState.update { it.copy(import = it.import.copy(week = null)) }
        importMatches(MatchAction.OVERWRITE, state.matches.filter { it.recipeId in woche.places })
    }

    private suspend fun overwrite(ready: List<FetchedMatch.Ready>, problems: MatchProblems, besuch: Int) {
        // Wie beim Einplanen: Was in der Sammlung liegt, kommt von dort - mit seiner Nummer.
        val behalten = _uiState.value.import.kept
        val gerichte = ready.mapNotNull { treffer ->
            val platz = overwritePlaces[treffer.match.recipeId] ?: return@mapNotNull null
            val rezept = behalten[treffer.match.recipeId]?.let { recipeRepository.getRecipe(it) } ?: treffer.recipe
            PlacedRecipe(rezept, platz)
        }

        // Der erste Anlauf ersetzt den Plan, jeder weitere ergaenzt, was dabei noch fehlte.
        val ergaenzt = weekReplaced
        val gelegt = if (gerichte.isEmpty()) 0 else overwritePlan(gerichte, keepOthers = ergaenzt)
        if (besuch != visit) return
        if (gelegt > 0) {
            weekReplaced = true
            waitingForRoom = emptyList()
        }

        _uiState.update {
            val bisher = it.import.plan
            it.copy(
                import = it.import.copy(
                    running = null,
                    plan = when {
                        gelegt == 0 -> bisher
                        ergaenzt -> (bisher ?: PlanSummary()).let { plan ->
                            plan.copy(overwritten = plan.overwritten + gelegt)
                        }
                        // Was vorher eingeplant wurde, stand im alten Plan - den gibt es nicht mehr.
                        else -> PlanSummary(overwritten = gelegt)
                    },
                    askToReplace = if (gelegt > 0) 0 else it.import.askToReplace,
                    noRoom = if (gelegt > 0) 0 else it.import.noRoom,
                    notLoaded = problems.notLoaded,
                    notYetShared = problems.notYetShared,
                    retry = MatchAction.OVERWRITE.takeIf { problems.any },
                ),
            )
        }
    }

    // ------------------------------------------------------------------ Einkaufsliste

    /** Vor dem Uebernehmen der Zutaten steht die Frage, fuer wie viele eingekauft wird. */
    fun onListRequest() {
        val stand = _uiState.value.import
        if (stand.kept.isEmpty() || stand.isBusy) return
        _uiState.update { it.copy(import = it.import.copy(askPortions = true)) }
    }

    fun onListDismiss() {
        _uiState.update { it.copy(import = it.import.copy(askPortions = false)) }
    }

    /** Legt die Zutaten der behaltenen Treffer auf die Einkaufsliste - umgerechnet auf [persons]. */
    fun onAddToShoppingList(persons: Int) {
        val kennungen = _uiState.value.import.kept.values
            .distinct()
        _uiState.update { it.copy(import = it.import.copy(askPortions = false)) }
        if (kennungen.isEmpty()) return

        viewModelScope.launch {
            val posten = addToShoppingList(kennungen, persons = persons)
            _uiState.update { it.copy(import = it.import.copy(listItems = posten)) }
        }
    }

    fun onDismissError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val MAX_CODE_LENGTH = 8
        const val MILLIS = 1_000L

        /** So oft wird versucht, den Inhalt eigener Treffer zu hinterlegen - mit wachsender Pause. */
        const val SHARE_ATTEMPTS = 4
        const val SHARE_RETRY_MS = 3_000L
    }
}
