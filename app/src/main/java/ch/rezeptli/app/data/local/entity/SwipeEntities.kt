package ch.rezeptli.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ch.rezeptli.app.domain.model.SwipeMode

@Entity(tableName = "swipe_sessions")
data class SwipeSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val startedAt: Long,
    val finishedAt: Long?,
    val mode: SwipeMode,
)

/**
 * Eine einzelne Wisch-Entscheidung.
 *
 * [participantId] gehoert von Anfang an zum Primaerschluessel, damit der spaetere
 * Mehrspieler-Modus die Entscheidungen einer zweiten Person in dieselbe Session
 * schreiben kann, ohne dass dafuer eine Schema-Migration noetig wird.
 */
@Entity(
    tableName = "swipe_results",
    primaryKeys = ["sessionId", "participantId", "recipeId"],
    foreignKeys = [
        ForeignKey(
            entity = SwipeSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("recipeId")],
)
data class SwipeResultEntity(
    val sessionId: Long,
    val participantId: String,
    val recipeId: Long,
    val liked: Boolean,
    val decidedAt: Long,
)
