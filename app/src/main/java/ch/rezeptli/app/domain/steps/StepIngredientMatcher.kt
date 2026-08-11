package ch.rezeptli.app.domain.steps

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientNameNormalizer
import javax.inject.Inject

/**
 * Findet die Zutaten, die in einem Schritt vorkommen.
 *
 * Damit zeigt der Kochmodus bei "Zwiebeln andaempfen" die Menge Zwiebeln an, statt bei
 * jedem Schritt die ganze Zutatenliste zu wiederholen.
 *
 * Erkannt wird ueber den normalisierten Namen, also ueber Umlaute und Schweizer
 * Synonyme hinweg: Steht im Rezept "Rüebli" und im Schritt "Karotten", passt das
 * trotzdem zusammen. Auch die Mehrzahl wird gefunden, weil ein Wortanfang genuegt.
 *
 * Was nicht vorkommt, wird auch nicht behauptet: Findet sich in einem Schritt keine
 * Zutat, bleibt die Liste leer. Lieber nichts anzeigen als das Falsche.
 */
class StepIngredientMatcher @Inject constructor() {
    fun ingredientsFor(step: RecipeStep, ingredients: List<Ingredient>): List<Ingredient> {
        val words = wordsIn(step.text)
        if (words.isEmpty()) return emptyList()

        return ingredients.filter { ingredient -> matches(ingredient, words) }
    }

    /** Zu jedem Schritt die passenden Zutaten - in einem Durchgang fuer das ganze Rezept. */
    fun byStep(steps: List<RecipeStep>, ingredients: List<Ingredient>): Map<Int, List<Ingredient>> =
        steps.associate { step -> step.position to ingredientsFor(step, ingredients) }

    private fun matches(ingredient: Ingredient, stepWords: List<String>): Boolean {
        val names = listOfNotNull(ingredient.name, ingredient.canonicalName)
        return names.any { name ->
            val needles = wordsIn(name).filter { it.length >= MIN_LENGTH }
            // Mehrteilige Namen ("gehackte Tomaten") passen, wenn jeder Teil vorkommt.
            needles.isNotEmpty() &&
                needles.all { needle ->
                    stepWords.any { word -> word.startsWith(needle) || needle.startsWith(word) }
                }
        }
    }

    private fun wordsIn(text: String): List<String> =
        IngredientNameNormalizer
            .normalize(text)
            .split(' ')
            .filter { it.isNotBlank() }

    private companion object {
        /**
         * Kuerzere Woerter treffen zu leicht daneben: "Ei" steckt sonst in "eingiessen",
         * und der Kochmodus wuerde Eier zu einem Schritt zeigen, der keine braucht.
         */
        const val MIN_LENGTH = 4
    }
}
