package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.Mood
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.shopping.ShoppingListAggregator
import javax.inject.Inject
import kotlin.random.Random

/**
 * Baut aus Kandidaten einen Wochenplan.
 *
 * Reihenfolge der Entscheidungen:
 * 1. **Hart ausgeschlossen** wird, was keine Mahlzeit ist (Getraenke, Desserts, Saucen) und
 *    was gegen Ernaehrung, Unvertraeglichkeiten, Abneigungen oder die vorhandenen Geraete
 *    verstoesst. Das sind Zusagen, keine Vorlieben.
 * 2. **Gewertet** wird nach Stimmungen, Kuechen und besonderen Geraeten - sie verschieben
 *    die Reihenfolge, schliessen aber nichts aus: Wer "schnell" will, bekommt auch ein
 *    langsames Gericht, wenn es sonst nichts gaebe.
 * 3. **Abgestimmt** wird auf das Budget: Ist der Einkauf zu teuer, wird die teuerste
 *    Mahlzeit gegen eine guenstigere getauscht, solange das hilft.
 * 4. **Verteilt** wird auf Mittag und Abend: Was schnell geht oder leicht ist, kommt
 *    mittags auf den Tisch.
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
        val places = placesOf(profile)

        val usable = candidates
            .filter { isAllowed(it, profile) }
            .distinctBy { it.key }
        val priced = usable.map { it to mealCost(it.recipe, persons, profile) }

        val ranked = priced.sortedByDescending { (candidate, _) -> score(candidate, profile) + random.nextDouble() }
        var chosen = pickVaried(ranked, places.size)

        var basket = basketFor(chosen, persons, profile)
        var cost = basket.sumOf { it.price ?: 0.0 }
        var rounds = 0
        while (cost > profile.budget && rounds < MAX_SWAPS) {
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

        return PlanDraft(meals = assign(chosen, places, persons), basket = basket, budget = profile.budget)
    }

    /**
     * Ersetzt eine Mahlzeit durch ein Rezept, das weder im Plan steht noch schon verworfen
     * wurde ([avoid]).
     *
     * Gewaehlt wird zufaellig unter den besten freien Kandidaten statt immer der eine beste:
     * Wer zweimal tauscht, soll zwei verschiedene Vorschlaege sehen.
     *
     * Gibt denselben Entwurf zurueck, wenn es nichts Neues gibt.
     */
    fun replace(
        draft: PlanDraft,
        place: PlanPlace,
        candidates: List<PlanCandidate>,
        profile: UserProfile,
        avoid: Set<String> = emptySet(),
        random: Random = Random.Default,
    ): PlanDraft {
        val current = draft.meals.firstOrNull { it.place == place } ?: return draft
        val taken = draft.meals.mapTo(mutableSetOf()) { it.candidate.key }
        val persons = profile.persons

        val free = candidates
            .filter { isAllowed(it, profile) && it.key !in taken && it.key !in avoid }
            .distinctBy { it.key }
        if (free.isEmpty()) return draft

        val replacement = free
            .sortedByDescending { score(it, profile) + slotFit(it, place.slot) + random.nextDouble() }
            .take(REPLACEMENT_CHOICES)
            .random(random)

        val meals = draft.meals.map {
            if (it.place != place) {
                it
            } else {
                current.copy(
                    candidate = replacement,
                    cost = mealCost(replacement.recipe, persons, profile),
                )
            }
        }
        return draft.copy(
            meals = meals,
            basket = basketFor(meals.map { it.candidate to it.cost }, persons, profile),
        )
    }

    /** Wie viele Rezepte nach den harten Regeln ueberhaupt in Frage kommen. */
    fun countUsable(candidates: List<PlanCandidate>, profile: UserProfile, without: Set<String> = emptySet()): Int =
        candidates.count { isAllowed(it, profile) && it.key !in without }

    fun basketFor(
        meals: List<Pair<PlanCandidate, Double>>,
        persons: Int,
        profile: UserProfile,
    ): List<ShoppingItem> {
        val scaled = meals.map { (candidate, _) -> scaledRecipe(candidate.recipe, persons) }
        return aggregator.aggregate(scaled).map { item ->
            val choice = PriceCatalog.choose(item, profile.priceFactor)
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
        aggregator.aggregate(listOf(scaled)).sumOf { PriceCatalog.fractionalCost(it, profile.priceFactor) }

    /**
     * Die Plaetze der Woche. Reichen die Rezepte nicht fuer alle, soll zuerst jeder Tag ein
     * Abendessen haben - darum stehen die Abende vorn.
     */
    fun placesOf(profile: UserProfile): List<PlanPlace> {
        val slots = profile.slots.sortedByDescending { it == MealSlot.ABEND }
        return slots.flatMap { slot -> profile.days.map { day -> PlanPlace(day, slot) } }
    }

    /**
     * Verteilt die gewaehlten Rezepte auf die Plaetze: Was am besten zum Mittag passt,
     * kommt mittags, der Rest abends.
     */
    private fun assign(
        chosen: List<Pair<PlanCandidate, Double>>,
        places: List<PlanPlace>,
        persons: Int,
    ): List<PlannedMeal> {
        val used = places.take(chosen.size)
        val lunchPlaces = used.filter { it.slot == MealSlot.MITTAG }
        val dinnerPlaces = used.filter { it.slot == MealSlot.ABEND }

        // Stabil sortiert: Bei gleicher Eignung bleibt die Reihenfolge der Wertung erhalten.
        val byLunchFit = chosen.sortedByDescending { slotFit(it.first, MealSlot.MITTAG) }
        val lunches = byLunchFit.take(lunchPlaces.size)
        val dinners = chosen.filter { entry -> lunches.none { it === entry } }

        val meals = lunchPlaces.zip(lunches) + dinnerPlaces.zip(dinners)
        return meals
            .map { (place, entry) ->
                PlannedMeal(
                    day = place.day,
                    slot = place.slot,
                    candidate = entry.first,
                    servings = persons,
                    cost = entry.second,
                )
            }.sortedWith(compareBy({ it.day.ordinal }, { it.slot.ordinal }))
    }

    private fun isAllowed(candidate: PlanCandidate, profile: UserProfile): Boolean {
        val traits = candidate.traits
        val recipe = candidate.recipe
        if (recipe.ingredients.size < MIN_INGREDIENTS) return false
        if (recipe.instructions.isBlank() && recipe.steps.isEmpty()) return false
        // Der Plan besteht aus Mahlzeiten - Getraenke, Desserts und Saucen gehoeren nicht hinein.
        if (traits.course != Course.HAUPTGERICHT) return false
        if (!DietaryFilter.of(profile).allows(traits)) return false
        if (profile.appliances.isNotEmpty()) {
            if (!profile.appliances.containsAll(traits.requiredAppliances)) return false
        }
        return true
    }

    private fun score(candidate: PlanCandidate, profile: UserProfile): Double {
        val traits = candidate.traits
        var score = 0.0
        profile.moods.forEach { if (it in traits.moods) score += MOOD_POINTS }
        if (Mood.SCHNELL in profile.moods) {
            candidate.recipe.prepTimeMinutes?.let {
                score +=
                    (QUICK_LIMIT - it).coerceIn(-QUICK_LIMIT, QUICK_LIMIT) / QUICK_LIMIT
            }
        }
        // Kuechen sind eine Vorliebe: Gewaehltes kommt nach vorn, klar anderes etwas nach
        // hinten - aber nichts faellt heraus.
        if (profile.cuisines.isNotEmpty() && traits.cuisines.isNotEmpty()) {
            score += if (traits.cuisines.any { it in profile.cuisines }) CUISINE_POINTS else OTHER_CUISINE_POINTS
        }
        // Wer ein besonderes Geraet hat, soll auch Rezepte dafuer bekommen.
        if (traits.requiredAppliances.any { it.isSpecial && it in profile.appliances }) score += APPLIANCE_POINTS
        if (candidate.recipe.photoUri != null) score += IMAGE_POINTS
        return score
    }

    /** Wie gut ein Rezept zu einer Tageszeit passt: Mittags zaehlt, was schnell geht oder leicht ist. */
    private fun slotFit(candidate: PlanCandidate, slot: MealSlot): Double {
        if (slot != MealSlot.MITTAG) return 0.0
        var fit = 0.0
        val minutes = candidate.recipe.prepTimeMinutes
        if (minutes != null && minutes <= LUNCH_MINUTES) fit += LUNCH_POINTS
        if (Mood.LEICHT in candidate.traits.moods) fit += LUNCH_POINTS / 2
        if (candidate.traits.words.any { word -> LUNCH_WORDS.any { word.endsWith(it) } }) fit += LUNCH_POINTS
        return fit
    }

    /** Waehlt der Reihe nach, aber nicht zweimal dasselbe Etikett hintereinander, wenn es Auswahl gibt. */
    private fun pickVaried(
        ranked: List<Pair<PlanCandidate, Double>>,
        count: Int,
    ): List<Pair<PlanCandidate, Double>> {
        val picked = mutableListOf<Pair<PlanCandidate, Double>>()
        val pool = ranked.toMutableList()
        while (picked.size < count && pool.isNotEmpty()) {
            val lastLabel = picked
                .lastOrNull()
                ?.first
                ?.traits
                ?.label
            val next = pool.firstOrNull { it.first.traits.label != lastLabel } ?: pool.first()
            picked += next
            pool.remove(next)
        }
        return picked
    }

    private companion object {
        const val MIN_INGREDIENTS = 2
        const val MAX_SWAPS = 8
        const val MOOD_POINTS = 3.0
        const val CUISINE_POINTS = 2.5
        const val OTHER_CUISINE_POINTS = -1.5
        const val APPLIANCE_POINTS = 1.5
        const val IMAGE_POINTS = 0.5
        const val QUICK_LIMIT = 60.0
        const val COST_WEIGHT = 0.15
        const val LUNCH_MINUTES = 30
        const val LUNCH_POINTS = 2.0

        /** Unter so vielen der besten freien Rezepte wird beim Ersetzen gelost. */
        const val REPLACEMENT_CHOICES = 4

        val LUNCH_WORDS = listOf("salat", "suppe", "sandwich", "wrap", "wraps", "bowl", "toast", "omelette", "tortilla")
    }
}
