package ch.rezeptli.app.presentation.recipelist

import app.cash.turbine.test
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.domain.plan.MealLabel
import ch.rezeptli.app.domain.plan.PlanEntry
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.domain.usecase.DeleteRecipeUseCase
import ch.rezeptli.app.domain.usecase.MarkRecipeCookedUseCase
import ch.rezeptli.app.domain.usecase.ObservePagedRecipesUseCase
import ch.rezeptli.app.domain.usecase.ObserveRecipeCountUseCase
import ch.rezeptli.app.domain.usecase.ObserveTagsUseCase
import ch.rezeptli.app.fake.FakePlanMemory
import ch.rezeptli.app.fake.FakePlanRepository
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.fake.FakeUserProfileRepository
import ch.rezeptli.app.util.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeListViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repository = FakeRecipeRepository(
        listOf(
            Recipe(id = 1L, title = "Älplermagronen", tags = listOf("vegetarisch"), prepTimeMinutes = 40),
            Recipe(
                id = 2L,
                title = "Rösti",
                tags = listOf("vegetarisch", "schnell"),
                prepTimeMinutes = 25,
                lastCookedAt = 1_700_000_000_000L,
            ),
            // Nur fuer den Wochenplan geholt - gehoert nicht zu "Meine Rezepte".
            Recipe(id = 3L, title = "Plan-Curry", tags = listOf("asiatisch"), inCollection = false),
        ),
    )

    private val planRepository = FakePlanRepository(
        listOf(
            PlanEntry(
                day = Weekday.MONTAG,
                slot = MealSlot.ABEND,
                recipeId = 1L,
                title = "Älplermagronen",
                photoUri = null,
                prepTimeMinutes = 40,
                servings = 2,
                cost = 8.0,
                label = MealLabel.ALLTAG,
            ),
        ),
    )

    private fun createViewModel() = RecipeListViewModel(
        observePagedRecipes = ObservePagedRecipesUseCase(repository),
        observeRecipeCount = ObserveRecipeCountUseCase(repository),
        observeTags = ObserveTagsUseCase(repository),
        profileRepository = FakeUserProfileRepository(),
        deleteRecipe = DeleteRecipeUseCase(repository, planRepository),
        markRecipeCooked = MarkRecipeCookedUseCase(repository, planRepository, FakePlanMemory()),
    )

    private val aelpler = RecipeSummary(id = 1L, title = "Älplermagronen")
    private val roesti = RecipeSummary(id = 2L, title = "Rösti", lastCookedAt = 1_700_000_000_000L)

    @Test
    fun `kennt die Gesamtzahl der Rezepte und alle vergebenen Tags`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Was nur fuer den Wochenplan gespeichert ist, zaehlt nicht zur Sammlung - weder in der
        // Anzahl noch bei den Tags.
        assertEquals(2, state.totalRecipeCount)
        assertTrue(state.hasAnyRecipes)
        // Die meistbenutzten zuerst: "vegetarisch" tragen beide Rezepte, "schnell" nur eines.
        assertEquals(listOf("vegetarisch", "schnell"), state.availableTags)
    }

    @Test
    fun `der Filter zeigt wahlweise Gekochtes oder noch nie Gekochtes`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onCookedFilterChange(true)
        assertEquals(true, viewModel.uiState.value.filter.cooked)
        assertTrue(viewModel.uiState.value.isFilterActive)
        assertEquals(listOf("Rösti"), repository.visible(viewModel.uiState.value.filter).map { it.title })

        viewModel.onCookedFilterChange(false)
        assertEquals(listOf("Älplermagronen"), repository.visible(viewModel.uiState.value.filter).map { it.title })

        viewModel.onCookedFilterChange(null)
        assertEquals(2, repository.visible(viewModel.uiState.value.filter).size)
        assertFalse(viewModel.uiState.value.isFilterActive)
    }

    @Test
    fun `nach links gewischt wird erst gefragt und dann entfernt`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onRemoveRequest(roesti)

        // Der Wisch allein entfernt nichts - er oeffnet die Rueckfrage.
        assertEquals(roesti, viewModel.uiState.value.pendingRemoval)
        assertNotNull(repository.current(2L))

        viewModel.events.test {
            viewModel.onRemoveConfirm()
            assertEquals(RecipeListEvent.Removed("Rösti"), awaitItem())
        }
        assertNull(repository.current(2L))
        assertNull(viewModel.uiState.value.pendingRemoval)
    }

    @Test
    fun `wer die Rueckfrage schliesst, behaelt das Rezept`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onRemoveRequest(roesti)
        viewModel.onDialogDismiss()
        viewModel.onRemoveConfirm()

        assertNotNull(repository.current(2L))
        assertTrue(repository.deletedIds.isEmpty())
    }

    @Test
    fun `ein Rezept, das diese Woche im Plan steht, verschwindet nur aus der Sammlung`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onRemoveRequest(aelpler)
        viewModel.onRemoveConfirm()

        // Sonst risse das Entfernen ein Loch in die Woche, waehrend die Zutaten noch auf der
        // Einkaufsliste stehen.
        assertTrue(repository.deletedIds.isEmpty())
        assertEquals(false, repository.current(1L)?.inCollection)
        assertEquals(1, viewModel.uiState.value.totalRecipeCount)
    }

    @Test
    fun `nach rechts gewischt wird erst gefragt und dann als gekocht vermerkt`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onCookedRequest(aelpler)

        assertEquals(aelpler, viewModel.uiState.value.pendingCooked)
        assertNull(repository.current(1L)?.lastCookedAt)

        viewModel.events.test {
            viewModel.onCookedConfirm()
            assertEquals(RecipeListEvent.MarkedCooked("Älplermagronen"), awaitItem())
        }
        assertNotNull(repository.current(1L)?.lastCookedAt)
        // Steht das Rezept im Wochenplan, ist die Mahlzeit dort ebenfalls abgehakt.
        assertTrue(planRepository.currentPlan().single().isCooked)
    }

    @Test
    fun `ein versehentlicher Vermerk laesst sich wieder entfernen`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onCookedRequest(roesti)
        viewModel.events.test {
            viewModel.onCookedClear()
            assertEquals(RecipeListEvent.CookedCleared("Rösti"), awaitItem())
        }

        assertNull(repository.current(2L)?.lastCookedAt)
    }

    @Test
    fun `es ist immer nur eine Rueckfrage offen`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onRemoveRequest(roesti)
        viewModel.onCookedRequest(aelpler)

        assertNull(viewModel.uiState.value.pendingRemoval)
        assertEquals(aelpler, viewModel.uiState.value.pendingCooked)
    }

    @Test
    fun `uebernimmt Suchbegriff und Filter in den Filterzustand`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onQueryChange("Rösti")
        viewModel.onToggleTag("schnell")
        viewModel.onMaxPrepTimeChange(30)

        val filter = viewModel.uiState.value.filter
        assertEquals("Rösti", filter.query)
        assertEquals(setOf("schnell"), filter.tags)
        assertEquals(30, filter.maxPrepTimeMinutes)
        assertTrue(viewModel.uiState.value.isFilterActive)
    }

    @Test
    fun `schaltet einen Tag beim zweiten Tippen wieder aus`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onToggleTag("vegetarisch")
        viewModel.onToggleTag("vegetarisch")

        assertTrue(
            viewModel.uiState.value.selectedTags
                .isEmpty(),
        )
    }

    @Test
    fun `setzt alle Filter gemeinsam zurueck`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onQueryChange("Rösti")
        viewModel.onToggleTag("schnell")
        viewModel.onMaxPrepTimeChange(30)
        viewModel.onCookedFilterChange(true)
        viewModel.onResetFilter()

        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertTrue(state.selectedTags.isEmpty())
        assertEquals(null, state.maxPrepTimeMinutes)
        assertNull(state.cooked)
        assertFalse(state.isFilterActive)
    }

    @Test
    fun `meldet einen leeren Bestand solange keine Rezepte da sind`() = runTest {
        val emptyRepository = FakeRecipeRepository()
        val viewModel = RecipeListViewModel(
            observePagedRecipes = ObservePagedRecipesUseCase(emptyRepository),
            observeRecipeCount = ObserveRecipeCountUseCase(emptyRepository),
            observeTags = ObserveTagsUseCase(emptyRepository),
            profileRepository = FakeUserProfileRepository(),
            deleteRecipe = DeleteRecipeUseCase(emptyRepository, FakePlanRepository()),
            markRecipeCooked = MarkRecipeCookedUseCase(emptyRepository, FakePlanRepository(), FakePlanMemory()),
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasAnyRecipes)
    }
}
