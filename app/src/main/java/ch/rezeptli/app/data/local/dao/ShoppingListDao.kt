package ch.rezeptli.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ch.rezeptli.app.data.local.entity.ShoppingItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ShoppingListDao {
    @Query("SELECT * FROM shopping_items ORDER BY category, name COLLATE NOCASE ASC")
    abstract fun observeItems(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items ORDER BY category, name COLLATE NOCASE ASC")
    abstract suspend fun items(): List<ShoppingItemEntity>

    @Query("SELECT COUNT(*) FROM shopping_items WHERE isChecked = 0")
    abstract fun observeOpenCount(): Flow<Int>

    @Insert
    abstract suspend fun insert(items: List<ShoppingItemEntity>)

    @Update
    abstract suspend fun update(item: ShoppingItemEntity)

    @Query("UPDATE shopping_items SET isChecked = :checked WHERE id = :id")
    abstract suspend fun setChecked(id: Long, checked: Boolean)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM shopping_items WHERE isChecked = 1")
    abstract suspend fun deleteChecked()

    @Query("DELETE FROM shopping_items")
    abstract suspend fun deleteAll()

    /**
     * Ersetzt die Liste in einem Rutsch. Das Zusammenfuehren passiert in der
     * Domain-Schicht; hier wird nur das Ergebnis festgehalten.
     */
    @Transaction
    open suspend fun replaceAll(items: List<ShoppingItemEntity>) {
        deleteAll()
        if (items.isNotEmpty()) {
            insert(items.map { it.copy(id = 0L) })
        }
    }
}
