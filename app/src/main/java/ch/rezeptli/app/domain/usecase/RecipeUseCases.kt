package ch.rezeptli.app.domain.usecase

import androidx.paging.PagingData
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.plan.PlanMemory
import ch.rezeptli.app.domain.plan.PlanRepository
import ch.rezeptli.app.domain.plan.RecentlyCooked
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

/**
 * Wie viele Rezepte zu einem Filter passen. Wird vor dem Start einer Swipe-Session
 * gebraucht, damit die Nutzerin sieht, wie gross der Stapel wird.
 */
class CountMatchingRecipesUseCase @Inject constructor(
    private val repository: RecipeRepository,
) {
    suspend operator fun invoke(filter: RecipeFilter): Int = repository.getFilteredRecipeIds(filter).size
}

/**
 * Entfernt ein Rezept aus der Sammlung.
 *
 * Steht es gerade im Wochenplan, bleibt es fuer den Plan gespeichert und verschwindet nur
 * aus "Meine Rezepte" - sonst risse das Loeschen ein Loch in die Woche, waehrend die
 * Zutaten noch auf der Einkaufsliste stehen.
 */
class DeleteRecipeUseCase @Inject constructor(
    private val repository: RecipeRepository,
    private val planRepository: PlanRepository,
) {
    suspend operator fun invoke(recipeId: Long) {
        val planned = planRepository.currentPlan().any { it.recipeId == recipeId }
        if (planned) repository.setInCollection(recipeId, inCollection = false) else repository.deleteRecipe(recipeId)
    }
}

/** Nimmt ein Rezept, das bisher nur im Wochenplan stand, in die Sammlung auf. */
class KeepRecipeUseCase @Inject constructor(
    private val repository: RecipeRepository,
) {
    suspend operator fun invoke(recipeId: Long) = repository.setInCollection(recipeId, inCollection = true)
}

/**
 * Haelt fest, fuer wie viele ein Rezept gedacht ist, wenn es das selbst nicht sagt.
 *
 * Die Angabe kommt von der Person, die es kocht - sie weiss, ob "ein Blech" bei ihr fuer
 * vier oder fuer acht reicht. Einmal gesagt, rechnet die App ab da richtig um.
 */
class RememberServingsUseCase @Inject constructor(
    private val repository: RecipeRepository,
) {
    suspend operator fun invoke(recipeId: Long, servings: Int) {
        if (servings > 0) repository.setServings(recipeId, servings)
    }
}

/**
 * Haelt fest, dass ein Rezept gekocht wurde - am Rezept selbst und, falls es diese Woche
 * im Plan steht, an der Mahlzeit dort. Mit `null` wird der Vermerk zurueckgenommen.
 */
class MarkRecipeCookedUseCase @Inject constructor(
    private val repository: RecipeRepository,
    private val planRepository: PlanRepository,
    private val memory: PlanMemory,
) {
    suspend operator fun invoke(recipeId: Long, cookedAt: Long? = System.currentTimeMillis()) {
        repository.markCooked(recipeId, cookedAt)
        planRepository.markCooked(recipeId, cookedAt)
        // Der Plan merkt es sich fuer sich: Ein Rezept, das nur fuer ihn geholt wurde, ist mit
        // dem naechsten Plan wieder weg - und mit ihm der Vermerk am Rezept.
        repository.getRecipe(recipeId)?.let { memory.noteCooked(RecentlyCooked.keysOf(it), cookedAt) }
    }
}
