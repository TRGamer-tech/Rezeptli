package ch.rezeptli.app.fake

import ch.rezeptli.app.domain.model.SwipeDecision
import ch.rezeptli.app.domain.model.SwipeMode
import ch.rezeptli.app.domain.model.SwipeSession
import ch.rezeptli.app.domain.repository.SwipeSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-Memory-Ersatz fuer Swipe-Sessions. */
class FakeSwipeSessionRepository : SwipeSessionRepository {
    private val sessions = MutableStateFlow<Map<Long, SwipeSession>>(emptyMap())
    private val decisions = MutableStateFlow<List<SwipeDecision>>(emptyList())
    private var nextSessionId = 1L

    override suspend fun startSession(mode: SwipeMode, startedAt: Long): Long {
        val id = nextSessionId++
        sessions.value = sessions.value + (id to SwipeSession(id = id, startedAt = startedAt, mode = mode))
        return id
    }

    override suspend fun finishSession(sessionId: Long, finishedAt: Long) {
        sessions.value[sessionId]?.let { session ->
            sessions.value = sessions.value + (sessionId to session.copy(finishedAt = finishedAt))
        }
    }

    override suspend fun getSession(sessionId: Long): SwipeSession? = sessions.value[sessionId]

    override fun observeSession(sessionId: Long): Flow<SwipeSession?> = sessions.map { it[sessionId] }

    override fun observeDecisions(sessionId: Long): Flow<List<SwipeDecision>> =
        decisions.map { all -> all.filter { it.sessionId == sessionId }.sortedBy { it.decidedAt } }

    override suspend fun getDecisions(sessionId: Long): List<SwipeDecision> =
        decisions.value.filter { it.sessionId == sessionId }.sortedBy { it.decidedAt }

    override suspend fun recordDecision(decision: SwipeDecision) {
        decisions.value = decisions.value.filterNot {
            it.sessionId == decision.sessionId &&
                it.recipeId == decision.recipeId &&
                it.participantId == decision.participantId
        } + decision
    }

    override suspend fun removeDecision(sessionId: Long, recipeId: Long, participantId: String) {
        decisions.value = decisions.value.filterNot {
            it.sessionId == sessionId && it.recipeId == recipeId && it.participantId == participantId
        }
    }

    override suspend fun deleteSession(sessionId: Long) {
        sessions.value = sessions.value - sessionId
        decisions.value = decisions.value.filterNot { it.sessionId == sessionId }
    }
}
