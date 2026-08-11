package ch.rezeptli.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import ch.rezeptli.app.data.local.PhotoStorage
import ch.rezeptli.app.data.local.dao.RecipeDao
import ch.rezeptli.app.data.mapper.toDomain
import ch.rezeptli.app.data.mapper.toEntity
import ch.rezeptli.app.di.IoDispatcher
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.repository.RecipeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRepositoryImpl @Inject constructor(
    private val recipeDao: RecipeDao,
    private val photoStorage: PhotoStorage,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RecipeRepository {
    override fun pagedSummaries(filter: RecipeFilter): Flow<PagingData<RecipeSummary>> =
        Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PAGE_SIZE / 2,
                initialLoadSize = PAGE_SIZE * 2,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                recipeDao.pagedSummaries(
                    query = filter.query.trim(),
                    tags = filter.tags.toList(),
                    tagCount = filter.tags.size,
                    maxPrepTimeMinutes = filter.maxPrepTimeMinutes,
                )
            },
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override fun observeRecipe(id: Long): Flow<Recipe?> =
        recipeDao.observeRecipeWithDetails(id).map { it?.toDomain() }

    override fun observeRecipeCount(): Flow<Int> = recipeDao.observeRecipeCount()

    override fun observeAllTags(): Flow<List<String>> = recipeDao.observeAllTags()

    override suspend fun getRecipe(id: Long): Recipe? = withContext(ioDispatcher) {
        recipeDao.recipeWithDetails(id)?.toDomain()
    }

    override suspend fun getSummaries(ids: List<Long>): List<RecipeSummary> = withContext(ioDispatcher) {
        if (ids.isEmpty()) return@withContext emptyList()
        val byId = recipeDao.summariesByIds(ids).associateBy { it.id }
        ids.mapNotNull { id -> byId[id]?.toDomain() }
    }

    override suspend fun getFilteredRecipeIds(filter: RecipeFilter): List<Long> = withContext(ioDispatcher) {
        recipeDao.filteredRecipeIds(
            query = filter.query.trim(),
            tags = filter.tags.toList(),
            tagCount = filter.tags.size,
            maxPrepTimeMinutes = filter.maxPrepTimeMinutes,
        )
    }

    override suspend fun saveRecipe(recipe: Recipe): Long = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        recipeDao.upsertRecipe(
            recipe = recipe.toEntity(now),
            ingredients = recipe.ingredients.mapIndexed { index, ingredient ->
                ingredient.toEntity(recipeId = recipe.id, position = index)
            },
            tags = recipe.tags
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct(),
            steps = recipe.steps.mapIndexed { index, step ->
                step.toEntity(recipeId = recipe.id, position = index)
            },
        )
    }

    override suspend fun deleteRecipe(id: Long) = withContext(ioDispatcher) {
        // Das Foto gehoert zum Rezept - beim Loeschen darf keine verwaiste Datei zurueckbleiben.
        val photoUri = recipeDao.recipeWithDetails(id)?.recipe?.photoUri
        recipeDao.deleteRecipe(id)
        photoStorage.deletePhoto(photoUri)
    }

    override suspend fun markCooked(id: Long, cookedAt: Long) = withContext(ioDispatcher) {
        recipeDao.updateLastCookedAt(id, cookedAt)
    }

    private companion object {
        const val PAGE_SIZE = 30
    }
}
