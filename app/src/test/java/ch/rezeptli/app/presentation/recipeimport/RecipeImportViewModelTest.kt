package ch.rezeptli.app.presentation.recipeimport

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.parser.IngredientTextParser
import ch.rezeptli.app.domain.parser.RecipeTextParser
import ch.rezeptli.app.domain.steps.InstructionSplitter
import ch.rezeptli.app.domain.translate.NoTranslation
import ch.rezeptli.app.domain.usecase.LoadWebRecipeUseCase
import ch.rezeptli.app.domain.usecase.ParseRecipeTextUseCase
import ch.rezeptli.app.domain.usecase.SaveRecipeUseCase
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.fake.FakeWebRecipeRepository
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
class RecipeImportViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repository = FakeRecipeRepository()

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        RecipeImportViewModel(
            savedStateHandle = savedStateHandle,
            parseRecipeText = ParseRecipeTextUseCase(RecipeTextParser(IngredientTextParser())),
            loadWebRecipe = LoadWebRecipeUseCase(
                FakeWebRecipeRepository(),
                IngredientTextParser(),
                InstructionSplitter(),
                NoTranslation,
            ),
            saveRecipe = SaveRecipeUseCase(repository, InstructionSplitter()),
        )

    private val sampleText =
        """
        Rösti
        Zubereitungszeit: 30 Minuten

        Zutaten
        800 g Kartoffeln
        2 EL Butter
        Salz nach Belieben

        Zubereitung
        Kartoffeln raffeln und in der Butter goldbraun braten.
        """.trimIndent()

    @Test
    fun `zeigt den Vorschlag erst nach dem Auswerten und speichert nichts von selbst`() = runTest {
        val viewModel = createViewModel()

        viewModel.onTextChange(sampleText)
        assertNull(viewModel.uiState.value.form, "Vor dem Auswerten gibt es keinen Vorschlag")

        viewModel.onAnalyse()

        val form = viewModel.uiState.value.form
        assertNotNull(form)
        assertEquals("Rösti", form!!.title)
        assertEquals("30", form.prepTimeText)
        assertEquals(listOf("Kartoffeln", "Butter", "Salz"), form.ingredients.map { it.name })
        assertEquals(IngredientUnit.GRAMM, form.ingredients.first().unit)
        assertTrue(repository.savedRecipes.isEmpty(), "Der Parser darf nichts stillschweigend speichern")
    }

    @Test
    fun `markiert unsichere Zeilen zur Kontrolle`() = runTest {
        val viewModel = createViewModel()

        viewModel.onTextChange(sampleText)
        viewModel.onAnalyse()

        val salt = viewModel.uiState.value.form!!
            .ingredients
            .last()
        assertTrue(salt.needsReview, "\"Salz nach Belieben\" hat keine Menge und ist damit unsicher")
        assertTrue(viewModel.uiState.value.hasUncertainLines)
    }

    @Test
    fun `eine korrigierte Zeile gilt als geprueft`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTextChange(sampleText)
        viewModel.onAnalyse()

        val index = viewModel.uiState.value.form!!
            .ingredients
            .indexOfLast { it.needsReview }
        val draft = viewModel.uiState.value.form!!
            .ingredients[index]
        viewModel.onIngredientChange(index, draft.copy(amountText = "1", unit = IngredientUnit.PRISE))

        val corrected = viewModel.uiState.value.form!!
            .ingredients[index]
        assertFalse(corrected.needsReview)
        assertEquals(IngredientUnit.PRISE, corrected.unit)
        assertFalse(viewModel.uiState.value.hasUncertainLines)
    }

    @Test
    fun `speichert den korrigierten Vorschlag und meldet die neue ID`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTextChange(sampleText)
        viewModel.onAnalyse()

        viewModel.events.test {
            viewModel.onSave()
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is RecipeImportEvent.Saved)
            assertEquals(1, repository.savedRecipes.size)
            assertEquals("Rösti", repository.savedRecipes.single().title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `meldet wenn aus dem Text nichts zu holen war`() = runTest {
        val viewModel = createViewModel()

        viewModel.onTextChange("   \n  ")
        viewModel.onAnalyse()

        assertTrue(viewModel.uiState.value.nothingFound)
        assertNull(viewModel.uiState.value.form)
    }
}
