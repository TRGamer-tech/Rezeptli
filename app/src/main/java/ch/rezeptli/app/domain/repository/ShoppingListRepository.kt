package ch.rezeptli.app.domain.repository

import ch.rezeptli.app.domain.model.ShoppingItem
import kotlinx.coroutines.flow.Flow

/** Zugriff auf die Einkaufsliste. */
interface ShoppingListRepository {
    fun observeItems(): Flow<List<ShoppingItem>>

    /** Anzahl noch offener Posten - fuer die Anzeige am Einkaufslisten-Symbol. */
    fun observeOpenCount(): Flow<Int>

    suspend fun getItems(): List<ShoppingItem>

    /** Ersetzt die gesamte Liste; das Zusammenfuehren passiert in der Domain-Schicht. */
    suspend fun replaceAll(items: List<ShoppingItem>)

    suspend fun addItem(item: ShoppingItem)

    suspend fun setChecked(itemId: Long, checked: Boolean)

    suspend fun deleteItem(itemId: Long)

    suspend fun deleteChecked()

    suspend fun clear()
}
