package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Mood
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.domain.shopping.ShoppingListAggregator
import javax.inject.Inject
import kotlin.random.Random

/**
 * Baut aus Kandidaten einen Wochenplan.
 *
 * Reihenfolge der Entscheidungen:
 * 1. **Hart ausgeschlossen** wird, was gegen Ernaehrung, Unvertraeglichkeiten oder die
 *    vorhandenen Geraete verstoesst. Das sind Zusagen, keine Vorlieben.
 * 2. **Gewertet** wird nach den gewaehlten Stimmungen - sie verschieben die Reihenfolge,
 *    schliessen aber nichts aus: Wer "schnell" will, bekommt auch ein langsames Gericht,
 *    wenn es sonst nichts gaebe.
 * 3. **Abgestimmt** wird auf das Budget: Ist der Einkauf zu teuer, wird die teuerste
 *    Mahlzeit gegen eine guenstigere getauscht, solange das hilft.
 */
class PlanGenerator @Inject constructor(
    private val aggregator: ShoppingListAggregator,
) {
    fun generate(
        candidates: List<PlanCandidate>,
        profile: UserProfile,
        random: Random = Random.Default,
    ): PlanDraft {
        val persons = profile.persons
        val days = daysOf(profile)
        val budget = profile.weeklyBudget ?: UserProfile.DEFAULT_BUDGET

        val usable = candidates
            .filter { isAllowed(it, profile) }
            .distinctBy { it.recipe.sourceUrl ?: it.recipe.title.lowercase() }
        val priced = usable.map { it to mealCost(it.recipe, persons, profile) }

        val ranked = priced.sortedByDescending { (candidate, _) -> score(candidate, profile) + random.nextDouble() }
        var chosen = pickVaried(ranked, days.size)

        var basket = basketFor(chosen, persons, profile)
        var cost = basket.sumOf { it.price ?: 0.0 }
        var rounds = 0
        while (cost > budget && rounds < MAX_SWAPS) {
            val unused = ranked.filter { entry -> chosen.none { it.first == entry.first } }
            val dearest = chosen.maxByOrNull { it.second } ?: break
            val cheaper = unused
                .filter { it.second < dearest.second }
                .maxByOrNull { score(it.first, profile) - it.second * COST_WEIGHT }
                ?: break
            chosen = chosen.map { if (it === dearest) cheaper else it }
            basket = basketFor(chosen, persons, profile)
            cost = basket.sumOf { it.price ?: 0.0 }
            rounds += 1
        }

        val meals = chosen.zip(days).map { (entry, day) ->
            PlannedMeal(day = day, candidate = entry.first, servings = persons, cost = entry.second)
        }
        return PlanDraft(meals = meals, basket = basket, budget = budget)
    }

    /** Ersetzt eine Mahlzeit durch die naechstbeste, die noch nicht im Plan ist. */
    fun replace(
        draft: PlanDraft,
        day: Weekday,
        candidates: List<PlanCandidate>,
        profile: UserProfile,
        random: Random = Random.Default,
    ): PlanDraft {
        val current = draft.meals.firstOrNull { it.day == day } ?: return draft
        val taken = draft.meals.map { key(it.candidate) }.toSet()
        val persons = profile.persons

        val replacement = candidates
            .filter { isAllowed(it, profile) && key(it) !in taken }
            .maxByOrNull { score(it, profile) + random.nextDouble() }
            ?: return draft

        val meals = draft.meals.map {
            if (it.day != day) it else current.copy(
                candidate = replacement,
                cost = mealCost(replacement.recipe, persons, profile),
            )
        }
        return draft.copy(
            meals = meals,
            basket = basketFor(meals.map { it.candidate to it.cost }, persons, profile),
        )
    }

    fun basketFor(
        meals: List<Pair<PlanCandidate, Double>>,
        persons: Int,
        profile: UserProfile,
    ): List<ShoppingItem> {
        val scaled = meals.map { (candidate, _) -> scaledRecipe(candidate.recipe, persons) }
        return aggregator.aggregate(scaled).map { item ->
            val choice = PriceCatalog.choose(item, profile.store)
            item.copy(packText = choice.text, price = choice.total, fromPlan = true)
        }
    }

    fun scaledRecipe(recipe: Recipe, persons: Int): Recipe {
        val factor = IngredientScaler.factor(persons, recipe.servings)
        return recipe.copy(ingredients = IngredientScaler.scale(recipe.ingredients, factor), servings = persons)
    }

    private fun mealCost(recipe: Recipe, persons: Int, profile: UserProfile): Double =
        mealCostOf(scaledRecipe(recipe, persons), profile)

    /** Anteilige Kosten eines bereits auf die Personenzahl umgerechneten Rezepts. */
    fun mealCostOf(scaled: Recipe, profile: UserProfile): Double =
        aggregator.aggregate(listOf(scaled)).sumOf { PriceCatalog.fractionalCost(it, profile.store) }

    private fun daysOf(profile: UserProfile): List<Weekday> =
        profile.cookDays.sortedBy { it.ordinal }.ifEmpty { Weekday.entries.take(UserProfile.DEFAULT_COOK_DAYS) }

    private fun isAllowed(candidate: PlanCandidate, profile: UserProfile): Boolean {
        val traits = candidate.traits
        val recipe = candidate.recipe
        if (recipe.ingredients.size < MIN_INGREDIENTS) return false
        if (recipe.instructions.isBlank() && recipe.steps.isEmpty()) return false
        if (profile.diets.any { !satisfies(traits, it) }) return false
        if (profile.intolerances.any { it in traits.allergens }) return false
        if (profile.appliances.isNotEmpty()) {
            if (!profile.appliances.containsAll(traits.requiredAppliances)) return false
        }
        return true
    }

    private fun satisfies(traits: RecipeTraits, diet: Diet): Boolean = when (diet) {
        Diet.VEGAN -> traits.diet == DietClass.VEGAN
        Diet.VEGETARISCH ->
            traits.diet == DietClass.VEGAN || traits.diet == DietClass.VEGETARISCH
        Diet.PESCETARISCH -> traits.diet != DietClass.FLEISCH
        Diet.KOHLENHYDRATARM -> traits.lowCarb
    }

    private fun score(candidate: PlanCandidate, profile: UserProfile): Double {
        var score = 0.0
        profile.moods.forEach { if (it in candidate.traits.moods) score += MOOD_POINTS }
        if (Mood.SCHNELL in profile.moods) {
            candidate.recipe.prepTimeMinutes?.let { score += (QUICK_LIMIT - it).coerceIn(-QUICK_LIMIT, QUICK_LIMIT) / QUICK_LIMIT }
        }
        if (candidate.recipe.photoUri != null) score += IMAGE_POINTS
        return score
    }

    /** Waehlt der Reihe nach, aber nicht zweimal dasselbe Etikett hintereinander, wenn es Auswahl gibt. */
    private fun pickVaried(
        ranked: List<Pair<PlanCandidate, Double>>,
        count: Int,
    ): List<Pair<PlanCandidate, Double>> {
        val picked = mutableListOf<Pair<PlanCandidate, Double>>()
        val pool = ranked.toMutableList()
        while (picked.size < count && pool.isNotEmpty()) {
            val lastLabel = picked.lastOrNull()?.first?.traits?.label
            val next = pool.firstOrNull { it.first.traits.label != lastLabel } ?: pool.first()
            picked += next
            pool.remove(next)
        }
        return picked
    }

    private fun key(candidate: PlanCandidate): String =
        candidate.recipe.sourceUrl ?: candidate.recipe.title.lowercase()

    private companion object {
        const val MIN_INGREDIENTS = 2
        const val MAX_SWAPS = 8
        const val MOOD_POINTS = 3.0
        const val IMAGE_POINTS = 0.5
        const val QUICK_LIMIT = 60.0
        const val COST_WEIGHT = 0.15
    }
}
