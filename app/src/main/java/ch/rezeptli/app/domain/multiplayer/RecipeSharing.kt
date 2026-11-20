package ch.rezeptli.app.domain.multiplayer

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.steps.RecipeStep

/**
 * Uebersetzt ein Rezept in das, was an die andere Person geht, und zurueck.
 *
 * Mit geht, was man zum Kochen braucht: Zutaten, Zubereitung, Portionen, Zeit, Schlagworte.
 * Nicht mit gehen das Foto (eine Datei auf dem Telefon), wann zuletzt gekocht wurde und die
 * Nummer des Rezepts - die bedeuten auf dem anderen Geraet nichts.
 */
object RecipeSharing {
    fun contentOf(recipe: Recipe): SharedContent = SharedContent(
        instructions = recipe.instructions,
        steps = recipe.steps.sortedBy { it.position }.map { it.text },
        ingredients = recipe.ingredients.sortedBy { it.position }.map { ingredient ->
            SharedIngredient(
                name = ingredient.name,
                amount = ingredient.amount,
                unit = ingredient.unit.takeIf { it != IngredientUnit.NONE }?.name,
                note = ingredient.note,
            )
        },
        servings = recipe.servings,
        prepTimeMinutes = recipe.prepTimeMinutes,
        tags = recipe.tags,
    )

    /**
     * Baut aus dem uebergebenen Inhalt ein Rezept fuer die eigene Sammlung.
     *
     * Der Titel kommt von der Karte - ihn kennen beide schon. Fehlt die Zubereitung als Text,
     * wird sie aus den Schritten zusammengesetzt, damit das Rezept auch beim Bearbeiten
     * vollstaendig dasteht.
     */
    fun recipeFrom(card: SharedRecipe, content: SharedContent): Recipe {
        val steps = content.steps.filter { it.isNotBlank() }
        return Recipe(
            title = card.title,
            instructions = content.instructions.ifBlank { steps.joinToString("\n\n") },
            steps = steps.mapIndexed { index, text -> RecipeStep(position = index, text = text) },
            ingredients = content.ingredients
                .filter { it.name.isNotBlank() }
                .mapIndexed { index, ingredient ->
                    Ingredient(
                        name = ingredient.name,
                        amount = ingredient.amount?.takeIf { it > 0 },
                        unit = IngredientUnit.entries.firstOrNull { it.name == ingredient.unit } ?: IngredientUnit.NONE,
                        note = ingredient.note,
                        position = index,
                    )
                },
            tags = content.tags,
            prepTimeMinutes = content.prepTimeMinutes ?: card.prepTimeMinutes,
            servings = content.servings,
        )
    }
}
