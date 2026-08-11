package ch.rezeptli.app.fake

import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.PairingResult
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedSession
import ch.rezeptli.app.domain.multiplayer.SharedSessionState
import ch.rezeptli.app.domain.multiplayer.SharedVote
import ch.rezeptli.app.domain.repository.PairingRepository

/**
 * Ein Pairing-Dienst im Speicher.
 *
 * Er ahmt die Regeln des echten Dienstes nach - vor allem die wichtigste: Treffer gibt
 * es erst, wenn alle entschieden haben.
 */
class FakePairingRepository(
    private val pool: List<SharedRecipe> = emptyList(),
    var failWith: PairingError? = null,
) : PairingRepository {
    var createdSession: SharedSession? = null
        private set
    var sentVotes: List<SharedVote> = emptyList()
        private set
    var markedFinished = false
        private set
    var closedCode: String? = null
        private set

    /** Was der Dienst als naechstes ueber den Stand meldet. */
    var state: SharedSessionState = SharedSessionState(
        code = CODE,
        participants = 1,
        finished = 0,
        allFinished = false,
    )

    override suspend fun createSession(recipes: List<SharedRecipe>): PairingResult<SharedSession> {
        failWith?.let { return PairingResult.Failure(it) }
        if (recipes.isEmpty()) return PairingResult.Failure(PairingError.NO_RECIPES)

        val session = SharedSession(code = CODE, expiresAt = EXPIRY, recipes = recipes)
        createdSession = session
        return PairingResult.Success(session)
    }

    override suspend fun joinSession(code: String): PairingResult<SharedSession> {
        failWith?.let { return PairingResult.Failure(it) }
        if (code != CODE) return PairingResult.Failure(PairingError.UNKNOWN_CODE)

        return PairingResult.Success(SharedSession(code = CODE, expiresAt = EXPIRY, recipes = pool))
    }

    override suspend fun sendVotes(
        code: String,
        votes: List<SharedVote>,
        finished: Boolean,
    ): PairingResult<Unit> {
        failWith?.let { return PairingResult.Failure(it) }
        sentVotes = votes
        markedFinished = finished
        return PairingResult.Success(Unit)
    }

    override suspend fun sessionState(code: String): PairingResult<SharedSessionState> {
        failWith?.let { return PairingResult.Failure(it) }
        return PairingResult.Success(state)
    }

    override suspend fun closeSession(code: String): PairingResult<Unit> {
        closedCode = code
        return PairingResult.Success(Unit)
    }

    override suspend fun participantId(): String = "test-teilnehmer"

    companion object {
        const val CODE = "ABC123"
        const val EXPIRY = 9_999_999_999_999L
    }
}
