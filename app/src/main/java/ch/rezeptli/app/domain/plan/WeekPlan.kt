package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.deck.TitleKey
import ch.rezeptli.app.domain.deck.UrlKey
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.Weekday

/** Ein Rezept samt seiner Einschaetzung - die Auswahl, aus der der Plan gebaut wird. */
data class PlanCandidate(
    val recipe: Recipe,
    val traits: RecipeTraits,
) {
    /** Woran ein Rezept wiedererkannt wird: seine Adresse, sonst sein Titel. */
    val key: String get() = keyOf(recipe)

    companion object {
        /**
         * Verglichen wird nicht Zeichen fuer Zeichen: Dieselbe Seite ist mit und ohne "www."
         * oder Schraegstrich am Ende dasselbe Rezept - siehe [UrlKey] und [TitleKey].
         */
        fun keyOf(recipe: Recipe): String = recipe.sourceUrl?.let(UrlKey::of) ?: TitleKey.of(recipe.title)
    }
}

/** Ein Platz im Plan: Mittag oder Abend eines Wochentags. */
data class PlanPlace(
    val day: Weekday,
    val slot: MealSlot,
)

/** Eine Mahlzeit im Plan: welches Rezept, an welchem Platz, fuer wie viele. */
data class PlannedMeal(
    val day: Weekday,
    val slot: MealSlot,
    val candidate: PlanCandidate,
    val servings: Int,
    /** Anteilige Kosten dieser Mahlzeit, ohne Packungsreste. */
    val cost: Double,
) {
    val recipe: Recipe get() = candidate.recipe
    val label: MealLabel get() = candidate.traits.label
    val place: PlanPlace get() = PlanPlace(day, slot)
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

    /** Keine einzige Mahlzeit: Es gab kein passendes Rezept. */
    val isIncomplete: Boolean get() = meals.isEmpty()
}
