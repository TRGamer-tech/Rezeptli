package ch.rezeptli.app.data.repository

import ch.rezeptli.app.data.local.dao.SwipeSessionDao
import ch.rezeptli.app.data.local.entity.SwipeSessionEntity
import ch.rezeptli.app.data.mapper.toDomain
import ch.rezeptli.app.data.mapper.toEntity
import ch.rezeptli.app.di.IoDispatcher
import ch.rezeptli.app.domain.model.SwipeDecision
import ch.rezeptli.app.domain.model.SwipeMode
import ch.rezeptli.app.domain.model.SwipeSession
import ch.rezeptli.app.domain.repository.SwipeSessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SwipeSessionRepositoryImpl @Inject constructor(
    private val swipeSessionDao: SwipeSessionDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SwipeSessionRepository {
    override suspend fun startSession(mode: SwipeMode, startedAt: Long): Long = withContext(ioDispatcher) {
        swipeSessionDao.insertSession(
            SwipeSessionEntity(startedAt = startedAt, finishedAt = null, mode = mode),
        )
    }

    override suspend fun finishSession(sessionId: Long, finishedAt: Long) = withContext(ioDispatcher) {
        swipeSessionDao.finishSession(sessionId, finishedAt)
    }

    override suspend fun getSession(sessionId: Long): SwipeSession? = withContext(ioDispatcher) {
        swipeSessionDao.session(sessionId)?.toDomain()
    }

    override fun observeSession(sessionId: Long): Flow<SwipeSession?> =
        swipeSessionDao.observeSession(sessionId).map { it?.toDomain() }

    override fun observeDecisions(sessionId: Long): Flow<List<SwipeDecision>> =
        swipeSessionDao.observeResults(sessionId).map { results -> results.map { it.toDomain() } }

    override suspend fun getDecisions(sessionId: Long): List<SwipeDecision> = withContext(ioDispatcher) {
        swipeSessionDao.results(sessionId).map { it.toDomain() }
    }

    override suspend fun recordDecision(decision: SwipeDecision) = withContext(ioDispatcher) {
        swipeSessionDao.upsertResult(decision.toEntity())
    }

    override suspend fun removeDecision(
        sessionId: Long,
        recipeId: Long,
        participantId: String,
    ) = withContext(ioDispatcher) {
        swipeSessionDao.deleteResult(sessionId, participantId, recipeId)
    }

    override suspend fun deleteSession(sessionId: Long) = withContext(ioDispatcher) {
        swipeSessionDao.deleteSession(sessionId)
    }
}
