package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.Weekday
import kotlinx.coroutines.flow.Flow

/** Eine Mahlzeit im Plan, so wie die Karte sie zeigt. */
data class PlanEntry(
    val day: Weekday,
    val slot: MealSlot = MealSlot.ABEND,
    val recipeId: Long,
    val title: String,
    val photoUri: String?,
    val prepTimeMinutes: Int?,
    val servings: Int,
    val cost: Double,
    val label: MealLabel,
    /** Wann diese Mahlzeit gekocht wurde - `null`, solange sie noch aussteht. */
    val cookedAt: Long? = null,
) {
    val place: PlanPlace get() = PlanPlace(day, slot)
    val isCooked: Boolean get() = cookedAt != null
}

/** Was beim Speichern eines Planeintrags festgehalten wird. */
data class PlanWrite(
    val day: Weekday,
    val slot: MealSlot,
    val recipeId: Long,
    val servings: Int,
    val cost: Double,
    val label: MealLabel,
    val cookedAt: Long? = null,
)

/** Zugriff auf den Wochenplan. */
interface PlanRepository {
    /** Die Mahlzeiten in Wochentagsreihenfolge, mittags vor abends. */
    fun observePlan(): Flow<List<PlanEntry>>

    suspend fun currentPlan(): List<PlanEntry>

    /** Ersetzt Plan und Plan-Einkaufsposten; von Hand ergaenzte Posten bleiben. */
    suspend fun savePlan(entries: List<PlanWrite>, basket: List<ShoppingItem>)

    /** Tauscht die Mahlzeit eines Platzes und setzt den Einkauf neu. */
    suspend fun replaceEntry(entry: PlanWrite, basket: List<ShoppingItem>)

    /** Vermerkt an den Mahlzeiten mit diesem Rezept, dass gekocht wurde - `null` nimmt es zurueck. */
    suspend fun markCooked(recipeId: Long, cookedAt: Long?)
}

/**
 * Was der Plan sich ueber verworfene Vorschlaege merkt.
 *
 * Wer eine Mahlzeit tauscht, will dieses Rezept in dieser Woche nicht - also darf es beim
 * naechsten Tausch nicht wiederkommen. Ohne dieses Gedaechtnis drehte sich das Ersetzen im
 * Kreis: Das eben Verworfene war sofort wieder der beste freie Kandidat.
 */
interface PlanMemory {
    suspend fun rejected(): Set<String>

    suspend fun reject(key: String)

    /** Ein neuer Plan faengt ohne Vorgeschichte an. */
    suspend fun clear()
}
