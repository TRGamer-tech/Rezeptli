package ch.rezeptli.app.data.repository

import ch.rezeptli.app.data.local.dao.ShoppingListDao
import ch.rezeptli.app.data.mapper.toDomain
import ch.rezeptli.app.data.mapper.toEntity
import ch.rezeptli.app.di.IoDispatcher
import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.repository.ShoppingListRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoppingListRepositoryImpl @Inject constructor(
    private val shoppingListDao: ShoppingListDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ShoppingListRepository {
    override fun observeItems(): Flow<List<ShoppingItem>> =
        shoppingListDao.observeItems().map { items -> items.map { it.toDomain() } }

    override fun observeOpenCount(): Flow<Int> = shoppingListDao.observeOpenCount()

    override suspend fun getItems(): List<ShoppingItem> = withContext(ioDispatcher) {
        shoppingListDao.items().map { it.toDomain() }
    }

    override suspend fun replaceAll(items: List<ShoppingItem>) = withContext(ioDispatcher) {
        shoppingListDao.replaceAll(items.map { it.toEntity() })
    }

    override suspend fun addItem(item: ShoppingItem) = withContext(ioDispatcher) {
        shoppingListDao.insert(listOf(item.toEntity()))
    }

    override suspend fun setChecked(itemId: Long, checked: Boolean) = withContext(ioDispatcher) {
        shoppingListDao.setChecked(itemId, checked)
    }

    override suspend fun deleteItem(itemId: Long) = withContext(ioDispatcher) {
        shoppingListDao.delete(itemId)
    }

    override suspend fun deleteChecked() = withContext(ioDispatcher) {
        shoppingListDao.deleteChecked()
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        shoppingListDao.deleteAll()
    }
}
