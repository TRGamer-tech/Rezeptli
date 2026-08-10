package ch.rezeptli.app.domain.usecase

import androidx.paging.PagingData
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.repository.RecipeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Seitenweise Rezeptliste passend zum aktuellen Filter. */
class ObservePagedRecipesUseCase @Inject constructor(
    private val repository: RecipeRepository,
) {
    operator fun invoke(filter: RecipeFilter): Flow<PagingData<RecipeSummary>> =
        repository.pagedSummaries(filter)
}

/** Ein einzelnes Rezept samt Zutaten beobachten. */
class ObserveRecipeUseCase @Inject constructor(
    private val repository: RecipeRepository,
) {
    operator fun invoke(recipeId: Long): Flow<Recipe?> = repository.observeRecipe(recipeId)
}

/** Anzahl aller Rezepte - steuert den Leerzustand der App. */
class ObserveRecipeCountUseCase @Inject constructor(
    private val repository: RecipeRepository,
) {
    operator fun invoke(): Flow<Int> = repository.observeRecipeCount()
}

/** Alle vergebenen Tags, alphabetisch - Grundlage der Filter-Chips. */
class ObserveTagsUseCase @Inject constructor(
    private val repository: RecipeRepository,
) {
    operator fun invoke(): Flow<List<String>> = repository.observeAllTags()
}

class DeleteRecipeUseCase @Inject constructor(
    private val repository: RecipeRepository,
) {
    suspend operator fun invoke(recipeId: Long) = repository.deleteRecipe(recipeId)
}

/** Haelt fest, dass ein Rezept heute gekocht wurde. */
class MarkRecipeCookedUseCase @Inject constructor(
    private val repository: RecipeRepository,
) {
    suspend operator fun invoke(recipeId: Long, cookedAt: Long = System.currentTimeMillis()) =
        repository.markCooked(recipeId, cookedAt)
}
