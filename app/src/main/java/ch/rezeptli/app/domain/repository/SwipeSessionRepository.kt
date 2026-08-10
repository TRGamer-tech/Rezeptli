package ch.rezeptli.app.domain.repository

import ch.rezeptli.app.domain.model.SwipeDecision
import ch.rezeptli.app.domain.model.SwipeMode
import ch.rezeptli.app.domain.model.SwipeSession
import kotlinx.coroutines.flow.Flow

/** Zugriff auf Swipe-Sessions und die darin getroffenen Entscheidungen. */
interface SwipeSessionRepository {

    suspend fun startSession(mode: SwipeMode = SwipeMode.SOLO, startedAt: Long): Long

    suspend fun finishSession(sessionId: Long, finishedAt: Long)

    suspend fun getSession(sessionId: Long): SwipeSession?

    fun observeSession(sessionId: Long): Flow<SwipeSession?>

    fun observeDecisions(sessionId: Long): Flow<List<SwipeDecision>>

    suspend fun getDecisions(sessionId: Long): List<SwipeDecision>

    /** Speichert eine Entscheidung; eine erneute Entscheidung zum selben Rezept ersetzt sie. */
    suspend fun recordDecision(decision: SwipeDecision)

    /** Nimmt eine Entscheidung zurueck (Undo). */
    suspend fun removeDecision(sessionId: Long, recipeId: Long, participantId: String)

    suspend fun deleteSession(sessionId: Long)
}
