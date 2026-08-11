package ch.rezeptli.app.presentation.recipeedit

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import ch.rezeptli.app.data.local.PhotoStorage
import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.usecase.ObserveRecipeUseCase
import ch.rezeptli.app.domain.usecase.SaveRecipeUseCase
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.presentation.navigation.Destinations
import ch.rezeptli.app.util.MainDispatcherExtension
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeEditViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val existingRecipe = Recipe(
        id = 7L,
        title = "Rösti",
        instructions = "Raffeln und braten.",
        ingredients = listOf(
            Ingredient(id = 1L, recipeId = 7L, name = "Kartoffeln", amount = 800.0, unit = IngredientUnit.GRAMM),
        ),
        tags = listOf("vegetarisch"),
        prepTimeMinutes = 30,
    )

    private val repository = FakeRecipeRepository(listOf(existingRecipe))
    private val photoStorage: PhotoStorage = mockk(relaxed = true)

    private fun createViewModel(recipeId: Long) = RecipeEditViewModel(
        savedStateHandle = SavedStateHandle(mapOf(Destinations.ARG_RECIPE_ID to recipeId)),
        observeRecipe = ObserveRecipeUseCase(repository),
        saveRecipe = SaveRecipeUseCase(repository),
        photoStorage = photoStorage,
    )

    @Test
    fun `laedt ein bestehendes Rezept ins Formular`() = runTest {
        val viewModel = createViewModel(existingRecipe.id)
        advanceUntilIdle()

        val form = viewModel.uiState.value.form
        assertFalse(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.isNewRecipe)
        assertEquals("Rösti", form.title)
        assertEquals("30", form.prepTimeText)
        assertEquals(listOf("Kartoffeln"), form.ingredients.map { it.name })
        assertEquals("800", form.ingredients.single().amountText)
    }

    @Test
    fun `startet ein neues Rezept mit einer leeren Zutatenzeile`() = runTest {
        val viewModel = createViewModel(0L)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isNewRecipe)
        assertEquals(1, viewModel.uiState.value.form.ingredients.size)
        assertEquals(
            "",
            viewModel.uiState.value.form.ingredients
                .single()
                .name,
        )
    }

    @Test
    fun `meldet einen fehlenden Titel statt zu speichern`() = runTest {
        val viewModel = createViewModel(0L)
        advanceUntilIdle()

        viewModel.onSave()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.form.titleError)
        assertTrue(repository.savedRecipes.isEmpty())
    }

    @Test
    fun `speichert die Aenderungen und meldet die ID zurueck`() = runTest {
        val viewModel = createViewModel(existingRecipe.id)
        advanceUntilIdle()

        viewModel.onTitleChange("Rösti mit Speck")
        viewModel.onIngredientAdd()
        val newIndex = viewModel.uiState.value.form.ingredients.lastIndex
        viewModel.onIngredientChange(
            newIndex,
            viewModel.uiState.value.form.ingredients[newIndex]
                .copy(name = "Speck", amountText = "100"),
        )

        viewModel.events.test {
            viewModel.onSave()
            advanceUntilIdle()

            assertEquals(RecipeEditEvent.Saved(existingRecipe.id), awaitItem())
            val saved = repository.savedRecipes.single()
            assertEquals("Rösti mit Speck", saved.title)
            assertEquals(listOf("Kartoffeln", "Speck"), saved.ingredients.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fragt vor dem Verwerfen ungespeicherter Aenderungen nach`() = runTest {
        val viewModel = createViewModel(existingRecipe.id)
        advanceUntilIdle()

        viewModel.onTitleChange("Anderer Titel")
        viewModel.onBackRequest()

        assertTrue(viewModel.uiState.value.isDiscardDialogVisible)
    }

    @Test
    fun `geht ohne Rueckfrage zurueck wenn nichts geaendert wurde`() = runTest {
        val viewModel = createViewModel(existingRecipe.id)
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.onBackRequest()
            advanceUntilIdle()

            assertEquals(RecipeEditEvent.Discarded, awaitItem())
            assertFalse(viewModel.uiState.value.isDiscardDialogVisible)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
