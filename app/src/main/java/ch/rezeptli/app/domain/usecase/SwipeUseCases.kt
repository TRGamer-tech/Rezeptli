package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.model.SwipeDecision
import ch.rezeptli.app.domain.model.SwipeMode
import ch.rezeptli.app.domain.repository.RecipeRepository
import ch.rezeptli.app.domain.repository.SwipeSessionRepository
import ch.rezeptli.app.domain.swipe.SwipeMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Eine frisch gestartete Session samt der Rezepte, durch die geswiped wird. */
data class StartedSwipeSession(
    val sessionId: Long,
    val recipeIds: List<Long>,
)

/**
 * Startet eine Swipe-Session fuer alle Rezepte, die zum Filter passen.
 *
 * Geladen werden nur die IDs - die Karteninhalte holt der Swipe-Screen nach Bedarf.
 */
class StartSwipeSessionUseCase @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val sessionRepository: SwipeSessionRepository,
) {
    suspend operator fun invoke(
        filter: RecipeFilter = RecipeFilter.NONE,
        mode: SwipeMode = SwipeMode.SOLO,
        startedAt: Long = System.currentTimeMillis(),
    ): StartedSwipeSession {
        val recipeIds = recipeRepository.getFilteredRecipeIds(filter)
        val sessionId = sessionRepository.startSession(mode, startedAt)
        return StartedSwipeSession(sessionId = sessionId, recipeIds = recipeIds)
    }
}

/** Haelt eine einzelne Wisch-Entscheidung fest. */
class RecordSwipeUseCase @Inject constructor(
    private val sessionRepository: SwipeSessionRepository,
) {
    suspend operator fun invoke(
        sessionId: Long,
        recipeId: Long,
        liked: Boolean,
        decidedAt: Long = System.currentTimeMillis(),
        participantId: String = SwipeDecision.LOCAL_PARTICIPANT,
    ) {
        sessionRepository.recordDecision(
            SwipeDecision(
                sessionId = sessionId,
                recipeId = recipeId,
                liked = liked,
                decidedAt = decidedAt,
                participantId = participantId,
            ),
        )
    }
}

/**
 * Nimmt die zuletzt getroffene Entscheidung zurueck und liefert das betroffene Rezept,
 * damit die UI die Karte zurueck auf den Stapel legen kann.
 */
class UndoLastSwipeUseCase @Inject constructor(
    private val sessionRepository: SwipeSessionRepository,
) {
    suspend operator fun invoke(
        sessionId: Long,
        participantId: String = SwipeDecision.LOCAL_PARTICIPANT,
    ): Long? {
        val last = sessionRepository
            .getDecisions(sessionId)
            .filter { it.participantId == participantId }
            .maxByOrNull { it.decidedAt }
            ?: return null

        sessionRepository.removeDecision(sessionId, last.recipeId, participantId)
        return last.recipeId
    }
}

/**
 * Die Treffer einer Session als fertige Liste.
 *
 * Im Solo-Modus sind das alle "Ja"-Rezepte; sobald mehrere Personen beteiligt sind,
 * bleiben nur die gemeinsamen Treffer uebrig - die Auswertung dafuer steckt im
 * [SwipeMatcher] und ist unabhaengig vom Modus.
 */
class ObserveSwipeMatchesUseCase @Inject constructor(
    private val sessionRepository: SwipeSessionRepository,
    private val recipeRepository: RecipeRepository,
    private val matcher: SwipeMatcher,
) {
    operator fun invoke(sessionId: Long): Flow<List<RecipeSummary>> =
        sessionRepository.observeDecisions(sessionId).map { decisions ->
            recipeRepository.getSummaries(matcher.commonMatches(decisions))
        }
}

/** Beobachtet den Fortschritt einer Session (wie viele Rezepte sind entschieden). */
class ObserveSwipeDecisionsUseCase @Inject constructor(
    private val sessionRepository: SwipeSessionRepository,
) {
    operator fun invoke(sessionId: Long): Flow<List<SwipeDecision>> =
        sessionRepository.observeDecisions(sessionId)
}

/** Schliesst eine Session ab. */
class FinishSwipeSessionUseCase @Inject constructor(
    private val sessionRepository: SwipeSessionRepository,
) {
    suspend operator fun invoke(sessionId: Long, finishedAt: Long = System.currentTimeMillis()) {
        sessionRepository.finishSession(sessionId, finishedAt)
    }
}
