package ch.rezeptli.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ch.rezeptli.app.data.local.entity.SwipeResultEntity
import ch.rezeptli.app.data.local.entity.SwipeSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SwipeSessionDao {
    @Insert
    suspend fun insertSession(session: SwipeSessionEntity): Long

    @Query("SELECT * FROM swipe_sessions WHERE id = :id")
    fun observeSession(id: Long): Flow<SwipeSessionEntity?>

    @Query("SELECT * FROM swipe_sessions WHERE id = :id")
    suspend fun session(id: Long): SwipeSessionEntity?

    @Query("SELECT * FROM swipe_sessions WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestUnfinishedSession(): SwipeSessionEntity?

    @Query("SELECT * FROM swipe_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int): Flow<List<SwipeSessionEntity>>

    @Query("UPDATE swipe_sessions SET finishedAt = :finishedAt WHERE id = :id")
    suspend fun finishSession(id: Long, finishedAt: Long)

    @Query("DELETE FROM swipe_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResult(result: SwipeResultEntity)

    @Query(
        """
        DELETE FROM swipe_results
        WHERE sessionId = :sessionId AND participantId = :participantId AND recipeId = :recipeId
        """,
    )
    suspend fun deleteResult(sessionId: Long, participantId: String, recipeId: Long)

    @Query("SELECT * FROM swipe_results WHERE sessionId = :sessionId ORDER BY decidedAt ASC")
    fun observeResults(sessionId: Long): Flow<List<SwipeResultEntity>>

    @Query("SELECT * FROM swipe_results WHERE sessionId = :sessionId ORDER BY decidedAt ASC")
    suspend fun results(sessionId: Long): List<SwipeResultEntity>
}
