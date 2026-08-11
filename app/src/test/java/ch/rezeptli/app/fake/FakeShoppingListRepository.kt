package ch.rezeptli.app.fake

import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.repository.ShoppingListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-Memory-Ersatz fuer die Einkaufsliste. */
class FakeShoppingListRepository(
    initialItems: List<ShoppingItem> = emptyList(),
) : ShoppingListRepository {
    private val items = MutableStateFlow(
        initialItems.mapIndexed { index, item -> item.copy(id = index.toLong() + 1) },
    )
    private var nextId: Long = initialItems.size.toLong() + 1

    override fun observeItems(): Flow<List<ShoppingItem>> = items

    override fun observeOpenCount(): Flow<Int> = items.map { list -> list.count { !it.isChecked } }

    override suspend fun getItems(): List<ShoppingItem> = items.value

    override suspend fun replaceAll(items: List<ShoppingItem>) {
        this.items.value = items.map { item ->
            if (item.id == 0L) item.copy(id = nextId++) else item
        }
    }

    override suspend fun addItem(item: ShoppingItem) {
        items.value = items.value + item.copy(id = nextId++)
    }

    override suspend fun setChecked(itemId: Long, checked: Boolean) {
        items.value = items.value.map { if (it.id == itemId) it.copy(isChecked = checked) else it }
    }

    override suspend fun deleteItem(itemId: Long) {
        items.value = items.value.filterNot { it.id == itemId }
    }

    override suspend fun deleteChecked() {
        items.value = items.value.filterNot { it.isChecked }
    }

    override suspend fun clear() {
        items.value = emptyList()
    }
}
