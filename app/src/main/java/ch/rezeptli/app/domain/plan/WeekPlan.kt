package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.profile.Weekday

/** Ein Rezept samt seiner Einschaetzung - die Auswahl, aus der der Plan gebaut wird. */
data class PlanCandidate(
    val recipe: Recipe,
    val traits: RecipeTraits,
)

/** Eine Mahlzeit im Plan: welches Rezept, an welchem Tag, fuer wie viele. */
data class PlannedMeal(
    val day: Weekday,
    val candidate: PlanCandidate,
    val servings: Int,
    /** Anteilige Kosten dieser Mahlzeit, ohne Packungsreste. */
    val cost: Double,
) {
    val recipe: Recipe get() = candidate.recipe
    val label: MealLabel get() = candidate.traits.label
}

/** Das Ergebnis der Planung. */
data class PlanDraft(
    val meals: List<PlannedMeal>,
    val basket: List<ShoppingItem>,
    val budget: Int,
) {
    /** Was der Einkauf kostet - mit ganzen Packungen, wie an der Kasse. */
    val basketCost: Double get() = basket.sumOf { it.price ?: 0.0 }

    val isOverBudget: Boolean get() = basketCost > budget

    /** Weniger Mahlzeiten als gewuenscht: es gab nicht genug passende Rezepte. */
    val isIncomplete: Boolean get() = meals.isEmpty()
}
