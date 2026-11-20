package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.profile.LanguageRegion
import ch.rezeptli.app.domain.profile.MealSlot
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
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
 *
 * Was schon geladen wurde, bleibt als Vorrat liegen: Wer eine Mahlzeit ersetzt, soll
 * sofort einen anderen Vorschlag bekommen und nicht erst auf vierzig Seiten warten.
 */
@Singleton
class PlanCandidateSource @Inject constructor(
    private val buildDeck: BuildSwipeDeckUseCase,
    private val loadWebRecipe: LoadWebRecipeUseCase,
    private val recipeRepository: RecipeRepository,
    private val classifier: RecipeClassifier,
) {
    /** Frisch geladene Rezepte der letzten Zuege - die eigene Sammlung wird jedes Mal neu gelesen. */
    private var fresh: List<PlanCandidate> = emptyList()

    /**
     * Mit [refresh] wird zusaetzlich neu gezogen; sonst kommt der Vorrat der letzten Zuege.
     *
     * Gezogen wird fuer [profile]: Was sich schon am Titel als unpassend erkennen laesst
     * (Getraenke, Desserts, "Lachs" ohne Fisch), wird gar nicht erst geladen.
     */
    suspend fun candidates(refresh: Boolean, profile: UserProfile): List<PlanCandidate> {
        val own = ownRecipes()
        if (refresh || fresh.isEmpty()) {
            val known = (own + fresh).mapNotNullTo(mutableSetOf()) { it.recipe.sourceUrl }
            fresh = (fresh + loadFresh(exclude = known, profile = profile)).takeLast(MAX_STOCK)
        }
        return (own + fresh).distinctBy { it.key }
    }

    private suspend fun ownRecipes(): List<PlanCandidate> =
        recipeRepository
            .getFilteredRecipeIds(RecipeFilter())
            .shuffled()
            .take(OWN_LIMIT)
            .mapNotNull { recipeRepository.getRecipe(it) }
            .map { PlanCandidate(it, classifier.classify(it)) }

    private suspend fun loadFresh(exclude: Set<String>, profile: UserProfile): List<PlanCandidate> {
        val size = (profile.mealsPerWeek * CARDS_PER_MEAL).coerceIn(MIN_DECK, MAX_DECK)
        val rules = CardRules.of(profile).copy(mainsOnly = true)
        val entries = buildDeck(size = size, exclude = exclude, onlyRegion = LanguageRegion.DEUTSCH, rules = rules)
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

        /** Pro Mahlzeit ein paar Seiten: Ein Teil laesst sich nicht laden, ein Teil passt nicht. */
        const val CARDS_PER_MEAL = 4
        const val MIN_DECK = 40
        const val MAX_DECK = 64
        const val MAX_STOCK = 160
        const val PARALLEL = 6
        const val PAGE_TIMEOUT_MS = 12_000L
    }
}

/**
 * Schreibt einen Plan in die Datenbank: Rezepte (ohne Dubletten), Eintraege und Einkauf.
 *
 * Ein Rezept, das nur fuer den Plan geholt wird, landet nicht in der Sammlung - der Plan
 * soll "Meine Rezepte" nicht ungefragt fuellen. Es bleibt gespeichert, solange es im Plan
 * steht, und verschwindet danach wieder.
 */
class PlanPersister @Inject constructor(
    private val recipeRepository: RecipeRepository,
    private val shoppingRepository: ShoppingListRepository,
    private val planRepository: PlanRepository,
) {
    suspend fun idOf(recipe: Recipe): Long =
        recipe.id.takeIf { it != 0L }
            ?: recipe.sourceUrl?.let { recipeRepository.findIdBySourceUrl(it) }
            ?: recipeRepository.saveRecipe(recipe.copy(inCollection = false))

    /**
     * Mit [keep] bleibt, was die Woche schon erlebt hat: abgehakte Einkaeufe und der
     * Vermerk "gekocht". Ein ganz neu gebauter Plan faengt ohne beides an.
     */
    suspend fun save(draft: PlanDraft, keep: Boolean) {
        val cooked = if (keep) cookedMarks() else emptyMap()
        val writes = draft.meals.map { meal ->
            val id = idOf(meal.recipe)
            meal.toWrite(id, cooked[meal.place to id])
        }
        planRepository.savePlan(writes, carryChecked(draft.basket, keep))
        recipeRepository.deleteUnusedPlanRecipes()
    }

    suspend fun saveReplacement(meal: PlannedMeal, basket: List<ShoppingItem>) {
        planRepository.replaceEntry(meal.toWrite(idOf(meal.recipe), cookedAt = null), carryChecked(basket, keep = true))
        recipeRepository.deleteUnusedPlanRecipes()
    }

    private suspend fun cookedMarks(): Map<Pair<PlanPlace, Long>, Long?> =
        planRepository.currentPlan().associate { (it.place to it.recipeId) to it.cookedAt }

    /** Was schon abgehakt war, bleibt es - wer mitten im Einkauf eine Mahlzeit tauscht, fängt nicht von vorn an. */
    private suspend fun carryChecked(basket: List<ShoppingItem>, keep: Boolean): List<ShoppingItem> {
        if (!keep) return basket
        val checked = shoppingRepository
            .getItems()
            .filter { it.fromPlan && it.isChecked }
            .mapTo(mutableSetOf()) { it.matchKey }
        return basket.map { if (it.matchKey in checked) it.copy(isChecked = true) else it }
    }

    private fun PlannedMeal.toWrite(recipeId: Long, cookedAt: Long?) = PlanWrite(
        day = day,
        slot = slot,
        recipeId = recipeId,
        servings = servings,
        cost = cost,
        label = label,
        cookedAt = cookedAt,
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
    private val memory: PlanMemory,
) {
    suspend operator fun invoke(onStage: (PlanStage) -> Unit = {}): PlanOutcome {
        onStage(PlanStage.FINDING)
        val profile = profileRepository.profile.first()
        val candidates = source.candidates(refresh = true, profile = profile)
        if (candidates.isEmpty()) return PlanOutcome.Failed(PlanFailure.NO_RECIPES)

        onStage(PlanStage.CHOOSING)
        val draft = generator.generate(candidates, profile)
        if (draft.isIncomplete) return PlanOutcome.Failed(PlanFailure.NOTHING_FITS)

        onStage(PlanStage.SHOPPING)
        // Eine neue Woche: Was in der alten verworfen wurde, darf wieder vorgeschlagen werden.
        memory.clear()
        persister.save(draft, keep = false)
        return PlanOutcome.Built(draft)
    }
}

/**
 * Tauscht eine Mahlzeit gegen ein anderes passendes Rezept.
 *
 * Das verworfene Rezept merkt sich der Plan: Es kommt in dieser Woche nicht wieder. Geht
 * der Vorrat an unverbrauchten Vorschlaegen zur Neige, wird frisch aus dem Verzeichnis
 * nachgezogen - so findet das Ersetzen wirklich Neues, statt zwischen denselben paar
 * Rezepten zu kreisen.
 */
class ReplaceMealUseCase @Inject constructor(
    private val profileRepository: UserProfileRepository,
    private val planRepository: PlanRepository,
    private val recipeRepository: RecipeRepository,
    private val source: PlanCandidateSource,
    private val classifier: RecipeClassifier,
    private val generator: PlanGenerator,
    private val persister: PlanPersister,
    private val memory: PlanMemory,
) {
    /** `false`, wenn es keine Alternative gab. */
    suspend operator fun invoke(day: Weekday, slot: MealSlot): Boolean {
        val place = PlanPlace(day, slot)
        val profile = profileRepository.profile.first()
        val draft = currentDraft(profile)
        val current = draft.meals.firstOrNull { it.place == place } ?: return false

        val avoid = memory.rejected() + current.candidate.key
        val taken = draft.meals.mapTo(mutableSetOf()) { it.candidate.key }

        var pool = source.candidates(refresh = false, profile = profile)
        if (generator.countUsable(pool, profile, without = avoid + taken) < MIN_CHOICES) {
            // Kaum noch Unverbrauchtes - frisch nachziehen, bevor wieder dasselbe kommt.
            pool = source.candidates(refresh = true, profile = profile)
        }

        val replaced = generator.replace(draft, place, pool, profile, avoid)
        if (replaced === draft) return false

        memory.reject(current.candidate.key)
        persister.saveReplacement(replaced.meals.first { it.place == place }, replaced.basket)
        return true
    }

    private suspend fun currentDraft(profile: UserProfile): PlanDraft {
        val meals = planRepository.currentPlan().mapNotNull { entry ->
            val recipe = recipeRepository.getRecipe(entry.recipeId) ?: return@mapNotNull null
            PlannedMeal(
                day = entry.day,
                slot = entry.slot,
                candidate = PlanCandidate(recipe, classifier.classify(recipe)),
                servings = entry.servings,
                cost = entry.cost,
            )
        }
        return PlanDraft(meals, basket = emptyList(), budget = profile.budget)
    }

    private companion object {
        /** Unter so vielen freien Vorschlaegen wird nachgezogen. */
        const val MIN_CHOICES = 3
    }
}

/**
 * Rechnet den bestehenden Plan auf die geltenden Angaben um: Personenzahl und Preise.
 *
 * Die Gerichte bleiben, wo sie sind - nur Mengen, Kosten und der Einkauf folgen dem Profil.
 * Wer den Hauptladen wechselt oder fuer eine Person mehr kocht, soll das sofort sehen, ohne
 * dafuer die ganze Woche neu wuerfeln zu muessen. Was gekocht oder schon eingekauft ist,
 * bleibt abgehakt.
 */
class AdjustPlanUseCase @Inject constructor(
    private val profileRepository: UserProfileRepository,
    private val planRepository: PlanRepository,
    private val recipeRepository: RecipeRepository,
    private val classifier: RecipeClassifier,
    private val generator: PlanGenerator,
    private val persister: PlanPersister,
) {
    private val lock = Mutex()

    /** `true`, wenn es einen Plan gab, der umgerechnet wurde. */
    suspend operator fun invoke(): Boolean = lock.withLock {
        // Das Profil wird erst im Schutz der Sperre gelesen: Von zwei schnellen Aenderungen
        // hintereinander zaehlt die letzte, nicht die, deren Rechnung laenger dauert.
        val profile = profileRepository.profile.first()
        val persons = profile.persons

        val meals = planRepository.currentPlan().mapNotNull { entry ->
            val recipe = recipeRepository.getRecipe(entry.recipeId) ?: return@mapNotNull null
            PlannedMeal(
                day = entry.day,
                slot = entry.slot,
                candidate = PlanCandidate(recipe, classifier.classify(recipe)),
                servings = persons,
                cost = generator.mealCostOf(generator.scaledRecipe(recipe, persons), profile),
            )
        }
        if (meals.isEmpty()) return@withLock false

        val basket = generator.basketFor(meals.map { it.candidate to it.cost }, persons, profile)
        persister.save(PlanDraft(meals, basket, profile.budget), keep = true)
        true
    }
}

/**
 * Nimmt Treffer aus der gemeinsamen Runde in den Wochenplan auf.
 *
 * Sie kommen auf die freien Plaetze der Kochtage; sind keine mehr frei, auf irgendeinen
 * freien Platz der Woche. Ist die Woche voll, bleibt der Rest draussen - ein Plan ersetzt
 * nichts, was schon steht.
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
    /**
     * Gibt zurueck, wie viele Rezepte tatsaechlich in den Plan kamen.
     *
     * [ownIds] nennt zu Treffern, die schon auf diesem Geraet liegen, die hiesige
     * Rezeptnummer - sie muessen nicht geladen werden. Alle anderen holt die App von ihrer
     * Adresse; ein Treffer ohne Adresse (ein selbst erfasstes Rezept der anderen Person)
     * laesst sich nicht uebernehmen.
     */
    suspend operator fun invoke(matches: List<SharedRecipe>, ownIds: Map<Long, Long> = emptyMap()): Int {
        val profile = profileRepository.profile.first()
        val existing = planRepository.currentPlan()
        val occupied = existing.mapTo(mutableSetOf()) { it.place }

        val everywhere = MealSlot.entries
            .sortedByDescending { it == MealSlot.ABEND }
            .flatMap { slot -> Weekday.entries.map { day -> PlanPlace(day, slot) } }
        val free = (generator.placesOf(profile) + everywhere).distinct().filter { it !in occupied }
        if (free.isEmpty()) return 0

        val planned = existing.mapNotNull { entry -> recipeRepository.getRecipe(entry.recipeId)?.let { entry to it } }
        val known = planned.mapTo(mutableSetOf()) { (_, recipe) -> PlanCandidate.keyOf(recipe) }

        val additions = mutableListOf<Recipe>()
        for (match in matches) {
            if (additions.size >= free.size) break
            val recipe = ownIds[match.recipeId]?.let { recipeRepository.getRecipe(it) }
                ?: match.sourceUrl?.let { url -> (loadWebRecipe(url) as? WebImportOutcome.Loaded)?.recipe }
                ?: continue
            // Was schon im Plan steht, kommt kein zweites Mal hinein.
            if (known.add(PlanCandidate.keyOf(recipe))) additions += recipe
        }
        if (additions.isEmpty()) return 0

        val kept = planned.map { (entry, recipe) ->
            PlannedMeal(
                day = entry.day,
                slot = entry.slot,
                candidate = PlanCandidate(recipe, classifier.classify(recipe)),
                servings = entry.servings,
                cost = entry.cost,
            )
        }
        val added = additions.zip(free).map { (recipe, place) ->
            val candidate = PlanCandidate(recipe, classifier.classify(recipe))
            val scaled = generator.scaledRecipe(recipe, profile.persons)
            PlannedMeal(place.day, place.slot, candidate, profile.persons, generator.mealCostOf(scaled, profile))
        }

        val meals = (kept + added).sortedWith(compareBy({ it.day.ordinal }, { it.slot.ordinal }))
        val basket = generator.basketFor(meals.map { it.candidate to it.cost }, profile.persons, profile)
        persister.save(PlanDraft(meals, basket, profile.budget), keep = true)
        return added.size
    }
}
