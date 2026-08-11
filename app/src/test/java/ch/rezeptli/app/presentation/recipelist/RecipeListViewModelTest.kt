package ch.rezeptli.app.presentation.recipelist

import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.usecase.ObserveOpenShoppingCountUseCase
import ch.rezeptli.app.domain.usecase.ObservePagedRecipesUseCase
import ch.rezeptli.app.domain.usecase.ObserveRecipeCountUseCase
import ch.rezeptli.app.domain.usecase.ObserveTagsUseCase
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.fake.FakeShoppingListRepository
import ch.rezeptli.app.fake.FakeUserProfileRepository
import ch.rezeptli.app.util.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
            Recipe(id = 2L, title = "Rösti", tags = listOf("vegetarisch", "schnell"), prepTimeMinutes = 25),
        ),
    )

    private val shoppingRepository = FakeShoppingListRepository()

    private fun createViewModel() = RecipeListViewModel(
        observePagedRecipes = ObservePagedRecipesUseCase(repository),
        observeRecipeCount = ObserveRecipeCountUseCase(repository),
        observeTags = ObserveTagsUseCase(repository),
        observeOpenShoppingCount = ObserveOpenShoppingCountUseCase(shoppingRepository),
        profileRepository = FakeUserProfileRepository(),
    )

    @Test
    fun `kennt die Gesamtzahl der Rezepte und alle vergebenen Tags`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.totalRecipeCount)
        assertTrue(state.hasAnyRecipes)
        assertEquals(listOf("schnell", "vegetarisch"), state.availableTags)
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
        viewModel.onResetFilter()

        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertTrue(state.selectedTags.isEmpty())
        assertEquals(null, state.maxPrepTimeMinutes)
        assertFalse(state.isFilterActive)
    }

    @Test
    fun `meldet einen leeren Bestand solange keine Rezepte da sind`() = runTest {
        val emptyRepository = FakeRecipeRepository()
        val viewModel = RecipeListViewModel(
            observePagedRecipes = ObservePagedRecipesUseCase(emptyRepository),
            observeRecipeCount = ObserveRecipeCountUseCase(emptyRepository),
            observeTags = ObserveTagsUseCase(emptyRepository),
            observeOpenShoppingCount = ObserveOpenShoppingCountUseCase(FakeShoppingListRepository()),
            profileRepository = FakeUserProfileRepository(),
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasAnyRecipes)
    }
}
