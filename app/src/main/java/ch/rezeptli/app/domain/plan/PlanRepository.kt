package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.profile.Weekday
import kotlinx.coroutines.flow.Flow

/** Eine Mahlzeit im Plan, so wie die Karte sie zeigt. */
data class PlanEntry(
    val day: Weekday,
    val recipeId: Long,
    val title: String,
    val photoUri: String?,
    val prepTimeMinutes: Int?,
    val servings: Int,
    val cost: Double,
    val label: MealLabel,
)

/** Was beim Speichern eines Planeintrags festgehalten wird. */
data class PlanWrite(
    val day: Weekday,
    val recipeId: Long,
    val servings: Int,
    val cost: Double,
    val label: MealLabel,
)

/** Zugriff auf den Wochenplan. */
interface PlanRepository {
    /** Die Mahlzeiten in Wochentagsreihenfolge. */
    fun observePlan(): Flow<List<PlanEntry>>

    suspend fun currentPlan(): List<PlanEntry>

    /** Ersetzt Plan und Plan-Einkaufsposten; von Hand ergaenzte Posten bleiben. */
    suspend fun savePlan(entries: List<PlanWrite>, basket: List<ShoppingItem>)

    /** Tauscht die Mahlzeit eines Tages und setzt den Einkauf neu. */
    suspend fun replaceEntry(entry: PlanWrite, basket: List<ShoppingItem>)
}
