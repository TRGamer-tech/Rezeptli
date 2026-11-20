package ch.rezeptli.app.fake

import androidx.paging.PagingData
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.plan.PlanRepository
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
    /**
     * Der Wochenplan, sofern ein Test einen hat. Die echte Datenbank raeumt Rezepte auf, die
     * nur fuer den Plan geholt wurden und dort nicht mehr stehen - dafuer muss der Ersatz den
     * Plan kennen.
     */
    var plan: PlanRepository? = null

    private val recipes = MutableStateFlow(initialRecipes.associateBy { it.id })
    private var nextId: Long = (initialRecipes.maxOfOrNull { it.id } ?: 0L) + 1L

    var savedRecipes: MutableList<Recipe> = mutableListOf()
        private set
    var deletedIds: MutableList<Long> = mutableListOf()
        private set
    var cookedIds: MutableList<Long> = mutableListOf()
        private set

    /** Wie die echte Sammlung: Was nur fuer den Plan gespeichert ist, zaehlt nicht dazu. */
    private fun Map<Long, Recipe>.collection(): List<Recipe> = values.filter { it.inCollection }

    /** Was der Bildschirm zuletzt an Rezepten zu sehen bekam - fuer Tests, die den Filter pruefen. */
    fun visible(filter: RecipeFilter): List<Recipe> = recipes.value.collection().filter { it.matches(filter) }

    override fun pagedSummaries(filter: RecipeFilter): Flow<PagingData<RecipeSummary>> =
        recipes.map { current ->
            PagingData.from(current.collection().filter { it.matches(filter) }.map { it.toSummary() })
        }

    override fun observeRecipe(id: Long): Flow<Recipe?> = recipes.map { it[id] }

    override fun observeRecipeCount(): Flow<Int> = recipes.map { it.collection().size }

    override fun observeAllTags(): Flow<List<String>> =
        recipes.map { current ->
            current
                .collection()
                .flatMap { it.tags }
                .distinct()
                .sorted()
        }

    override suspend fun getRecipe(id: Long): Recipe? = recipes.value[id]

    override suspend fun getSummaries(ids: List<Long>): List<RecipeSummary> =
        ids.mapNotNull { recipes.value[it]?.toSummary() }

    override suspend fun getFilteredRecipeIds(filter: RecipeFilter): List<Long> =
        recipes.value
            .collection()
            .filter { it.matches(filter) }
            .map { it.id }

    private fun Recipe.matches(filter: RecipeFilter): Boolean {
        val matchesQuery = filter.query.isBlank() || title.contains(filter.query, ignoreCase = true)
        val matchesTags = filter.tags.isEmpty() || tags.containsAll(filter.tags)
        val matchesTime = filter.maxPrepTimeMinutes?.let { max ->
            prepTimeMinutes != null && prepTimeMinutes <= max
        } ?: true
        val matchesCooked = filter.cooked?.let { cooked -> (lastCookedAt != null) == cooked } ?: true
        return matchesQuery && matchesTags && matchesTime && matchesCooked
    }

    override suspend fun saveRecipe(recipe: Recipe): Long {
        val id = if (recipe.id == 0L) nextId++ else recipe.id
        val stored = recipe.copy(id = id)
        savedRecipes.add(stored)
        recipes.value = recipes.value + (id to stored)
        return id
    }

    override suspend fun findIdBySourceUrl(url: String): Long? =
        recipes.value.values
            .sortedByDescending { it.inCollection }
            .firstOrNull { it.sourceUrl == url }
            ?.id

    override suspend fun savedSourceUrls(): Set<String> =
        recipes.value.collection().mapNotNullTo(mutableSetOf()) { it.sourceUrl }

    override suspend fun setInCollection(id: Long, inCollection: Boolean) {
        recipes.value[id]?.let { recipe ->
            recipes.value = recipes.value + (id to recipe.copy(inCollection = inCollection))
        }
    }

    override suspend fun deleteRecipe(id: Long) {
        deletedIds.add(id)
        recipes.value = recipes.value - id
    }

    override suspend fun deleteUnusedPlanRecipes() {
        val planned = plan?.currentPlan().orEmpty().mapTo(mutableSetOf()) { it.recipeId }
        recipes.value = recipes.value.filterValues { it.inCollection || it.id in planned }
    }

    /** Alles, was gespeichert ist - auch, was nur fuer den Plan geholt wurde. */
    fun all(): List<Recipe> = recipes.value.values.toList()

    override suspend fun markCooked(id: Long, cookedAt: Long?) {
        if (cookedAt != null) cookedIds.add(id)
        recipes.value[id]?.let { recipe ->
            recipes.value = recipes.value + (id to recipe.copy(lastCookedAt = cookedAt))
        }
    }

    /** Nur fuer Tests: der aktuelle Stand eines Rezepts ohne Umweg ueber den Flow. */
    fun current(id: Long): Recipe? = recipes.value[id]

    private fun Recipe.toSummary() = RecipeSummary(
        id = id,
        title = title,
        photoUri = photoUri,
        prepTimeMinutes = prepTimeMinutes,
        tags = tags,
        lastCookedAt = lastCookedAt,
    )
}
