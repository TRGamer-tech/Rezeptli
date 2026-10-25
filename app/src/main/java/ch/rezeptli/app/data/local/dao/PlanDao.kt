package ch.rezeptli.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import ch.rezeptli.app.data.local.entity.PlanEntryEntity
import ch.rezeptli.app.data.local.entity.PlanEntryRow
import ch.rezeptli.app.data.local.entity.ShoppingItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PlanDao {
    @Query(
        """
        SELECT p.day AS day, p.recipeId AS recipeId, p.servings AS servings, p.cost AS cost, p.label AS label,
               r.title AS title, r.photoUri AS photoUri, r.prepTimeMinutes AS prepTimeMinutes
        FROM plan_entries p JOIN recipes r ON r.id = p.recipeId
        """,
    )
    abstract fun observeRows(): Flow<List<PlanEntryRow>>

    @Query(
        """
        SELECT p.day AS day, p.recipeId AS recipeId, p.servings AS servings, p.cost AS cost, p.label AS label,
               r.title AS title, r.photoUri AS photoUri, r.prepTimeMinutes AS prepTimeMinutes
        FROM plan_entries p JOIN recipes r ON r.id = p.recipeId
        """,
    )
    abstract suspend fun rows(): List<PlanEntryRow>

    @Query("DELETE FROM plan_entries")
    abstract suspend fun deleteEntries()

    @Query("DELETE FROM plan_entries WHERE day = :day")
    abstract suspend fun deleteEntry(day: String)

    @Insert
    abstract suspend fun insertEntries(entries: List<PlanEntryEntity>)

    @Query("DELETE FROM shopping_items WHERE fromPlan = 1")
    abstract suspend fun deletePlanItems()

    @Insert
    abstract suspend fun insertItems(items: List<ShoppingItemEntity>)

    /**
     * Ersetzt den ganzen Plan samt seinen Einkaufsposten in einer Transaktion.
     *
     * Von Hand ergaenzte Posten bleiben stehen; nur was vom Plan stammt, wird getauscht.
     */
    @Transaction
    open suspend fun replacePlan(entries: List<PlanEntryEntity>, items: List<ShoppingItemEntity>) {
        deleteEntries()
        deletePlanItems()
        if (entries.isNotEmpty()) insertEntries(entries.map { it.copy(id = 0L) })
        if (items.isNotEmpty()) insertItems(items.map { it.copy(id = 0L) })
    }

    /** Tauscht die Mahlzeit eines Tages und baut den Einkauf neu auf. */
    @Transaction
    open suspend fun replaceEntry(day: String, entry: PlanEntryEntity, items: List<ShoppingItemEntity>) {
        deleteEntry(day)
        insertEntries(listOf(entry.copy(id = 0L)))
        deletePlanItems()
        if (items.isNotEmpty()) insertItems(items.map { it.copy(id = 0L) })
    }
}
