package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit
import kotlin.math.round

/**
 * Rechnet Zutatenmengen auf eine andere Personenzahl um.
 *
 * Gerundet wird so, wie man einkauft und abmisst: Gramm auf 5, Stueck auf halbe, Loeffel
 * auf Viertel. "187.5 g" ist keine Angabe, die jemand abwiegt.
 */
object IngredientScaler {
    fun factor(target: Int, original: Int?): Double {
        val basis = original?.takeIf { it > 0 } ?: DEFAULT_SERVINGS
        return target.coerceAtLeast(1).toDouble() / basis
    }

    fun scale(ingredients: List<Ingredient>, factor: Double): List<Ingredient> =
        ingredients.map { ingredient ->
            val amount = ingredient.amount ?: return@map ingredient
            ingredient.copy(amount = nice(amount * factor, ingredient.unit))
        }

    fun nice(value: Double, unit: IngredientUnit): Double = when (unit) {
        IngredientUnit.GRAMM, IngredientUnit.MILLILITER ->
            if (value < SMALL) roundTo(value, 1.0) else roundTo(value, FIVE)
        IngredientUnit.KILOGRAMM, IngredientUnit.LITER, IngredientUnit.DEZILITER -> roundTo(value, 0.05)
        IngredientUnit.TEELOEFFEL, IngredientUnit.ESSLOEFFEL, IngredientUnit.KAFFEELOEFFEL,
        IngredientUnit.TASSE, IngredientUnit.MESSERSPITZE, IngredientUnit.PRISE,
        -> roundTo(value, 0.25)
        else -> roundTo(value, 0.5)
    }.coerceAtLeast(MIN_AMOUNT)

    private fun roundTo(value: Double, step: Double): Double = round(value / step) * step

    private const val DEFAULT_SERVINGS = 4
    private const val SMALL = 20.0
    private const val FIVE = 5.0
    private const val MIN_AMOUNT = 0.25
}
