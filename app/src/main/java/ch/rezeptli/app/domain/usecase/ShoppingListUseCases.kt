package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.plan.IngredientScaler
import ch.rezeptli.app.domain.repository.RecipeRepository
import ch.rezeptli.app.domain.repository.ShoppingListRepository
import ch.rezeptli.app.domain.shopping.ShoppingListAggregator
import ch.rezeptli.app.domain.shopping.manualShoppingItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveShoppingListUseCase @Inject constructor(
    private val repository: ShoppingListRepository,
) {
    operator fun invoke(): Flow<List<ShoppingItem>> = repository.observeItems()
}

/** Anzahl offener Posten - blendet den Einkaufslisten-Einstieg nur ein, wenn es etwas gibt. */
class ObserveOpenShoppingCountUseCase @Inject constructor(
    private val repository: ShoppingListRepository,
) {
    operator fun invoke(): Flow<Int> = repository.observeOpenCount()
}

/**
 * Uebernimmt die Zutaten der angegebenen Rezepte in die Einkaufsliste.
 *
 * Bestehende Posten bleiben erhalten und werden ergaenzt - wer nach einer zweiten
 * Swipe-Runde nochmal Zutaten hinzufuegt, verliert die erste Liste nicht.
 *
 * Mit [persons] werden die Mengen auf den eigenen Haushalt umgerechnet - sofern das Rezept
 * sagt, fuer wie viele es gedacht ist. Ohne diese Angabe bleiben die Mengen, wie sie sind.
 *
 * Gibt zurueck, wie viele Posten die Liste danach umfasst.
 */
class AddRecipesToShoppingListUseCase @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val aggregator: ShoppingListAggregator,
) {
    suspend operator fun invoke(recipeIds: List<Long>, persons: Int? = null): Int {
        val recipes = recipeIds
            .mapNotNull { recipeRepository.getRecipe(it) }
            .map { recipe ->
                if (persons == null || recipe.servings == null) {
                    recipe
                } else {
                    val factor = IngredientScaler.factor(persons, recipe.servings)
                    recipe.copy(ingredients = IngredientScaler.scale(recipe.ingredients, factor), servings = persons)
                }
            }
        if (recipes.isEmpty()) return shoppingListRepository.getItems().size

        val additions = aggregator.aggregate(recipes)
        val merged = aggregator.merge(shoppingListRepository.getItems(), additions)
        shoppingListRepository.replaceAll(merged)
        return merged.size
    }
}

class AddManualShoppingItemUseCase @Inject constructor(
    private val repository: ShoppingListRepository,
) {
    suspend operator fun invoke(name: String, addedAt: Long = System.currentTimeMillis()) {
        if (name.isBlank()) return
        repository.addItem(manualShoppingItem(name, addedAt))
    }
}

class SetShoppingItemCheckedUseCase @Inject constructor(
    private val repository: ShoppingListRepository,
) {
    suspend operator fun invoke(itemId: Long, checked: Boolean) = repository.setChecked(itemId, checked)
}

class DeleteShoppingItemUseCase @Inject constructor(
    private val repository: ShoppingListRepository,
) {
    suspend operator fun invoke(itemId: Long) = repository.deleteItem(itemId)
}

class ClearShoppingListUseCase @Inject constructor(
    private val repository: ShoppingListRepository,
) {
    suspend fun checkedOnly() = repository.deleteChecked()

    suspend fun everything() = repository.clear()
}
