package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.multiplayer.PairingResult
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedSession
import ch.rezeptli.app.domain.multiplayer.SharedSessionState
import ch.rezeptli.app.domain.multiplayer.SharedVote
import ch.rezeptli.app.domain.repository.PairingRepository
import ch.rezeptli.app.domain.repository.RecipeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Eroeffnet eine gemeinsame Runde aus der eigenen Sammlung.
 *
 * Geteilt wird nur, was zum Entscheiden noetig ist: Titel, Bild und Zeit. Zutaten und
 * Zubereitung bleiben auf dem Geraet - die zweite Person stimmt ueber Rezepte ab, sie
 * bekommt sie nicht.
 */
class StartSharedSessionUseCase @Inject constructor(
    private val pairingRepository: PairingRepository,
    private val recipeRepository: RecipeRepository,
) {
    suspend operator fun invoke(filter: RecipeFilter): PairingResult<SharedSession> {
        val ids = recipeRepository.getFilteredRecipeIds(filter)
        val summaries = recipeRepository.getSummaries(ids)

        return pairingRepository.createSession(summaries.map { it.toShared() })
    }

    private fun RecipeSummary.toShared(): SharedRecipe = SharedRecipe(
        recipeId = id,
        title = title,
        imageUrl = photoUri,
        prepTimeMinutes = prepTimeMinutes,
    )
}

class JoinSharedSessionUseCase @Inject constructor(
    private val pairingRepository: PairingRepository,
) {
    suspend operator fun invoke(code: String): PairingResult<SharedSession> =
        pairingRepository.joinSession(code)
}

class SendSharedVotesUseCase @Inject constructor(
    private val pairingRepository: PairingRepository,
) {
    suspend operator fun invoke(
        code: String,
        votes: List<SharedVote>,
        finished: Boolean,
    ): PairingResult<Unit> = pairingRepository.sendVotes(code, votes, finished)
}

class CloseSharedSessionUseCase @Inject constructor(
    private val pairingRepository: PairingRepository,
) {
    suspend operator fun invoke(code: String): PairingResult<Unit> =
        pairingRepository.closeSession(code)
}

/**
 * Fragt den Stand der Runde regelmaessig ab.
 *
 * Abgefragt wird nur, solange jemand hinschaut - der Flow endet, sobald niemand mehr
 * sammelt. Das Intervall ist bewusst gemuetlich: Es geht darum zu merken, dass jemand
 * beigetreten oder fertig ist, nicht um Millisekunden.
 */
class ObserveSharedSessionUseCase @Inject constructor(
    private val pairingRepository: PairingRepository,
) {
    operator fun invoke(code: String): Flow<SharedSessionState> = flow {
        while (true) {
            when (val result = pairingRepository.sessionState(code)) {
                is PairingResult.Success -> {
                    emit(result.value)
                    // Sind alle fertig, aendert sich nichts mehr - dann hoert das
                    // Nachfragen auf, statt den Dienst weiter zu beschaeftigen.
                    if (result.value.allFinished) return@flow
                }

                is PairingResult.Failure -> Unit
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 3_000L
    }
}
