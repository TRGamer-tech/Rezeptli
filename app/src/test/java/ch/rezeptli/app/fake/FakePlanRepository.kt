package ch.rezeptli.app.fake

import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.plan.PlanEntry
import ch.rezeptli.app.domain.plan.PlanMemory
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

    override suspend fun currentPlan(): List<PlanEntry> =
        entries.value.sortedWith(compareBy({ it.day.ordinal }, { it.slot.ordinal }))

    override suspend fun savePlan(entries: List<PlanWrite>, basket: List<ShoppingItem>) {
        this.entries.value = entries.map { it.toEntry() }
        savedBasket = basket
    }

    /** Ein Platz ist Tag und Mahlzeit - getauscht wird nur genau dieser. */
    override suspend fun replaceEntry(entry: PlanWrite, basket: List<ShoppingItem>) {
        entries.value = entries.value.filterNot { it.day == entry.day && it.slot == entry.slot } + entry.toEntry()
        savedBasket = basket
    }

    override suspend fun markCooked(recipeId: Long, cookedAt: Long?) {
        entries.value = entries.value.map { if (it.recipeId == recipeId) it.copy(cookedAt = cookedAt) else it }
    }

    private fun PlanWrite.toEntry() = PlanEntry(
        day = day,
        slot = slot,
        recipeId = recipeId,
        title = "Rezept $recipeId",
        photoUri = null,
        prepTimeMinutes = null,
        servings = servings,
        cost = cost,
        label = label,
        cookedAt = cookedAt,
    )
}

/** Merkt sich im Speicher, was beim Tauschen verworfen wurde. */
class FakePlanMemory : PlanMemory {
    private val keys = mutableSetOf<String>()

    override suspend fun rejected(): Set<String> = keys.toSet()

    override suspend fun reject(key: String) {
        keys += key
    }

    override suspend fun clear() {
        keys.clear()
    }
}
