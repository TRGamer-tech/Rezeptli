package ch.rezeptli.app.fake

import androidx.paging.PagingData
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-Memory-Ersatz fuer die Rezeptsammlung.
 *
 * Bewusst ein handgeschriebener Fake statt eines Mocks: Die Tests lesen sich damit wie
 * eine Beschreibung des Verhaltens und nicht wie eine Liste erwarteter Aufrufe.
 */
class FakeRecipeRepository(
    initialRecipes: List<Recipe> = emptyList(),
) : RecipeRepository {
    private val recipes = MutableStateFlow(initialRecipes.associateBy { it.id })
    private var nextId: Long = (initialRecipes.maxOfOrNull { it.id } ?: 0L) + 1L

    var savedRecipes: MutableList<Recipe> = mutableListOf()
        private set
    var deletedIds: MutableList<Long> = mutableListOf()
        private set
    var cookedIds: MutableList<Long> = mutableListOf()
        private set

    override fun pagedSummaries(filter: RecipeFilter): Flow<PagingData<RecipeSummary>> =
        recipes.map { current -> PagingData.from(current.values.map { it.toSummary() }) }

    override fun observeRecipe(id: Long): Flow<Recipe?> = recipes.map { it[id] }

    override fun observeRecipeCount(): Flow<Int> = recipes.map { it.size }

    override fun observeAllTags(): Flow<List<String>> =
        recipes.map { current ->
            current.values
                .flatMap { it.tags }
                .distinct()
                .sorted()
        }

    override suspend fun getRecipe(id: Long): Recipe? = recipes.value[id]

    override suspend fun getSummaries(ids: List<Long>): List<RecipeSummary> =
        ids.mapNotNull { recipes.value[it]?.toSummary() }

    override suspend fun getFilteredRecipeIds(filter: RecipeFilter): List<Long> =
        recipes.value.values
            .filter { recipe ->
                val matchesTags = filter.tags.isEmpty() || recipe.tags.containsAll(filter.tags)
                val matchesTime = filter.maxPrepTimeMinutes?.let { max ->
                    recipe.prepTimeMinutes != null && recipe.prepTimeMinutes <= max
                } ?: true
                matchesTags && matchesTime
            }.map { it.id }

    override suspend fun saveRecipe(recipe: Recipe): Long {
        val id = if (recipe.id == 0L) nextId++ else recipe.id
        val stored = recipe.copy(id = id)
        savedRecipes.add(stored)
        recipes.value = recipes.value + (id to stored)
        return id
    }

    override suspend fun deleteRecipe(id: Long) {
        deletedIds.add(id)
        recipes.value = recipes.value - id
    }

    override suspend fun markCooked(id: Long, cookedAt: Long) {
        cookedIds.add(id)
        recipes.value[id]?.let { recipe ->
            recipes.value = recipes.value + (id to recipe.copy(lastCookedAt = cookedAt))
        }
    }

    private fun Recipe.toSummary() = RecipeSummary(
        id = id,
        title = title,
        photoUri = photoUri,
        prepTimeMinutes = prepTimeMinutes,
        tags = tags,
        lastCookedAt = lastCookedAt,
    )
}
