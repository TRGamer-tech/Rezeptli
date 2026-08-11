package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.repository.RecipeRepository
import ch.rezeptli.app.domain.steps.InstructionSplitter
import javax.inject.Inject

/** Gruende, weshalb ein Rezept nicht gespeichert werden kann. */
enum class RecipeValidationError {
    /** Ohne Titel laesst sich ein Rezept weder finden noch swipen. */
    TITLE_BLANK,

    /** Zubereitungszeit muss positiv und realistisch sein. */
    PREP_TIME_INVALID,
}

sealed interface SaveRecipeResult {
    data class Saved(val recipeId: Long) : SaveRecipeResult

    data class Invalid(val errors: Set<RecipeValidationError>) : SaveRecipeResult
}

/**
 * Speichert ein Rezept nach einer Validierung und raeumt dabei leere Eingaben auf:
 * Zutaten ohne Namen, doppelte Tags und ueberfluessige Leerzeichen verschwinden,
 * bevor etwas in die Datenbank geschrieben wird.
 */
class SaveRecipeUseCase @Inject constructor(
    private val repository: RecipeRepository,
    private val splitter: InstructionSplitter,
) {
    suspend operator fun invoke(recipe: Recipe): SaveRecipeResult {
        val errors = buildSet {
            if (recipe.title.isBlank()) add(RecipeValidationError.TITLE_BLANK)
            val prepTime = recipe.prepTimeMinutes
            if (prepTime != null && (prepTime <= 0 || prepTime > MAX_PREP_TIME_MINUTES)) {
                add(RecipeValidationError.PREP_TIME_INVALID)
            }
        }
        if (errors.isNotEmpty()) return SaveRecipeResult.Invalid(errors)

        val cleaned = recipe.copy(
            title = recipe.title.trim(),
            instructions = recipe.instructions.trim(),
            ingredients = recipe.ingredients
                .filter { it.name.isNotBlank() }
                .mapIndexed { index, ingredient ->
                    ingredient.copy(name = ingredient.name.trim(), position = index)
                },
            tags = recipe.tags
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() },
            // Bringt das Rezept eigene Schritte mit - etwa aus einem Import, wo die
            // Quelle sie selbst gegliedert hat -, bleiben die erhalten. Sonst wird der
            // Text aufgeteilt, damit der Kochmodus immer etwas zu zeigen hat.
            steps = recipe.steps
                .filter { it.text.isNotBlank() }
                .mapIndexed { index, step -> step.copy(text = step.text.trim(), position = index) }
                .ifEmpty { splitter.split(recipe.instructions) },
        )

        return SaveRecipeResult.Saved(repository.saveRecipe(cleaned))
    }

    private companion object {
        /** 24 Stunden - alles darueber ist mit Sicherheit ein Tippfehler. */
        const val MAX_PREP_TIME_MINUTES = 24 * 60
    }
}
