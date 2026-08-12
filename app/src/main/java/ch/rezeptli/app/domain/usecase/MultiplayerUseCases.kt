package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.multiplayer.PairingResult
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedSession
import ch.rezeptli.app.domain.multiplayer.SharedSessionState
import ch.rezeptli.app.domain.multiplayer.SharedVote
import ch.rezeptli.app.domain.repository.PairingRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Eroeffnet eine gemeinsame Runde.
 *
 * Geteilt wird nur, was zum Entscheiden noetig ist: Titel, Bild und Zeit. Zutaten und
 * Zubereitung bleiben auf dem Geraet - die zweite Person stimmt ueber Rezepte ab, sie
 * bekommt sie nicht.
 */
class StartSharedSessionUseCase @Inject constructor(
    private val pairingRepository: PairingRepository,
) {
    suspend operator fun invoke(recipes: List<SharedRecipe>): PairingResult<SharedSession> =
        pairingRepository.createSession(recipes.take(MAX_GETEILTE))

    companion object {
        /** Gleicher Wert wie MAX_REZEPTE im Pairing-Dienst. */
        const val MAX_GETEILTE = 200
    }
}

/**
 * Tritt einer Runde bei.
 *
 * Bringt [recipes] eigene Vorschlaege mit, mischt der Dienst sie in den Topf - danach
 * stimmen beide Seiten ueber dieselbe, gemeinsame Auswahl ab statt nur ueber die des
 * Gastgebers.
 */
class JoinSharedSessionUseCase @Inject constructor(
    private val pairingRepository: PairingRepository,
) {
    suspend operator fun invoke(
        code: String,
        recipes: List<SharedRecipe> = emptyList(),
    ): PairingResult<SharedSession> {
        val begrenzt = recipes.take(StartSharedSessionUseCase.MAX_GETEILTE)
        return pairingRepository.joinSession(code, begrenzt)
    }
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
