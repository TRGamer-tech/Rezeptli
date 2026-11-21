package ch.rezeptli.app.presentation.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.deck.CuratedDeckBuilder
import ch.rezeptli.app.domain.deck.DeckEntry
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedSelectionHolder
import ch.rezeptli.app.domain.plan.CardRules
import ch.rezeptli.app.domain.plan.CheckRecipeCardUseCase
import ch.rezeptli.app.domain.plan.CourseChoice
import ch.rezeptli.app.domain.plan.DietaryFilter
import ch.rezeptli.app.domain.plan.ReadRecipeCardUseCase
import ch.rezeptli.app.domain.profile.LanguageRegion
import ch.rezeptli.app.domain.repository.RecipeRepository
import ch.rezeptli.app.domain.repository.UserProfileRepository
import ch.rezeptli.app.domain.repository.WebRecipeRepository
import ch.rezeptli.app.domain.usecase.BuildSwipeDeckUseCase
import ch.rezeptli.app.domain.usecase.LoadWebRecipeUseCase
import ch.rezeptli.app.domain.usecase.SaveRecipeUseCase
import ch.rezeptli.app.domain.usecase.WebImportOutcome
import ch.rezeptli.app.presentation.swipe.CardInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/** Wo in einer Wischrunde man gerade steht. */
enum class DeckStep {
    /** Wie viele Gerichte sollen es werden, und was wird gesucht? Steht vor jeder Runde. */
    ANZAHL,

    /** Der Stapel wird zusammengestellt. */
    LADEN,

    WISCHEN,

    /** Genug beisammen - die Ausbeute steht. */
    FERTIG,
}

data class DeckUiState(
    val step: DeckStep = DeckStep.ANZAHL,
    /** `null` heisst ohne Ziel - die Runde endet erst, wenn die Person selbst aufhoert. */
    val target: Int? = DEFAULT_TARGET,
    /** Wonach gesucht wird: Essen, Getraenke oder beides. */
    val course: CourseChoice = CourseChoice.ESSEN,
    val cards: List<DeckEntry> = emptyList(),
    val liked: List<DeckEntry> = emptyList(),
    val isSaving: Boolean = false,
    val savedCount: Int = 0,
    val loadFailed: Boolean = false,
    /** Das Verzeichnis ist da, aber nichts darin passt zur Auswahl - oder alles ist schon gespeichert. */
    val nothingFits: Boolean = false,
    /** Die Runde endete, weil zu dieser Auswahl nichts mehr kam - nicht, weil das Ziel erreicht war. */
    val exhausted: Boolean = false,
    /** Was eine Karte erfuellen muss: Essen oder Getraenk, Ernaehrung, Abneigungen, Geraete. */
    val rules: CardRules = CardRules(),
    /** Karten, die die Pruefung ihrer Rezeptseite bestanden haben. Nur von Bedeutung, wenn sie noetig ist. */
    val approved: Set<String> = emptySet(),
    /** Laesst sich die letzte Entscheidung zuruecknehmen? */
    val canUndo: Boolean = false,
    /** Die Angaben zur angetippten Karte, solange das Blatt offen ist. */
    val info: CardInfo? = null,
) {
    /**
     * Die noch offenen Karten, in der Form, die der bestehende Stapel erwartet.
     *
     * Nur Karten mit Bild - eine Wischkarte lebt vom Foto, und eine Karte ohne eins
     * waere nur eine leere Flaeche mit Titel. Karten ohne Bild bleiben im Hintergrund
     * liegen, bis ihr Bild da ist (oder sie ganz herausfallen, wenn keins zu holen war),
     * und tauchen dann von selbst auf.
     */
    val remainingCards: List<RecipeSummary>
        get() = cards
            .filter { it.imageUrl != null && (!rules.needsPage || it.url in approved) }
            .map { eintrag ->
                RecipeSummary(
                    id = eintrag.url.hashCode().toLong(),
                    title = eintrag.title,
                    photoUri = eintrag.imageUrl,
                    prepTimeMinutes = null,
                )
            }

    val foundCount: Int get() = liked.size

    /** Wie viel vom Ziel geschafft ist - fuer den Fortschrittsbalken. Ohne Ziel unbenutzt. */
    val progress: Float get() = when (val ziel = target) {
        null -> 0f
        else -> if (ziel <= 0) 0f else (foundCount.toFloat() / ziel).coerceIn(0f, 1f)
    }

    /** Nichts zum Zeigen - entweder ist der Stapel leer, oder alle offenen Karten warten noch auf ihr Bild. */
    val isEmpty: Boolean get() = remainingCards.isEmpty()
}

/** Vorschlaege fuer die Anzahl - die meisten Haushalte planen in dieser Groessenordnung. */
val TARGET_OPTIONS = listOf(1, 2, 3, 4, 5, 7)

const val DEFAULT_TARGET = 3

/**
 * Fuehrt durch eine Wischrunde aus dem Rezeptverzeichnis.
 *
 * Die Runde beginnt mit zwei Fragen - wie viele Gerichte, und ob Essen, Getraenke oder
 * beides - jedes Mal, nicht einmalig in den Einstellungen: Das haengt an der Woche und an
 * der Laune, nicht an der Person.
 *
 * Gezeigt wird nur Neues: Was schon in der Sammlung steht, muss niemand noch einmal
 * entdecken. Ist die Anzahl erreicht, endet die Runde von selbst.
 */
@HiltViewModel
class DeckViewModel @Inject constructor(
    private val buildDeck: BuildSwipeDeckUseCase,
    private val selectionHolder: SharedSelectionHolder,
    private val webRepository: WebRecipeRepository,
    private val loadWebRecipe: LoadWebRecipeUseCase,
    private val saveRecipe: SaveRecipeUseCase,
    private val profileRepository: UserProfileRepository,
    private val recipeRepository: RecipeRepository,
    private val checkCard: CheckRecipeCardUseCase,
    private val readCard: ReadRecipeCardUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeckUiState())
    val uiState: StateFlow<DeckUiState> = _uiState.asStateFlow()

    /** Schon gesehene Adressen - damit ein Nachschlag keine Wiederholung wird. */
    private val gesehen = mutableSetOf<String>()

    /** Die Adressen der Sammlung, beim Start der Runde gelesen. */
    private var gespeichert: Set<String> = emptySet()

    /** Adressen, fuer die ein Bild schon versucht wurde - erfolgreich oder nicht. */
    private val bildVersucht = mutableSetOf<String>()

    /** Adressen, deren Pruefung schon laeuft oder lief. */
    private val pruefungVersucht = mutableSetOf<String>()

    /** Nicht zu viele Rezeptseiten auf einmal - die Quellen sollen nicht ueberrannt werden. */
    private val pruefSchranke = Semaphore(PARALLEL_CHECKS)

    /** Die Entscheidungen dieser Runde in ihrer Reihenfolge - die letzte laesst sich zuruecknehmen. */
    private val verlauf = ArrayDeque<Entscheidung>()

    private data class Entscheidung(val eintrag: DeckEntry, val gemocht: Boolean)

    /** `null` waehlt die endlose Runde ohne Ziel. */
    fun onTargetChange(value: Int?) {
        _uiState.update { it.copy(target = value?.coerceAtLeast(1)) }
    }

    fun onCourseChange(course: CourseChoice) {
        _uiState.update { it.copy(course = course) }
    }

    fun onStart() {
        val target = _uiState.value.target
        val course = _uiState.value.course
        val meineRunde = neueRunde()
        _uiState.update {
            it.copy(
                step = DeckStep.LADEN,
                liked = emptyList(),
                loadFailed = false,
                nothingFits = false,
                exhausted = false,
                canUndo = false,
            )
        }

        viewModelScope.launch {
            // Ernaehrung, Abneigungen und Geraete aus den Vorlieben gelten auch beim Entdecken.
            // Was die Rezeptseite braucht, laesst sich nur auf Deutsch pruefen - fremdsprachige
            // Quellen bleiben dann draussen.
            val rules = CardRules.of(profileRepository.profile.first(), course)
            _uiState.update { it.copy(rules = rules, approved = emptySet()) }
            region = if (rules.needsPage) LanguageRegion.DEUTSCH else null
            gespeichert = recipeRepository.savedSourceUrls()

            // Etwas mehr Karten als Ziele: Es wird ja auch abgelehnt. Ohne Ziel gibt es
            // keine Zahl, von der sich das ableiten liesse - dann ein fester Vorrat.
            val groesse = target?.let { CuratedDeckBuilder.deckSizeFor(it) } ?: ENDLESS_INITIAL_SIZE
            val deck = buildDeck(size = groesse, exclude = gesehen + gespeichert, onlyRegion = region, rules = rules)
            if (meineRunde != runde) return@launch
            gesehen += deck.map { it.url }

            // Ein leerer Stapel hat zwei moegliche Gruende, und die Person soll den richtigen
            // erfahren: kein Netz - oder ein Verzeichnis, in dem nichts (mehr) zur Auswahl passt.
            val leer = deck.isEmpty()
            val ohneVerzeichnis = leer && webRepository.deckPool().isEmpty
            _uiState.update {
                it.copy(
                    step = if (leer) DeckStep.ANZAHL else DeckStep.WISCHEN,
                    cards = deck,
                    loadFailed = ohneVerzeichnis,
                    nothingFits = leer && !ohneVerzeichnis,
                )
            }
            nachladenOderPruefen()
        }
    }

    /**
     * Beginnt eine neue Runde und gibt ihre Nummer zurueck.
     *
     * Was noch fuer die vorige Runde unterwegs ist - ein Nachschub, eine Pruefung -, erkennt
     * an der Nummer, dass es zu spaet kommt, und legt nichts mehr in den neuen Stapel.
     */
    private fun neueRunde(): Int {
        runde += 1
        verlauf.clear()
        vorratLeer = false
        nachschubLaeuft = false
        return runde
    }

    private var runde = 0

    /** Sprachraum, auf den der Stapel beschraenkt ist - nur gesetzt, wenn Rezeptseiten geprueft werden. */
    private var region: LanguageRegion? = null

    fun onSwiped(entry: DeckEntry, liked: Boolean) {
        val state = _uiState.value
        val rest = state.cards.filterNot { it.url == entry.url }
        val neuGemocht = if (liked) state.liked + entry else state.liked
        verlauf.addLast(Entscheidung(entry, liked))

        // Ohne Ziel (target == null) ist keine Anzahl je "erreicht" - die Runde endet
        // nur, wenn die Person selbst auf "Fertig" tippt.
        val ziel = state.target
        val fertig = ziel != null && neuGemocht.size >= ziel
        _uiState.update {
            it.copy(
                cards = rest,
                liked = neuGemocht,
                step = if (fertig) DeckStep.FERTIG else it.step,
                canUndo = !fertig,
            )
        }

        if (!fertig) {
            nachladenOderPruefen()
            refillWennNoetig()
        }
    }

    /**
     * Nimmt die letzte Entscheidung zurueck: Die Karte liegt wieder oben auf dem Stapel,
     * und war sie gemocht, ist sie es nicht mehr.
     */
    fun onUndo() {
        if (_uiState.value.step != DeckStep.WISCHEN) return
        val letzte = verlauf.removeLastOrNull() ?: return
        _uiState.update { state ->
            state.copy(
                cards = listOf(letzte.eintrag) + state.cards.filterNot { it.url == letzte.eintrag.url },
                liked = if (letzte.gemocht) state.liked.filterNot { it.url == letzte.eintrag.url } else state.liked,
                // Die Karte war schon geprueft - sie darf sofort wieder erscheinen.
                approved = state.approved + letzte.eintrag.url,
                canUndo = verlauf.isNotEmpty(),
            )
        }
    }

    /** Auch ohne erreichtes Ziel darf man aufhoeren - mit dem, was man hat. */
    fun onFinishEarly() {
        _uiState.update { it.copy(step = DeckStep.FERTIG, canUndo = false) }
    }

    fun onRestart() {
        neueRunde()
        _uiState.value = DeckUiState(target = _uiState.value.target, course = _uiState.value.course)
    }

    /**
     * Zeigt, woher das Rezept einer Karte kommt und was hineingehoert.
     *
     * Titel und Quelle stehen sofort da, die Zutaten kommen von der Rezeptseite nach. War
     * die Seite schon fuer die Pruefung geladen, kostet das keinen zweiten Abruf.
     */
    fun onInfoRequest(cardId: Long) {
        val eintrag = _uiState.value.cards.firstOrNull { it.url.hashCode().toLong() == cardId } ?: return
        val anfang = CardInfo(
            title = eintrag.title,
            sourceName = eintrag.sourceName,
            sourceUrl = eintrag.url,
            isLoading = true,
        )
        _uiState.update { it.copy(info = anfang) }

        viewModelScope.launch {
            val seite = readCard(eintrag.url)
            _uiState.update { state ->
                // Das Blatt kann inzwischen geschlossen oder fuer eine andere Karte offen sein.
                if (state.info?.sourceUrl != eintrag.url) return@update state
                state.copy(
                    info = if (seite == null) {
                        anfang.copy(isLoading = false, failed = true)
                    } else {
                        anfang.copy(
                            isLoading = false,
                            minutes = seite.totalMinutes,
                            servings = seite.servings,
                            lines = seite.ingredientLines,
                            isForeign = seite.isForeign,
                            nutrition = seite.nutrition,
                        )
                    },
                )
            }
        }
    }

    fun onInfoDismiss() {
        _uiState.update { it.copy(info = null) }
    }

    /**
     * Holt die gemochten Rezepte wirklich und legt sie in die Sammlung.
     *
     * Erst hier wird pro Rezept eine Seite geladen. Waehrend des Wischens waere das
     * verschwendet: Die meisten Karten werden abgelehnt.
     */
    fun onKeepLiked(onDone: (Int) -> Unit) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, savedCount = 0) }

        viewModelScope.launch {
            var gespeichertAnzahl = 0
            _uiState.value.liked.forEach { eintrag ->
                when (val ergebnis = loadWebRecipe(eintrag.url)) {
                    is WebImportOutcome.Loaded -> {
                        saveRecipe(ergebnis.recipe)
                        gespeichertAnzahl += 1
                        _uiState.update { it.copy(savedCount = gespeichertAnzahl) }
                    }

                    is WebImportOutcome.Failed -> Unit
                }
            }
            _uiState.update { it.copy(isSaving = false) }
            onDone(gespeichertAnzahl)
        }
    }

    /**
     * Legt die gemochten Karten fuer eine gemeinsame Runde bereit.
     *
     * Geteilt wird, was auf den Karten stand - die Rezepte muessen dafuer nicht erst
     * geholt und gespeichert werden.
     */
    fun prepareTogether() {
        selectionHolder.set(
            _uiState.value.liked.map { eintrag ->
                SharedRecipe(
                    recipeId = eintrag.url.hashCode().toLong(),
                    title = eintrag.title,
                    imageUrl = eintrag.imageUrl,
                    sourceUrl = eintrag.url,
                )
            },
        )
    }

    /**
     * Holt Bilder fuer die naechsten Karten nach, die keins mitbringen.
     *
     * Manche Quellen nennen in ihrem Verzeichnis kein Bild; ihre Karten bleiben deshalb
     * unsichtbar (siehe [DeckUiState.remainingCards]), bis das Bild da ist. Geholt wird
     * nur fuer die naechsten paar Karten und immer im Hintergrund - keine Karte wartet
     * darauf, und fuer abgelehnte Karten wird nichts geladen, was niemand sieht.
     *
     * Ist gar kein Bild zu bekommen, faellt die Karte ganz aus dem Stapel - sie wuerde
     * sonst fuer immer unsichtbar herumliegen, ohne dass jemals nachgelegt wird.
     */
    private fun bilderNachladen() {
        val offen = _uiState.value.cards
            .take(VORAUSLADEN)
            .filter { it.imageUrl == null && it.url !in bildVersucht }
        if (offen.isEmpty()) return

        bildVersucht += offen.map { it.url }

        val meineRunde = runde
        offen.forEach { eintrag ->
            viewModelScope.launch {
                val bild = webRepository.cardImage(eintrag.url)
                if (meineRunde != runde) return@launch

                _uiState.update { state ->
                    val karten = if (bild != null) {
                        state.cards.map { karte ->
                            if (karte.url == eintrag.url) karte.copy(imageUrl = bild) else karte
                        }
                    } else {
                        state.cards.filterNot { it.url == eintrag.url }
                    }
                    state.copy(cards = karten)
                }

                if (bild == null) refillWennNoetig()
            }
        }
    }

    /** Laeuft schon ein Nachschub-Abruf? Verhindert, dass mehrere gleichzeitig starten. */
    private var nachschubLaeuft = false

    /** Hat der letzte Nachschub nichts mehr gebracht? Dann ist der Vorrat fuer diese Auswahl erschoepft. */
    private var vorratLeer = false

    /**
     * Legt nach, wenn zu wenige sichtbare (bebilderte) Karten uebrig sind.
     *
     * Gezaehlt werden nur Karten mit Bild - eine Karte, die noch auf ihres wartet,
     * zaehlt hier nicht mit. Sonst saehe der Stapel voll aus, waere aber leer.
     */
    private fun refillWennNoetig() {
        if (nachschubLaeuft || vorratLeer) return
        // Werden Rezeptseiten geprueft, zaehlen auch Karten, die noch geprueft werden: Sie
        // kommen wahrscheinlich bald dazu. Sonst wuerde waehrend jeder Pruefung nachgelegt.
        val state = _uiState.value
        val sichtbar = if (state.rules.needsPage) {
            state.cards.size
        } else {
            state.cards.count { it.imageUrl != null }
        }
        if (sichtbar > REFILL_THRESHOLD) return

        nachschubLaeuft = true
        val meineRunde = runde
        viewModelScope.launch {
            val nachschub = buildDeck(
                size = REFILL_SIZE,
                exclude = gesehen + gespeichert,
                onlyRegion = region,
                rules = state.rules,
            )
            if (meineRunde != runde) return@launch
            gesehen += nachschub.map { it.url }
            if (nachschub.isNotEmpty()) {
                _uiState.update { it.copy(cards = it.cards + nachschub) }
            } else {
                // Nichts mehr da, was passt - etwa wenn nur Getraenke gesucht werden. Ohne diese
                // Merke wuerde nach jeder Karte wieder vergeblich gefragt.
                vorratLeer = true
                beendenWennNichtsMehrKommt()
            }
            nachschubLaeuft = false
            nachladenOderPruefen()
        }
    }

    /**
     * Ist der Vorrat erschoepft und der Stapel leer, endet die Runde mit dem, was da ist -
     * statt mit einem Topf, der fuer immer brodelt.
     */
    private fun beendenWennNichtsMehrKommt() {
        _uiState.update { state ->
            if (state.step == DeckStep.WISCHEN && vorratLeer && state.cards.isEmpty()) {
                state.copy(step = DeckStep.FERTIG, canUndo = false, exhausted = true)
            } else {
                state
            }
        }
    }

    private fun nachladenOderPruefen() {
        if (_uiState.value.rules.needsPage) kartenPruefen() else bilderNachladen()
        beendenWennNichtsMehrKommt()
    }

    /**
     * Prueft die naechsten Karten gegen die Regeln der Runde, bevor sie erscheinen.
     *
     * Die Pruefung laedt die Rezeptseite und bringt damit auch gleich das Bild mit. Eine
     * Karte, die nicht passt oder sich nicht pruefen laesst, faellt aus dem Stapel.
     */
    private fun kartenPruefen() {
        val state = _uiState.value
        val offen = state.cards
            .filterNot { it.url in state.approved || it.url in pruefungVersucht }
            .take(PRUEF_VORAUS)
        if (offen.isEmpty()) return
        pruefungVersucht += offen.map { it.url }

        val meineRunde = runde
        offen.forEach { eintrag ->
            viewModelScope.launch {
                val ergebnis = pruefSchranke.withPermit { checkCard(eintrag.url, state.rules) }
                if (meineRunde != runde) return@launch
                val passt = ergebnis.allowed && (eintrag.imageUrl != null || ergebnis.imageUrl != null)
                _uiState.update { aktuell ->
                    if (passt) {
                        aktuell.copy(
                            approved = aktuell.approved + eintrag.url,
                            cards = aktuell.cards.map { karte ->
                                if (karte.url == eintrag.url && karte.imageUrl == null) {
                                    karte.copy(imageUrl = ergebnis.imageUrl)
                                } else {
                                    karte
                                }
                            },
                        )
                    } else {
                        aktuell.copy(cards = aktuell.cards.filterNot { it.url == eintrag.url })
                    }
                }
                if (!passt) {
                    refillWennNoetig()
                    kartenPruefen()
                    beendenWennNichtsMehrKommt()
                }
            }
        }
    }

    /**
     * Zieht den Stapel neu, wenn sich die Vorlieben geaendert haben, waehrend eine Runde laeuft.
     *
     * Gemeldet war: Pilzgerichte trotz "keine Pilze". Die Regeln einer Runde wurden nur beim
     * Start gelesen - und dieser Stapel bleibt liegen, wenn man zum Tab "Vorlieben" wechselt.
     * Wer mitten in der Runde etwas ausschloss, sah es danach weiter.
     *
     * Was schon gefallen hat, bleibt. Die offenen Karten gehen zurueck in den Vorrat und werden
     * nach den neuen Regeln neu gezogen und geprueft: Eine Karte, die unter den alten Regeln
     * bestanden hat, gilt damit nicht als bestanden.
     */
    private suspend fun vorliebenUebernehmen() {
        val state = _uiState.value
        if (state.step != DeckStep.WISCHEN && state.step != DeckStep.LADEN) return
        val rules = CardRules.of(profileRepository.profile.first(), state.course)
        if (rules == state.rules) return

        if (state.step == DeckStep.LADEN) {
            // Der Stapel wird gerade erst gezogen - dann gleich mit den neuen Angaben.
            onStart()
            return
        }

        neueRunde()
        gesehen -= state.cards.map { it.url }.toSet()
        pruefungVersucht.clear()
        region = if (rules.needsPage) LanguageRegion.DEUTSCH else null
        _uiState.update { it.copy(rules = rules, approved = emptySet(), cards = emptyList(), canUndo = false) }
        refillWennNoetig()
    }

    // Steht am Ende der Klasse, damit beim ersten Wert alle Felder oben schon bereit sind.
    init {
        viewModelScope.launch {
            profileRepository.profile
                .map { DietaryFilter.of(it) to it.appliances }
                .distinctUntilChanged()
                .collect { vorliebenUebernehmen() }
        }
    }

    private companion object {
        const val REFILL_SIZE = 15
        const val REFILL_THRESHOLD = 4

        /** Anfangsgroesse ohne Ziel - danach legt refillWennNoetig() laufend nach. */
        const val ENDLESS_INITIAL_SIZE = 20

        /** Fuer so viele Karten im Voraus wird ein fehlendes Bild geholt. */
        const val VORAUSLADEN = 5

        /** So viele Karten werden im Voraus gegen die Regeln der Runde geprueft. */
        const val PRUEF_VORAUS = 6

        /** So viele Rezeptseiten werden gleichzeitig geladen. */
        const val PARALLEL_CHECKS = 3
    }
}
