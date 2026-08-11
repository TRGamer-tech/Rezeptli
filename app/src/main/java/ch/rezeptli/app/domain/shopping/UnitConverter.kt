package ch.rezeptli.app.domain.shopping

import ch.rezeptli.app.domain.model.IngredientUnit
import kotlin.math.abs
import kotlin.math.round

/**
 * Groessenart einer Einheit. Nur Mengen derselben Art werden zusammengezaehlt:
 * 200 g und 1 kg ergeben 1.2 kg, aber 2 EL Öl und 1 dl Öl bleiben getrennt stehen,
 * weil ein Esslöffel je nach Zutat unterschiedlich viel fasst.
 */
enum class UnitDimension {
    MASS,
    VOLUME,
    SPOON,
    COUNT,

    /** Einheiten, die nur mit sich selbst verrechnet werden (Bund, Dose, Prise …). */
    DISCRETE,
}

/**
 * Rechnet Mengenangaben innerhalb einer Groessenart um.
 *
 * Bewusst konservativ: Zwischen Groessenarten wird nie umgerechnet. Eine Faustformel
 * wie "1 EL = 15 ml" stimmt fuer Öl, aber nicht fuer Mehl - und eine Einkaufsliste,
 * die still falsche Mengen ausweist, ist schlimmer als eine mit zwei Zeilen.
 */
object UnitConverter {

    val IngredientUnit.dimension: UnitDimension
        get() = when (this) {
            IngredientUnit.GRAMM, IngredientUnit.KILOGRAMM -> UnitDimension.MASS
            IngredientUnit.MILLILITER, IngredientUnit.DEZILITER, IngredientUnit.LITER ->
                UnitDimension.VOLUME
            IngredientUnit.TEELOEFFEL, IngredientUnit.KAFFEELOEFFEL, IngredientUnit.ESSLOEFFEL ->
                UnitDimension.SPOON
            IngredientUnit.NONE, IngredientUnit.STUECK -> UnitDimension.COUNT
            else -> UnitDimension.DISCRETE
        }

    /** Faktor auf die Basiseinheit der jeweiligen Groessenart (g, ml, TL, Stück). */
    private val IngredientUnit.baseFactor: Double
        get() = when (this) {
            IngredientUnit.GRAMM -> 1.0
            IngredientUnit.KILOGRAMM -> GRAMS_PER_KILOGRAM
            IngredientUnit.MILLILITER -> 1.0
            IngredientUnit.DEZILITER -> MILLILITRES_PER_DECILITRE
            IngredientUnit.LITER -> MILLILITRES_PER_LITRE
            IngredientUnit.TEELOEFFEL, IngredientUnit.KAFFEELOEFFEL -> 1.0
            IngredientUnit.ESSLOEFFEL -> TEASPOONS_PER_TABLESPOON
            else -> 1.0
        }

    /** Rechnet [amount] in die Basiseinheit der Groessenart von [unit] um. */
    fun toBase(amount: Double, unit: IngredientUnit): Double = amount * unit.baseFactor

    /**
     * Waehlt zu einer Menge in der Basiseinheit die Einheit, in der man sie auch
     * aufschreiben wuerde: 1500 g werden zu 1.5 kg, 250 ml zu 2.5 dl.
     */
    fun fromBase(baseAmount: Double, dimension: UnitDimension, fallback: IngredientUnit): Pair<Double, IngredientUnit> =
        when (dimension) {
            UnitDimension.MASS ->
                if (baseAmount >= GRAMS_PER_KILOGRAM) {
                    round2(baseAmount / GRAMS_PER_KILOGRAM) to IngredientUnit.KILOGRAMM
                } else {
                    round2(baseAmount) to IngredientUnit.GRAMM
                }

            UnitDimension.VOLUME -> when {
                baseAmount >= MILLILITRES_PER_LITRE ->
                    round2(baseAmount / MILLILITRES_PER_LITRE) to IngredientUnit.LITER
                baseAmount >= MILLILITRES_PER_DECILITRE ->
                    round2(baseAmount / MILLILITRES_PER_DECILITRE) to IngredientUnit.DEZILITER
                else -> round2(baseAmount) to IngredientUnit.MILLILITER
            }

            // Nur ganze Esslöffel: aus 4 TL werden keine 1.33 EL.
            UnitDimension.SPOON ->
                if (baseAmount >= TEASPOONS_PER_TABLESPOON && isWholeMultiple(baseAmount)) {
                    round2(baseAmount / TEASPOONS_PER_TABLESPOON) to IngredientUnit.ESSLOEFFEL
                } else {
                    round2(baseAmount) to IngredientUnit.TEELOEFFEL
                }

            UnitDimension.COUNT -> round2(baseAmount) to fallback
            UnitDimension.DISCRETE -> round2(baseAmount) to fallback
        }

    private fun isWholeMultiple(teaspoons: Double): Boolean {
        val tablespoons = teaspoons / TEASPOONS_PER_TABLESPOON
        return abs(tablespoons - round(tablespoons)) < TOLERANCE
    }

    private fun round2(value: Double): Double = round(value * 100.0) / 100.0

    private const val GRAMS_PER_KILOGRAM = 1000.0
    private const val MILLILITRES_PER_DECILITRE = 100.0
    private const val MILLILITRES_PER_LITRE = 1000.0
    private const val TEASPOONS_PER_TABLESPOON = 3.0
    private const val TOLERANCE = 0.01
}
