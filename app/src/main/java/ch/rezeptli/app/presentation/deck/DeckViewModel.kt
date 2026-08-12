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
    val target: Int = DEFAULT_TARGET,
    val cards: List<DeckEntry> = emptyList(),
    val liked: List<DeckEntry> = emptyList(),
    val isSaving: Boolean = false,
    val savedCount: Int = 0,
    val loadFailed: Boolean = false,
) {
    /** Die noch offenen Karten, in der Form, die der bestehende Stapel erwartet. */
    val remainingCards: List<RecipeSummary>
        get() = cards.map { eintrag ->
            RecipeSummary(
                id = eintrag.url.hashCode().toLong(),
                title = eintrag.title,
                photoUri = eintrag.imageUrl,
                prepTimeMinutes = null,
            )
        }

    val foundCount: Int get() = liked.size

    /** Wie viel vom Ziel geschafft ist - fuer den Fortschrittsbalken. */
    val progress: Float get() = if (target <= 0) 0f else (foundCount.toFloat() / target).coerceIn(0f, 1f)

    val isEmpty: Boolean get() = cards.isEmpty()
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

    fun onTargetChange(value: Int) {
        _uiState.update { it.copy(target = value.coerceAtLeast(1)) }
    }

    fun onStart() {
        val target = _uiState.value.target
        _uiState.update { it.copy(step = DeckStep.LADEN, liked = emptyList(), loadFailed = false) }

        viewModelScope.launch {
            // Etwas mehr Karten als Ziele: Es wird ja auch abgelehnt.
            val deck = buildDeck(size = deckSizeFor(target), exclude = gesehen)
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

        val fertig = neuGemocht.size >= state.target
        _uiState.update {
            it.copy(
                cards = rest,
                liked = neuGemocht,
                step = if (fertig) DeckStep.FERTIG else it.step,
            )
        }

        // Geht der Stapel aus, bevor das Ziel steht, wird nachgelegt.
        if (!fertig && rest.size <= REFILL_THRESHOLD) nachlegen()
        if (!fertig) bilderNachladen()
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
     * Manche Quellen nennen in ihrem Verzeichnis kein Bild; deren Karten haetten
     * sonst dauerhaft nur die Platzhalterflaeche. Geholt wird nur fuer die naechsten
     * paar Karten und immer im Hintergrund - keine Karte wartet auf ihr Bild, und
     * fuer abgelehnte Karten wird nichts geladen, was niemand sieht.
     */
    private fun bilderNachladen() {
        val offen = _uiState.value.cards
            .take(VORAUSLADEN)
            .filter { it.imageUrl == null && it.url !in bildVersucht }
        if (offen.isEmpty()) return

        bildVersucht += offen.map { it.url }

        offen.forEach { eintrag ->
            viewModelScope.launch {
                val bild = webRepository.cardImage(eintrag.url) ?: return@launch

                _uiState.update { state ->
                    state.copy(
                        cards = state.cards.map { karte ->
                            if (karte.url == eintrag.url) karte.copy(imageUrl = bild) else karte
                        },
                    )
                }
            }
        }
    }

    private fun nachlegen() {
        viewModelScope.launch {
            val nachschub = buildDeck(size = REFILL_SIZE, exclude = gesehen)
            if (nachschub.isEmpty()) return@launch

            gesehen += nachschub.map { it.url }
            _uiState.update { it.copy(cards = it.cards + nachschub) }
        }
    }

    private companion object {
        /** Pro gesuchtem Gericht ein paar Karten - abgelehnt wird oefter als behalten. */
        const val CARDS_PER_TARGET = 6
        const val MIN_DECK = 12
        const val REFILL_SIZE = 15
        const val REFILL_THRESHOLD = 4

        /** Fuer so viele Karten im Voraus wird ein fehlendes Bild geholt. */
        const val VORAUSLADEN = 3

        fun deckSizeFor(target: Int): Int = (target * CARDS_PER_TARGET).coerceAtLeast(MIN_DECK)
    }
}
