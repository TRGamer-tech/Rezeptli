package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.profile.LanguageRegion
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.domain.repository.RecipeRepository
import ch.rezeptli.app.domain.repository.ShoppingListRepository
import ch.rezeptli.app.domain.repository.UserProfileRepository
import ch.rezeptli.app.domain.usecase.BuildSwipeDeckUseCase
import ch.rezeptli.app.domain.usecase.LoadWebRecipeUseCase
import ch.rezeptli.app.domain.usecase.WebImportOutcome
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Die drei Schritte, die beim Bauen des Plans nacheinander angezeigt werden - und wirklich passieren. */
enum class PlanStage {
    FINDING,
    CHOOSING,
    SHOPPING,
}

enum class PlanFailure {
    /** Es liessen sich keine Rezepte laden. */
    NO_RECIPES,

    /** Es gab Rezepte, aber keines passte zu Ernaehrung, Unvertraeglichkeiten und Geraeten. */
    NOTHING_FITS,
}

sealed interface PlanOutcome {
    data class Built(val draft: PlanDraft) : PlanOutcome

    data class Failed(val reason: PlanFailure) : PlanOutcome
}

/**
 * Beschafft die Rezepte, aus denen ein Plan gewaehlt wird.
 *
 * Quellen sind die eigene Sammlung und ein frischer Zug aus dem taeglich gebauten
 * Verzeichnis. Die Verzeichniseintraege kennen nur Titel und Bild, also werden die
 * Rezeptseiten geladen - mit wenigen gleichzeitigen Abrufen und einer Zeitgrenze pro
 * Seite, damit eine langsame Quelle den Plan nicht aufhaelt.
 */
@Singleton
class PlanCandidateSource @Inject constructor(
    private val buildDeck: BuildSwipeDeckUseCase,
    private val loadWebRecipe: LoadWebRecipeUseCase,
    private val recipeRepository: RecipeRepository,
    private val classifier: RecipeClassifier,
) {
    private var cache: List<PlanCandidate> = emptyList()

    /** Mit [refresh] wird neu gezogen; sonst kommt, was der letzte Plan geladen hat. */
    suspend fun candidates(refresh: Boolean): List<PlanCandidate> {
        if (!refresh && cache.isNotEmpty()) return cache

        val own = ownRecipes()
        val ownUrls = own.mapNotNullTo(mutableSetOf()) { it.recipe.sourceUrl }
        val fresh = loadFresh(exclude = ownUrls + cache.mapNotNull { it.recipe.sourceUrl })

        cache = (own + fresh).distinctBy { it.recipe.sourceUrl ?: it.recipe.title.lowercase() }
        return cache
    }

    private suspend fun ownRecipes(): List<PlanCandidate> =
        recipeRepository
            .getFilteredRecipeIds(RecipeFilter())
            .shuffled()
            .take(OWN_LIMIT)
            .mapNotNull { recipeRepository.getRecipe(it) }
            .map { PlanCandidate(it, classifier.classify(it)) }

    private suspend fun loadFresh(exclude: Set<String>): List<PlanCandidate> {
        val entries = buildDeck(size = DECK_SIZE, exclude = exclude, onlyRegion = LanguageRegion.DEUTSCH)
        if (entries.isEmpty()) return emptyList()

        val gate = Semaphore(PARALLEL)
        return coroutineScope {
            entries
                .map { entry ->
                    async {
                        gate.withPermit {
                            withTimeoutOrNull(PAGE_TIMEOUT_MS) { loadWebRecipe(entry.url) }
                        }
                    }
                }.awaitAll()
        }.mapNotNull { outcome ->
            (outcome as? WebImportOutcome.Loaded)?.recipe
        }.map { PlanCandidate(it, classifier.classify(it)) }
    }

    private companion object {
        const val OWN_LIMIT = 40
        const val DECK_SIZE = 40
        const val PARALLEL = 6
        const val PAGE_TIMEOUT_MS = 12_000L
    }
}

/** Schreibt einen Plan in die Datenbank: Rezepte (ohne Dubletten), Eintraege und Einkauf. */
class PlanPersister @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val shoppingRepository: ShoppingListRepository,
    private val planRepository: PlanRepository,
) {
    suspend fun idOf(recipe: Recipe): Long =
        recipe.id.takeIf { it != 0L }
            ?: recipe.sourceUrl?.let { recipeRepository.findIdBySourceUrl(it) }
            ?: recipeRepository.saveRecipe(recipe)

    suspend fun save(draft: PlanDraft, keepChecked: Boolean) {
        val writes = draft.meals.map { it.toWrite(idOf(it.recipe)) }
        planRepository.savePlan(writes, carryChecked(draft.basket, keepChecked))
    }

    suspend fun saveReplacement(meal: PlannedMeal, basket: List<ShoppingItem>) {
        planRepository.replaceEntry(meal.toWrite(idOf(meal.recipe)), carryChecked(basket, keep = true))
    }

    /** Was schon abgehakt war, bleibt es - wer mitten im Einkauf eine Mahlzeit tauscht, fängt nicht von vorn an. */
    private suspend fun carryChecked(basket: List<ShoppingItem>, keep: Boolean): List<ShoppingItem> {
        if (!keep) return basket
        val checked = shoppingRepository
            .getItems()
            .filter { it.fromPlan && it.isChecked }
            .mapTo(mutableSetOf()) { it.matchKey }
        return basket.map { if (it.matchKey in checked) it.copy(isChecked = true) else it }
    }

    private fun PlannedMeal.toWrite(recipeId: Long) = PlanWrite(
        day = day,
        recipeId = recipeId,
        servings = servings,
        cost = cost,
        label = label,
    )
}

class ObservePlanUseCase @Inject constructor(
    private val repository: PlanRepository,
) {
    operator fun invoke(): Flow<List<PlanEntry>> = repository.observePlan()
}

/** Baut den Wochenplan aus Profil und Rezepten neu. */
class GeneratePlanUseCase @Inject constructor(
    private val profileRepository: UserProfileRepository,
    private val source: PlanCandidateSource,
    private val generator: PlanGenerator,
    private val persister: PlanPersister,
) {
    suspend operator fun invoke(onStage: (PlanStage) -> Unit = {}): PlanOutcome {
        onStage(PlanStage.FINDING)
        val profile = profileRepository.profile.first()
        val candidates = source.candidates(refresh = true)
        if (candidates.isEmpty()) return PlanOutcome.Failed(PlanFailure.NO_RECIPES)

        onStage(PlanStage.CHOOSING)
        val draft = generator.generate(candidates, profile)
        if (draft.isIncomplete) return PlanOutcome.Failed(PlanFailure.NOTHING_FITS)

        onStage(PlanStage.SHOPPING)
        persister.save(draft, keepChecked = false)
        return PlanOutcome.Built(draft)
    }
}

/** Tauscht die Mahlzeit eines Tages gegen ein anderes passendes Rezept. */
class ReplaceMealUseCase @Inject constructor(
    private val profileRepository: UserProfileRepository,
    private val planRepository: PlanRepository,
    private val recipeRepository: RecipeRepository,
    private val source: PlanCandidateSource,
    private val classifier: RecipeClassifier,
    private val generator: PlanGenerator,
    private val persister: PlanPersister,
) {
    /** `false`, wenn es keine Alternative gab. */
    suspend operator fun invoke(day: Weekday): Boolean {
        val profile = profileRepository.profile.first()
        val draft = currentDraft(profile)
        if (draft.meals.none { it.day == day }) return false

        var pool = source.candidates(refresh = false)
        var replaced = generator.replace(draft, day, pool, profile)
        if (replaced === draft) {
            // Der Vorrat ist aufgebraucht - einmal frisch ziehen, bevor aufgegeben wird.
            pool = source.candidates(refresh = true)
            replaced = generator.replace(draft, day, pool, profile)
        }
        if (replaced === draft) return false

        persister.saveReplacement(replaced.meals.first { it.day == day }, replaced.basket)
        return true
    }

    private suspend fun currentDraft(profile: UserProfile): PlanDraft {
        val meals = planRepository.currentPlan().mapNotNull { entry ->
            val recipe = recipeRepository.getRecipe(entry.recipeId) ?: return@mapNotNull null
            PlannedMeal(entry.day, PlanCandidate(recipe, classifier.classify(recipe)), entry.servings, entry.cost)
        }
        return PlanDraft(meals, basket = emptyList(), budget = profile.weeklyBudget ?: UserProfile.DEFAULT_BUDGET)
    }
}

/**
 * Nimmt Treffer aus der gemeinsamen Runde in den Wochenplan auf.
 *
 * Sie kommen auf die freien Kochtage; sind keine mehr frei, auf irgendeinen freien Tag.
 * Ist die Woche voll, bleibt der Rest draussen - ein Plan ersetzt nichts, was schon steht.
 */
class AddToPlanUseCase @Inject constructor(
    private val profileRepository: UserProfileRepository,
    private val planRepository: PlanRepository,
    private val recipeRepository: RecipeRepository,
    private val loadWebRecipe: LoadWebRecipeUseCase,
    private val classifier: RecipeClassifier,
    private val generator: PlanGenerator,
    private val persister: PlanPersister,
) {
    /** Gibt zurueck, wie viele Rezepte tatsaechlich in den Plan kamen. */
    suspend operator fun invoke(matches: List<SharedRecipe>): Int {
        val profile = profileRepository.profile.first()
        val existing = planRepository.currentPlan()
        val occupied = existing.mapTo(mutableSetOf()) { it.day }

        val preferred = profile.cookDays
            .sortedBy { it.ordinal }
            .ifEmpty { Weekday.entries.take(UserProfile.DEFAULT_COOK_DAYS) }
        val free = (preferred + Weekday.entries).distinct().filter { it !in occupied }
        if (free.isEmpty()) return 0

        val known = existing.mapNotNull { recipeRepository.getRecipe(it.recipeId)?.sourceUrl }.toSet()
        val additions = matches
            .mapNotNull { it.sourceUrl }
            .filter { it !in known }
            .take(free.size)
            .mapNotNull { url -> (loadWebRecipe(url) as? WebImportOutcome.Loaded)?.recipe }
        if (additions.isEmpty()) return 0

        val kept = existing.mapNotNull { entry ->
            val recipe = recipeRepository.getRecipe(entry.recipeId) ?: return@mapNotNull null
            PlannedMeal(entry.day, PlanCandidate(recipe, classifier.classify(recipe)), entry.servings, entry.cost)
        }
        val added = additions.zip(free).map { (recipe, day) ->
            val candidate = PlanCandidate(recipe, classifier.classify(recipe))
            val scaled = generator.scaledRecipe(recipe, profile.persons)
            PlannedMeal(day, candidate, profile.persons, generator.mealCostOf(scaled, profile))
        }

        val meals = (kept + added).sortedBy { it.day.ordinal }
        val basket = generator.basketFor(meals.map { it.candidate to it.cost }, profile.persons, profile)
        persister.save(
            PlanDraft(meals, basket, profile.weeklyBudget ?: UserProfile.DEFAULT_BUDGET),
            keepChecked = true,
        )
        return added.size
    }
}
