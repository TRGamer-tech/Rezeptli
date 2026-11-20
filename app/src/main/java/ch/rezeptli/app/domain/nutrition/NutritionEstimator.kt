package ch.rezeptli.app.domain.nutrition

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientCategory
import ch.rezeptli.app.domain.model.IngredientNameNormalizer
import ch.rezeptli.app.domain.model.IngredientUnit

/**
 * Schaetzt Kalorien und Eiweiss eines Rezepts aus seiner Zutatenliste.
 *
 * Jede Zutat wird in der [FoodTable] nachgeschlagen und in Gramm umgerechnet - auch
 * "2 Eier", "1 EL Öl" oder "1 Dose Kichererbsen". Was die Tabelle nicht kennt, bekommt
 * den mittleren Wert seiner Warengruppe. Bleibt danach zu viel unerkannt, gibt es gar
 * keine Zahl: Eine Schaetzung, der das Fleisch fehlt, ist schlechter als keine.
 *
 * Das Ergebnis gilt fuer das ganze Rezept, so wie es dasteht. Durch die Portionen teilt,
 * wer es anzeigt - siehe [RecipeNutrition].
 */
object NutritionEstimator {
    fun estimate(ingredients: List<Ingredient>): Nutrients? {
        var total = Nutrients.NONE
        var counted = 0
        var unknown = 0
        ingredients.forEach { ingredient ->
            when (val part = partOf(ingredient)) {
                is Part.Counted -> {
                    total += part.nutrients
                    counted += 1
                }
                Part.Unknown -> unknown += 1
                Part.Ignored -> Unit
            }
        }

        if (counted == 0 || total.kcal < MIN_KCAL) return null
        // Mehr als ein Viertel nicht erkannt: Die Zahl waere bloss falsch genau.
        if (unknown * MAX_UNKNOWN_SHARE > counted + unknown) return null
        return total
    }

    private sealed interface Part {
        data class Counted(val nutrients: Nutrients) : Part

        /** Hat eine Menge, liess sich aber nicht einordnen oder nicht in Gramm umrechnen. */
        data object Unknown : Part

        /** Faellt nicht ins Gewicht: Gewuerze, Wasser, "etwas Petersilie". */
        data object Ignored : Part
    }

    private fun partOf(ingredient: Ingredient): Part {
        val words = wordsOf(ingredient.name)
        if (words.isEmpty()) return Part.Ignored

        val amount = ingredient.amount?.takeIf { it > 0.0 }
        val food = FoodTable.find(words)
            ?: ingredient.canonicalName?.let { FoodTable.find(wordsOf(it)) }
            ?: FoodTable.typicalOf(IngredientCategory.forIngredient(ingredient.name))
        if (food == null) {
            // In Prisen und Teeloeffeln faellt auch Unbekanntes nicht ins Gewicht.
            return if (amount == null || ingredient.unit in TINY_UNITS) Part.Ignored else Part.Unknown
        }
        if (food.trace) return Part.Ignored

        val text = IngredientNameNormalizer.normalize(ingredient.name + " " + ingredient.note.orEmpty())
        var grams = when (amount) {
            null -> if (food.fat) UNMEASURED_FAT else return Part.Ignored
            else -> gramsOf(amount, ingredient.unit, food, words.first()) ?: return Part.Unknown
        }
        // Vom Frittieroel landet nur ein Bruchteil im Essen - der Rest bleibt in der Pfanne.
        if (food.fat && FRYING.containsMatchIn(text)) grams *= FRYING_SHARE

        val cooked = food.cooked?.takeIf {
            when {
                DRIED.containsMatchIn(text) -> false
                ingredient.unit == IngredientUnit.DOSE || COOKED.containsMatchIn(text) -> true
                else -> food.cookedByDefault
            }
        }
        val per100 = cooked ?: Nutrients(food.kcal, food.protein)
        return Part.Counted(per100 * (grams / HUNDRED))
    }

    private fun wordsOf(name: String): List<String> =
        IngredientNameNormalizer.normalize(name).split(' ').filter { it.isNotEmpty() }

    private fun gramsOf(amount: Double, unit: IngredientUnit, food: Food, firstWord: String): Double? {
        val spoon = food.spoon ?: (SPOON_ML * food.density)
        return when (unit) {
            IngredientUnit.GRAMM -> amount
            IngredientUnit.KILOGRAMM -> amount * KILO
            IngredientUnit.MILLILITER -> amount * food.density
            IngredientUnit.DEZILITER -> amount * DECILITRE * food.density
            IngredientUnit.LITER -> amount * KILO * food.density
            IngredientUnit.ESSLOEFFEL -> amount * spoon
            IngredientUnit.TEELOEFFEL, IngredientUnit.KAFFEELOEFFEL -> amount * spoon / TEASPOONS_PER_SPOON
            IngredientUnit.MESSERSPITZE, IngredientUnit.PRISE -> amount * PINCH
            IngredientUnit.BUND -> amount * (food.bunch ?: DEFAULT_BUNCH)
            IngredientUnit.DOSE -> amount * (food.can ?: DEFAULT_CAN)
            IngredientUnit.PACKUNG -> (food.pack ?: food.piece)?.let { amount * it }
            IngredientUnit.ZEHE -> amount * CLOVE
            IngredientUnit.SCHEIBE -> amount * (food.slice ?: DEFAULT_SLICE)
            IngredientUnit.TASSE, IngredientUnit.CUP -> amount * CUP_ML * food.density
            IngredientUnit.OUNCE -> amount * OUNCE
            IngredientUnit.FLUID_OUNCE -> amount * FLUID_OUNCE_ML * food.density
            IngredientUnit.POUND -> amount * POUND
            IngredientUnit.NONE, IngredientUnit.STUECK ->
                (householdMeasure(firstWord, food) ?: food.piece)?.let { amount * it }
        }
    }

    /**
     * Masse, die der Zutatenparser nicht als Einheit kennt und deshalb im Namen stehen laesst:
     * "4 cl Rum", "1 Handvoll Rucola", "1 Becher Rahm". In Gramm je Stueck.
     */
    private fun householdMeasure(word: String, food: Food): Double? = when (word) {
        "cl" -> CENTILITRE * food.density
        "schuss" -> SPOON_ML * food.density
        "spritzer", "tropfen", "dash" -> DASH
        "handvoll", "hand" -> HANDFUL
        "becher", "glas", "glaeser" -> TUB * food.density
        "tafel", "tafeln" -> BAR
        "tranche", "tranchen" -> food.slice ?: DEFAULT_SLICE
        "paket", "pakete", "pack", "beutel", "paeckli", "rolle" -> food.pack ?: food.piece
        "blatt", "blaetter", "zweig", "zweige", "stiel", "stiele", "staengel" -> LEAF
        else -> null
    }

    private val TINY_UNITS = setOf(
        IngredientUnit.PRISE,
        IngredientUnit.MESSERSPITZE,
        IngredientUnit.TEELOEFFEL,
        IngredientUnit.KAFFEELOEFFEL,
    )

    private val DRIED = Regex("""\b(getrocknet|trocken|ungekocht)""")
    private val COOKED = Regex("""\b(gekocht|vorgekocht|gegart|abgetropft|dose|konserve|vortag)""")
    private val FRYING = Regex("frittier|ausback|fritteuse")

    /** Darunter hat die Liste nichts Essbares erkannt - etwa nur Wasser und Gewuerze. */
    private const val MIN_KCAL = 1.0
    private const val MAX_UNKNOWN_SHARE = 4

    private const val HUNDRED = 100.0
    private const val KILO = 1000.0
    private const val DECILITRE = 100.0
    private const val CENTILITRE = 10.0
    private const val SPOON_ML = 15.0
    private const val TEASPOONS_PER_SPOON = 3.0
    private const val CUP_ML = 240.0
    private const val OUNCE = 28.35
    private const val FLUID_OUNCE_ML = 29.57
    private const val POUND = 453.6
    private const val PINCH = 0.3
    private const val CLOVE = 4.0
    private const val DASH = 2.0
    private const val HANDFUL = 30.0
    private const val TUB = 200.0
    private const val BAR = 100.0
    private const val LEAF = 2.0
    private const val DEFAULT_BUNCH = 30.0
    private const val DEFAULT_CAN = 400.0
    private const val DEFAULT_SLICE = 25.0

    /** "Öl zum Braten" ohne Menge: ein knapper Essloeffel. */
    private const val UNMEASURED_FAT = 10.0
    private const val FRYING_SHARE = 0.1
}
