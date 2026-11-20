package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.di.IoDispatcher
import ch.rezeptli.app.domain.deck.CuratedDeckBuilder
import ch.rezeptli.app.domain.deck.DeckEntry
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.PairingResult
import ch.rezeptli.app.domain.multiplayer.RoundKeys
import ch.rezeptli.app.domain.multiplayer.RoundOffer
import ch.rezeptli.app.domain.multiplayer.RoundPhase
import ch.rezeptli.app.domain.multiplayer.RoundPoll
import ch.rezeptli.app.domain.multiplayer.RoundSource
import ch.rezeptli.app.domain.multiplayer.RoundState
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.plan.CardRules
import ch.rezeptli.app.domain.plan.CheckRecipeCardUseCase
import ch.rezeptli.app.domain.plan.RecipeClassifier
import ch.rezeptli.app.domain.profile.LanguageRegion
import ch.rezeptli.app.domain.repository.PairingRepository
import ch.rezeptli.app.domain.repository.RecipeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Fragt den Stand der Runde regelmaessig ab.
 *
 * Abgefragt wird nur, solange jemand hinschaut - der Flow endet, sobald niemand mehr
 * sammelt. Das Intervall ist bewusst gemuetlich: Es geht darum zu merken, dass jemand
 * beigetreten ist oder das Ziel erreicht wurde, nicht um Millisekunden. Kennt das Geraet
 * den Stand schon, antwortet der Dienst nur mit "unveraendert".
 *
 * Anders als frueher wird ein Fehler nicht verschluckt: Wer wartet, soll erfahren, dass
 * die Verbindung weg ist - und dass die Runde nicht mehr existiert, statt ewig zu warten.
 *
 * [known] nennt, was das Geraet schon hat: die Nummer des letzten Stands und die Runde,
 * deren Stapel es haelt. Die Angabe wird vor jeder Abfrage neu gelesen, weil auch die
 * Antworten auf eigene Aktionen (eine Stimme, ein Beitrag) den Stand weiterdrehen.
 */
class ObserveRoundUseCase @Inject constructor(
    private val pairingRepository: PairingRepository,
) {
    data class Known(val revision: Int?, val round: Int)

    operator fun invoke(code: String, known: () -> Known): Flow<RoundPoll> = flow {
        while (true) {
            val bekannt = known()
            when (val result = pairingRepository.roundState(code, bekannt.revision, bekannt.round)) {
                is PairingResult.Success -> {
                    val state = result.value
                    emit(if (state == null) RoundPoll.Quiet else RoundPoll.Changed(state))
                    // Ist die Runde zu Ende, aendert sich nichts mehr - dann hoert das
                    // Nachfragen auf, statt den Dienst weiter zu beschaeftigen.
                    if (state?.phase == RoundPhase.BEENDET) return@flow
                }

                is PairingResult.Failure -> {
                    emit(RoundPoll.Problem(result.error))
                    // Die Runde gibt es nicht mehr, oder die App ist zu alt: Weiterfragen aendert daran nichts.
                    if (result.error in FATAL) return@flow
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_500L
        val FATAL = setOf(PairingError.UNKNOWN_CODE, PairingError.EXPIRED, PairingError.OUTDATED)
    }
}

/**
 * Stellt zusammen, was dieses Geraet zum gemeinsamen Stapel beisteuert.
 *
 * Jedes Geraet steuert nur Karten bei, die zu den Angaben BEIDER passen - die stehen
 * vereinigt im Stand der Runde. Deshalb sehen beide exakt denselben Stapel: Niemand muss
 * still ueberspringen, was die andere Person ausgeschlossen hat, und "kein Fisch" der einen
 * gilt wirklich fuer beide.
 *
 * Zuerst kommen die eigenen gespeicherten Rezepte - deren Zutaten kennt das Geraet, die
 * Pruefung ist also verlaesslich und braucht kein Netz. Danach neue aus dem Verzeichnis,
 * genau wie beim Wischen allein: am Titel vorsortiert und, sobald Ernaehrung,
 * Unvertraeglichkeiten oder Abneigungen im Spiel sind, an der Rezeptseite geprueft.
 *
 * Die Geraete der Kueche bleiben hier aussen vor: Sie gehoeren zu einer Person, und zu
 * zweit steht nicht fest, in wessen Kueche gekocht wird.
 */
class BuildRoundContributionUseCase @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val pairingRepository: PairingRepository,
    private val classifier: RecipeClassifier,
    private val buildDeck: BuildSwipeDeckUseCase,
    private val checkCard: CheckRecipeCardUseCase,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {
    /**
     * [selection] sind Karten, die gerade in einer eigenen Wischrunde gefallen haben - sie
     * kommen mit in den Stapel, auch wenn sie noch nicht gespeichert sind.
     */
    suspend operator fun invoke(round: RoundState, selection: List<SharedRecipe> = emptyList()): List<RoundOffer> {
        val rules = CardRules(course = round.course, filter = round.restrictions)
        val chosen = fromSelection(selection, rules)
        val rest = when (round.source) {
            RoundSource.GESPEICHERT -> saved(rules)
            RoundSource.NEU -> fresh(rules, missing = round.target - round.matchCount)
        }
        return (chosen + rest).distinctBy { it.key }.take(MAX_OFFERS)
    }

    /** Die eigene Sammlung, in zufaelliger Reihenfolge - soweit sie zu den Angaben beider passt. */
    private suspend fun saved(rules: CardRules): List<RoundOffer> = withContext(dispatcher) {
        val participant = pairingRepository.participantId()
        val offers = mutableListOf<RoundOffer>()
        for (id in recipeRepository.getFilteredRecipeIds(RecipeFilter()).take(SAVED_SCAN)) {
            if (offers.size >= MAX_SAVED) break
            val recipe = recipeRepository.getRecipe(id) ?: continue
            if (rules.allows(classifier.classify(recipe))) offers += recipe.toOffer(participant)
        }
        offers
    }

    /**
     * Zieht neue Karten aus dem Verzeichnis.
     *
     * Ohne Angaben genuegt der Titel. Sonst wird Seite um Seite geprueft, bis genug
     * beisammen sind - in kleinen Schueben, damit nicht vierzig Seiten geladen werden, wenn
     * die ersten zwoelf schon reichen, und mit einer Zeitgrenze: Lieber ein kleinerer
     * Stapel als zwei Leute, die auf einen Ladebalken schauen.
     */
    private suspend fun fresh(rules: CardRules, missing: Int): List<RoundOffer> {
        // Beide Geraete steuern bei - jedes also die Haelfte dessen, was der Stapel braucht.
        // Vorher brachte jedes den ganzen Stapel mit: doppelt so viele Seiten zu pruefen,
        // doppelt so lange zu warten, und am Ende doppelt so viele Karten wie noetig.
        val wanted = ((CuratedDeckBuilder.deckSizeFor(missing.coerceAtLeast(1)) + 1) / 2).coerceAtLeast(MIN_FRESH)
        val drawn = buildDeck(
            size = wanted * if (rules.needsPage) DRAW_FACTOR_CHECKED else DRAW_FACTOR,
            exclude = recipeRepository.savedSourceUrls(),
            // Zutaten lassen sich nur auf Deutsch pruefen.
            onlyRegion = if (rules.needsPage) LanguageRegion.DEUTSCH else null,
            rules = rules,
        )

        val passing = if (rules.needsPage) {
            checked(drawn, rules, wanted)
        } else {
            // Ohne Bild keine Karte - anders als allein gibt es hier kein Nachladen im Hintergrund.
            drawn.filter { it.imageUrl != null }.take(wanted)
        }
        return passing.map { entry ->
            RoundOffer(
                key = RoundKeys.forUrl(entry.url),
                title = entry.title,
                sourceUrl = entry.url,
                imageUrl = entry.imageUrl,
            )
        }
    }

    private suspend fun checked(entries: List<DeckEntry>, rules: CardRules, wanted: Int): List<DeckEntry> {
        val passing = mutableListOf<DeckEntry>()
        withTimeoutOrNull(CHECK_BUDGET_MS) {
            for (batch in entries.chunked(CHECK_BATCH)) {
                if (passing.size >= wanted) break
                passing += coroutineScope {
                    batch
                        .map { entry ->
                            async {
                                val check = withTimeoutOrNull(PAGE_TIMEOUT_MS) { checkCard(entry.url, rules) }
                                val image = entry.imageUrl ?: check?.imageUrl
                                if (check?.allowed == true && image != null) entry.copy(imageUrl = image) else null
                            }
                        }.awaitAll()
                        .filterNotNull()
                }
            }
        }
        return passing.take(wanted)
    }

    /**
     * Karten aus der eigenen Wischrunde. Sie haben dort nur die eigenen Vorlieben bestanden -
     * hier muessen sie auch zu denen der anderen Person passen.
     */
    private suspend fun fromSelection(selection: List<SharedRecipe>, rules: CardRules): List<RoundOffer> =
        selection.mapNotNull { card ->
            val url = card.sourceUrl ?: return@mapNotNull null
            val allowed = if (rules.needsPage) {
                withTimeoutOrNull(PAGE_TIMEOUT_MS) { checkCard(url, rules) }?.allowed == true
            } else {
                rules.allows(classifier.classifyTitle(card.title))
            }
            if (!allowed) return@mapNotNull null
            RoundOffer(
                key = RoundKeys.forUrl(url),
                title = card.title,
                sourceUrl = url,
                imageUrl = card.imageUrl,
                prepTimeMinutes = card.prepTimeMinutes,
            )
        }

    private fun Recipe.toOffer(participantId: String): RoundOffer = RoundOffer(
        key = sourceUrl?.let(RoundKeys::forUrl) ?: RoundKeys.forOwn(participantId, id),
        title = title,
        sourceUrl = sourceUrl,
        // Ein Foto vom eigenen Telefon nuetzt dem anderen nichts - geteilt wird nur ein Bild aus dem Netz.
        imageUrl = photoUri?.takeIf { it.startsWith("http://") || it.startsWith("https://") },
        prepTimeMinutes = prepTimeMinutes,
    )

    private companion object {
        /** Gleicher Wert wie MAX_BEITRAG im Pairing-Dienst. */
        const val MAX_OFFERS = 80

        /** So viele gespeicherte Rezepte steuert eine Person hoechstens bei. */
        const val MAX_SAVED = 60

        /** So viele Rezepte der Sammlung werden dafuer hoechstens angeschaut. */
        const val SAVED_SCAN = 300

        /** Es wird mehr gezogen als gebraucht: Karten ohne Bild fallen heraus. */
        const val DRAW_FACTOR = 2

        /** Mit Pruefung faellt zusaetzlich heraus, was nicht passt oder sich nicht laden laesst. */
        const val DRAW_FACTOR_CHECKED = 4

        /** Weniger als das lohnt den Stapel nicht - auch wenn nur noch ein Gericht fehlt. */
        const val MIN_FRESH = 8

        /** So viele Rezeptseiten werden gleichzeitig geprueft. Sie liegen bei verschiedenen Anbietern. */
        const val CHECK_BATCH = 10

        /** Eine Seite, die so lange nicht antwortet, haelt nur auf - die naechste Karte ist schneller. */
        const val PAGE_TIMEOUT_MS = 7_000L
        const val CHECK_BUDGET_MS = 20_000L
    }
}
