package ch.rezeptli.app.presentation.plan

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.plan.AdjustPlanUseCase
import ch.rezeptli.app.domain.plan.MealLabel
import ch.rezeptli.app.domain.plan.PlanGenerator
import ch.rezeptli.app.domain.plan.PlanPersister
import ch.rezeptli.app.domain.plan.PlanWrite
import ch.rezeptli.app.domain.plan.RecipeClassifier
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.Mood
import ch.rezeptli.app.domain.profile.StoreChain
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.domain.settings.AppearanceRepository
import ch.rezeptli.app.domain.shopping.ShoppingListAggregator
import ch.rezeptli.app.fake.FakePlanRepository
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.fake.FakeShoppingListRepository
import ch.rezeptli.app.fake.FakeUserProfileRepository
import ch.rezeptli.app.util.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Die Vorlieben-Seite: Was sofort gilt und was erst mit dem naechsten Plan.
 *
 * Aufgefallen war, dass Personenzahl und Laden zwar gespeichert wurden, der bestehende Plan
 * aber weiter mit den alten Mengen und Preisen dastand - unter dem Namen des neuen Ladens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private class FakeAppearance : AppearanceRepository {
        override val reduceMotion = MutableStateFlow(false)

        override suspend fun setReduceMotion(reduce: Boolean) {
            reduceMotion.value = reduce
        }
    }

    private val gratin = Recipe(
        id = 1L,
        title = "Kartoffelgratin",
        instructions = "Im Ofen backen.",
        ingredients = listOf(
            Ingredient(name = "Kartoffeln", amount = 800.0, unit = IngredientUnit.GRAMM),
            Ingredient(name = "Rahm", amount = 2.0, unit = IngredientUnit.DEZILITER),
        ),
        servings = 4,
    )

    private val profile = FakeUserProfileRepository(
        UserProfile(
            country = Country.SCHWEIZ,
            householdSize = 2,
            stores = listOf(StoreChain.COOP, StoreChain.ALDI),
            onboardingCompleted = true,
        ),
    )
    private val plan = FakePlanRepository()
    private val recipes = FakeRecipeRepository(listOf(gratin)).also { it.plan = plan }
    private val appearance = FakeAppearance()

    private fun createViewModel() = PreferencesViewModel(
        profileRepository = profile,
        appearanceRepository = appearance,
        adjustPlan = AdjustPlanUseCase(
            profileRepository = profile,
            planRepository = plan,
            recipeRepository = recipes,
            classifier = RecipeClassifier(),
            generator = PlanGenerator(ShoppingListAggregator()),
            persister = PlanPersister(recipes, FakeShoppingListRepository(), plan),
        ),
    )

    private suspend fun mitPlan() {
        plan.savePlan(
            listOf(PlanWrite(Weekday.MONTAG, MealSlot.ABEND, gratin.id, 2, 5.0, MealLabel.ALLTAG)),
            emptyList(),
        )
    }

    @Test
    fun `eine Person mehr rechnet den bestehenden Plan sofort um`() = runTest {
        mitPlan()
        val viewModel = createViewModel()

        viewModel.onPersonsChange(+1)
        viewModel.onPersonsChange(+1)
        advanceUntilIdle()

        assertEquals(4, profile.current.persons)
        val mahlzeit = plan.currentPlan().single()
        assertEquals(gratin.id, mahlzeit.recipeId)
        assertEquals(4, mahlzeit.servings)
        // Fuer vier braucht es, was im Rezept steht - vorher war es die Haelfte.
        assertEquals(800.0, plan.savedBasket.first { it.name == "Kartoffeln" }.amount)
    }

    @Test
    fun `ein neuer Hauptladen rechnet die Preise des bestehenden Plans um`() = runTest {
        mitPlan()
        val viewModel = createViewModel()
        // Erst einmal mit dem geltenden Laden rechnen lassen, dann wechseln.
        viewModel.onPersonsChange(+1)
        advanceUntilIdle()
        val beiCoop = plan.currentPlan().single().cost

        viewModel.onMainStore(StoreChain.ALDI)
        advanceUntilIdle()

        assertEquals(StoreChain.ALDI, profile.current.store)
        val beiAldi = plan.currentPlan().single().cost
        assertEquals(beiCoop * StoreChain.ALDI.priceFactor / StoreChain.COOP.priceFactor, beiAldi, 0.01)
        assertTrue(beiAldi < beiCoop)
    }

    @Test
    fun `ein weiterer Laden zum Vergleich aendert am Plan nichts`() = runTest {
        mitPlan()
        val viewModel = createViewModel()
        val vorher = plan.currentPlan()

        viewModel.onStoreToggle(StoreChain.MIGROS)
        advanceUntilIdle()

        assertEquals(listOf(StoreChain.COOP, StoreChain.ALDI, StoreChain.MIGROS), profile.current.stores)
        assertSame(vorher.single(), plan.currentPlan().single())
    }

    @Test
    fun `eine Stimmung laesst den bestehenden Plan in Ruhe`() = runTest {
        mitPlan()
        val viewModel = createViewModel()
        val vorher = plan.currentPlan()

        viewModel.onMoodToggle(Mood.SCHNELL)
        advanceUntilIdle()

        assertEquals(setOf(Mood.SCHNELL), profile.current.moods)
        assertSame(vorher.single(), plan.currentPlan().single())
    }

    @Test
    fun `zuruecksetzen loescht die Angaben, aber nicht die Gerichte im Plan`() = runTest {
        profile.update { it.copy(firstName = "Mara", householdSize = 5) }
        mitPlan()
        val viewModel = createViewModel()

        viewModel.onResetRequest()
        viewModel.onResetConfirm()
        advanceUntilIdle()

        assertEquals(UserProfile(onboardingCompleted = true), profile.current)
        val mahlzeit = plan.currentPlan().single()
        assertEquals(gratin.id, mahlzeit.recipeId)
        // Ohne Angabe wird fuer zwei gekocht - der Plan zeigt, was jetzt gilt.
        assertEquals(2, mahlzeit.servings)
    }

    @Test
    fun `Bewegung reduzieren gehoert nicht zu den Vorlieben und uebersteht das Zuruecksetzen`() = runTest {
        val viewModel = createViewModel()

        viewModel.onReduceMotionChange(true)
        viewModel.onResetConfirm()
        advanceUntilIdle()

        assertTrue(appearance.reduceMotion.value)
    }
}
