package ch.rezeptli.app.presentation.swipe

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.nutrition.NutritionFacts

/**
 * Was ein Antippen der Wischkarte zeigt: woher das Rezept kommt und was hineingehoert.
 *
 * Bewusst ohne Zubereitung - beim Wischen geht es um die Frage "will ich das?", und die
 * beantworten Quelle und Zutaten. Gekocht wird aus dem gespeicherten Rezept.
 */
data class CardInfo(
    val title: String,
    val sourceName: String? = null,
    val sourceUrl: String? = null,
    val minutes: Int? = null,
    val servings: String? = null,
    /** Zutatenzeilen, wie die Quelle sie schreibt. */
    val lines: List<String> = emptyList(),
    /** Zutaten eines Rezepts aus der eigenen Sammlung. */
    val ingredients: List<Ingredient> = emptyList(),
    val isLoading: Boolean = false,
    /** Die Seite liess sich gerade nicht lesen. */
    val failed: Boolean = false,
    /** Die Quelle ist nicht auf Deutsch - die Zutaten stehen in ihrer Sprache. */
    val isForeign: Boolean = false,
    /** Ein eigenes Rezept der anderen Person: Mehr als Titel und Bild hat sie nicht geteilt. */
    val isPartnersOwn: Boolean = false,
    /** Kalorien und Eiweiss: was die Quelle nennt, sonst aus den Zutaten geschaetzt. */
    val nutrition: NutritionFacts? = null,
) {
    val hasIngredients: Boolean get() = lines.isNotEmpty() || ingredients.isNotEmpty()

    /** Unter den Zutaten stehen die Naehrwerte - oder der Satz, dass sie sich nicht schaetzen liessen. */
    val showsNutrition: Boolean get() = !isLoading && !failed && (hasIngredients || nutrition != null)
}
