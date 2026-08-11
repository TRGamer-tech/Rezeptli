package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.steps.InstructionSplitter
import ch.rezeptli.app.fake.FakeRecipeRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SaveRecipeUseCaseTest {
    private val repository = FakeRecipeRepository()
    private val saveRecipe = SaveRecipeUseCase(repository, InstructionSplitter())

    @Test
    fun `speichert ein gueltiges Rezept und liefert dessen ID`() = runTest {
        val result = saveRecipe(Recipe(title = "Älplermagronen", instructions = "Kochen."))

        assertTrue(result is SaveRecipeResult.Saved, "Ergebnis war: $result")
        assertEquals(1, repository.savedRecipes.size)
    }

    @Test
    fun `lehnt ein Rezept ohne Titel ab`() = runTest {
        val result = saveRecipe(Recipe(title = "   "))

        assertEquals(
            SaveRecipeResult.Invalid(setOf(RecipeValidationError.TITLE_BLANK)),
            result,
        )
        assertTrue(repository.savedRecipes.isEmpty(), "Ungueltige Rezepte duerfen nicht gespeichert werden")
    }

    @Test
    fun `lehnt eine unrealistische Zubereitungszeit ab`() = runTest {
        val tooLong = saveRecipe(Recipe(title = "Sauerteig", prepTimeMinutes = 5000))
        val negative = saveRecipe(Recipe(title = "Sauerteig", prepTimeMinutes = -5))

        assertEquals(SaveRecipeResult.Invalid(setOf(RecipeValidationError.PREP_TIME_INVALID)), tooLong)
        assertEquals(SaveRecipeResult.Invalid(setOf(RecipeValidationError.PREP_TIME_INVALID)), negative)
    }

    @Test
    fun `entfernt leere Zutaten und nummeriert die uebrigen neu`() = runTest {
        saveRecipe(
            Recipe(
                title = "Suppe",
                ingredients = listOf(
                    Ingredient(name = "Bouillon", amount = 1.0, unit = IngredientUnit.LITER, position = 0),
                    Ingredient(name = "   ", position = 1),
                    Ingredient(name = " Rüebli ", amount = 2.0, position = 2),
                ),
            ),
        )

        val saved = repository.savedRecipes.single()
        assertEquals(listOf("Bouillon", "Rüebli"), saved.ingredients.map { it.name })
        assertEquals(listOf(0, 1), saved.ingredients.map { it.position })
    }

    @Test
    fun `entfernt doppelte Tags unabhaengig von der Schreibweise`() = runTest {
        saveRecipe(Recipe(title = "Salat", tags = listOf("vegan", "Vegan", " schnell ", "")))

        assertEquals(listOf("vegan", "schnell"), repository.savedRecipes.single().tags)
    }
}
