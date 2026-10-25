package ch.rezeptli.app.fake

import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.plan.MealLabel
import ch.rezeptli.app.domain.plan.PlanEntry
import ch.rezeptli.app.domain.plan.PlanRepository
import ch.rezeptli.app.domain.plan.PlanWrite
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Haelt den Wochenplan im Speicher. */
class FakePlanRepository(initial: List<PlanEntry> = emptyList()) : PlanRepository {
    private val entries = MutableStateFlow(initial)

    var savedBasket: List<ShoppingItem> = emptyList()
        private set

    override fun observePlan(): Flow<List<PlanEntry>> = entries

    override suspend fun currentPlan(): List<PlanEntry> = entries.value.sortedBy { it.day.ordinal }

    override suspend fun savePlan(entries: List<PlanWrite>, basket: List<ShoppingItem>) {
        this.entries.value = entries.map { it.toEntry() }
        savedBasket = basket
    }

    override suspend fun replaceEntry(entry: PlanWrite, basket: List<ShoppingItem>) {
        entries.value = entries.value.filterNot { it.day == entry.day } + entry.toEntry()
        savedBasket = basket
    }

    private fun PlanWrite.toEntry() = PlanEntry(
        day = day,
        recipeId = recipeId,
        title = "Rezept $recipeId",
        photoUri = null,
        prepTimeMinutes = null,
        servings = servings,
        cost = cost,
        label = label.takeIf { it != MealLabel.ALLTAG } ?: MealLabel.ALLTAG,
    )
}
