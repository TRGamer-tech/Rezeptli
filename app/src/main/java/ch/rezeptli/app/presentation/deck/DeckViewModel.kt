package ch.rezeptli.app.presentation.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.deck.DeckEntry
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedSelectionHolder
import ch.rezeptli.app.domain.repository.WebRecipeRepository
import ch.rezeptli.app.domain.usecase.BuildSwipeDeckUseCase
import ch.rezeptli.app.domain.usecase.LoadWebRecipeUseCase
import ch.rezeptli.app.domain.usecase.SaveRecipeUseCase
import ch.rezeptli.app.domain.usecase.WebImportOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Wo in einer Wischrunde man gerade steht. */
enum class DeckStep {
    /** Wie viele Gerichte sollen es werden? Steht vor jeder Runde. */
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
    val cards: List<DeckEntry> = emptyList(),
    val liked: List<DeckEntry> = emptyList(),
    val isSaving: Boolean = false,
    val savedCount: Int = 0,
    val loadFailed: Boolean = false,
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
            .filter { it.imageUrl != null }
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
 * Die Runde beginnt mit der Frage nach der Anzahl - jedes Mal, nicht einmalig in den
 * Einstellungen: Wie viel gekocht wird, haengt an der Woche, nicht an der Person.
 *
 * Ist die Anzahl erreicht, endet die Runde von selbst. Weiterwischen, bis der Stapel
 * leer ist, waere Arbeit ohne Ziel.
 */
@HiltViewModel
class DeckViewModel @Inject constructor(
    private val buildDeck: BuildSwipeDeckUseCase,
    private val selectionHolder: SharedSelectionHolder,
    private val webRepository: WebRecipeRepository,
    private val loadWebRecipe: LoadWebRecipeUseCase,
    private val saveRecipe: SaveRecipeUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DeckUiState())
    val uiState: StateFlow<DeckUiState> = _uiState.asStateFlow()

    /** Schon gesehene Adressen - damit ein Nachschlag keine Wiederholung wird. */
    private val gesehen = mutableSetOf<String>()

    /** Adressen, fuer die ein Bild schon versucht wurde - erfolgreich oder nicht. */
    private val bildVersucht = mutableSetOf<String>()

    /** `null` waehlt die endlose Runde ohne Ziel. */
    fun onTargetChange(value: Int?) {
        _uiState.update { it.copy(target = value?.coerceAtLeast(1)) }
    }

    fun onStart() {
        val target = _uiState.value.target
        _uiState.update { it.copy(step = DeckStep.LADEN, liked = emptyList(), loadFailed = false) }

        viewModelScope.launch {
            // Etwas mehr Karten als Ziele: Es wird ja auch abgelehnt. Ohne Ziel gibt es
            // keine Zahl, von der sich das ableiten liesse - dann ein fester Vorrat.
            val groesse = target?.let { deckSizeFor(it) } ?: ENDLESS_INITIAL_SIZE
            val deck = buildDeck(size = groesse, exclude = gesehen)
            gesehen += deck.map { it.url }

            _uiState.update {
                it.copy(
                    step = if (deck.isEmpty()) DeckStep.ANZAHL else DeckStep.WISCHEN,
                    cards = deck,
                    loadFailed = deck.isEmpty(),
                )
            }
            bilderNachladen()
        }
    }

    fun onSwiped(entry: DeckEntry, liked: Boolean) {
        val state = _uiState.value
        val rest = state.cards.filterNot { it.url == entry.url }
        val neuGemocht = if (liked) state.liked + entry else state.liked

        // Ohne Ziel (target == null) ist keine Anzahl je "erreicht" - die Runde endet
        // nur, wenn die Person selbst auf "Fertig" tippt.
        val ziel = state.target
        val fertig = ziel != null && neuGemocht.size >= ziel
        _uiState.update {
            it.copy(
                cards = rest,
                liked = neuGemocht,
                step = if (fertig) DeckStep.FERTIG else it.step,
            )
        }

        if (!fertig) {
            bilderNachladen()
            refillWennNoetig()
        }
    }

    /** Auch ohne erreichtes Ziel darf man aufhoeren - mit dem, was man hat. */
    fun onFinishEarly() {
        _uiState.update { it.copy(step = DeckStep.FERTIG) }
    }

    fun onRestart() {
        _uiState.value = DeckUiState(target = _uiState.value.target)
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
            var gespeichert = 0
            _uiState.value.liked.forEach { eintrag ->
                when (val ergebnis = loadWebRecipe(eintrag.url)) {
                    is WebImportOutcome.Loaded -> {
                        saveRecipe(ergebnis.recipe)
                        gespeichert += 1
                        _uiState.update { it.copy(savedCount = gespeichert) }
                    }

                    is WebImportOutcome.Failed -> Unit
                }
            }
            _uiState.update { it.copy(isSaving = false) }
            onDone(gespeichert)
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

        offen.forEach { eintrag ->
            viewModelScope.launch {
                val bild = webRepository.cardImage(eintrag.url)

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

    /**
     * Legt nach, wenn zu wenige sichtbare (bebilderte) Karten uebrig sind.
     *
     * Gezaehlt werden nur Karten mit Bild - eine Karte, die noch auf ihres wartet,
     * zaehlt hier nicht mit. Sonst saehe der Stapel voll aus, waere aber leer.
     */
    private fun refillWennNoetig() {
        if (nachschubLaeuft) return
        val sichtbar = _uiState.value.cards.count { it.imageUrl != null }
        if (sichtbar > REFILL_THRESHOLD) return

        nachschubLaeuft = true
        viewModelScope.launch {
            val nachschub = buildDeck(size = REFILL_SIZE, exclude = gesehen)
            gesehen += nachschub.map { it.url }
            if (nachschub.isNotEmpty()) {
                _uiState.update { it.copy(cards = it.cards + nachschub) }
            }
            nachschubLaeuft = false
            bilderNachladen()
        }
    }

    private companion object {
        /** Pro gesuchtem Gericht ein paar Karten - abgelehnt wird oefter als behalten. */
        const val CARDS_PER_TARGET = 6
        const val MIN_DECK = 12
        const val REFILL_SIZE = 15
        const val REFILL_THRESHOLD = 4

        /** Anfangsgroesse ohne Ziel - danach legt refillWennNoetig() laufend nach. */
        const val ENDLESS_INITIAL_SIZE = 20

        /** Fuer so viele Karten im Voraus wird ein fehlendes Bild geholt. */
        const val VORAUSLADEN = 5

        fun deckSizeFor(target: Int): Int = (target * CARDS_PER_TARGET).coerceAtLeast(MIN_DECK)
    }
}
