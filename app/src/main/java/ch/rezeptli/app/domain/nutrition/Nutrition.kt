package ch.rezeptli.app.domain.nutrition

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.Recipe
import kotlin.math.roundToInt

/**
 * Was eine Rezeptseite ueber die Naehrwerte einer Portion sagt.
 *
 * Was sie nicht nennt, bleibt `null` - diesen Wert schaetzt die App dann aus den Zutaten.
 */
data class SourceNutrition(
    val kcal: Int? = null,
    val proteinGrams: Double? = null,
) {
    val isEmpty: Boolean get() = kcal == null && proteinGrams == null
}

/** Worauf sich die gezeigten Naehrwerte beziehen. */
enum class NutritionBasis {
    /** Eine Portion - das, was auf einem Teller liegt. */
    SERVING,

    /** Alles, was das Rezept ergibt. So wird gerechnet, wenn es nicht sagt, fuer wie viele es reicht. */
    RECIPE,
}

/** Ein Naehrwert und woher er stammt: von der Quelle genannt oder aus den Zutaten geschaetzt. */
data class NutritionValue(
    val amount: Int,
    val estimated: Boolean,
)

/**
 * Kalorien (kcal) und Eiweiss (g) eines Rezepts, so wie die App sie zeigt.
 *
 * Mindestens einer der beiden Werte ist da. Fehlt einer, hat die Quelle nur den anderen
 * genannt, und fuer eine Schaetzung reichten die Angaben nicht.
 */
data class NutritionFacts(
    val kcal: NutritionValue?,
    val protein: NutritionValue?,
    val basis: NutritionBasis,
) {
    private val values: List<NutritionValue> get() = listOfNotNull(kcal, protein)

    /** Alles, was hier steht, ist geschaetzt. */
    val isEstimate: Boolean get() = values.all { it.estimated }

    /** Alles, was hier steht, hat die Quelle genannt. */
    val isFromSource: Boolean get() = values.none { it.estimated }
}

/** Kalorien und Eiweiss einer Menge Essen - ungerundet, zum Weiterrechnen. */
data class Nutrients(
    val kcal: Double,
    val proteinGrams: Double,
) {
    operator fun plus(other: Nutrients) = Nutrients(kcal + other.kcal, proteinGrams + other.proteinGrams)

    operator fun times(factor: Double) = Nutrients(kcal * factor, proteinGrams * factor)

    companion object {
        val NONE = Nutrients(0.0, 0.0)
    }
}

/**
 * Bestimmt die Naehrwerte eines Rezepts: zuerst, was die Quelle sagt - sonst geschaetzt.
 *
 * Die Angaben einer Rezeptseite sind verlaesslicher als jede Rechnung aus der Zutatenliste:
 * Dort hat jemand gewogen und nachgeschlagen. Geschaetzt wird deshalb nur, was die Quelle
 * offen laesst, und es steht immer dabei, dass es eine Schaetzung ist.
 */
object RecipeNutrition {
    fun of(recipe: Recipe): NutritionFacts? = of(
        ingredients = recipe.ingredients,
        servings = recipe.servings,
        source = SourceNutrition(recipe.kcalPerServing, recipe.proteinPerServing),
    )

    fun of(ingredients: List<Ingredient>, servings: Int?, source: SourceNutrition?): NutritionFacts? {
        val stated = source?.takeUnless { it.isEmpty }
        val portions = servings?.takeIf { it > 0 }
        val whole = NutritionEstimator.estimate(ingredients)

        if (stated != null) {
            // Die Quelle rechnet pro Portion. Ergaenzen laesst sich ihr fehlender Wert nur,
            // wenn bekannt ist, fuer wie viele das Rezept reicht.
            val perServing = whole?.takeIf { portions != null }?.let { it * (1.0 / portions!!) }
            return NutritionFacts(
                kcal = stated.kcal?.let { NutritionValue(it, estimated = false) }
                    ?: perServing?.let { estimatedKcal(it.kcal) },
                protein = stated.proteinGrams?.let { NutritionValue(it.roundToInt(), estimated = false) }
                    ?: perServing?.let { estimatedProtein(it.proteinGrams) },
                basis = NutritionBasis.SERVING,
            )
        }

        whole ?: return null
        val shown = if (portions != null) whole * (1.0 / portions) else whole
        return NutritionFacts(
            kcal = estimatedKcal(shown.kcal),
            protein = estimatedProtein(shown.proteinGrams),
            basis = if (portions != null) NutritionBasis.SERVING else NutritionBasis.RECIPE,
        )
    }

    /** Eine Schaetzung tut nicht so, als waere sie auf die Kalorie genau: gerundet auf Zehner. */
    private fun estimatedKcal(value: Double): NutritionValue {
        val step = if (value < SMALL_KCAL) FINE_STEP else COARSE_STEP
        return NutritionValue((value / step).roundToInt() * step, estimated = true)
    }

    private fun estimatedProtein(value: Double) = NutritionValue(value.roundToInt(), estimated = true)

    private const val SMALL_KCAL = 100.0
    private const val FINE_STEP = 5
    private const val COARSE_STEP = 10
}

/**
 * Liest die Naehrwertangaben einer Rezeptseite.
 *
 * Die Seiten schreiben alles Moegliche: "512 kcal", "512 calories", "2143 kJ", "2143 kJ / 512 kcal",
 * bloss "512", "23,5 g", "23 grams". Gelesen wird grosszuegig, geglaubt aber nur, was plausibel ist -
 * eine Null oder ein Wert pro 100 g waere schlimmer als gar keine Angabe, denn dann schaetzt die App.
 */
object SourceNutritionParser {
    private const val NUMBER = """(\d[\d'’.,]*)"""
    private val KCAL = Regex("""$NUMBER\s*(kcal|kilokalorien|kalorien|calories|cal)\b""", RegexOption.IGNORE_CASE)
    private val KILOJOULE = Regex("""$NUMBER\s*(kj|kilojoule)""", RegexOption.IGNORE_CASE)
    private val ANY_NUMBER = Regex(NUMBER)
    private val GRAMS = Regex("""$NUMBER\s*(mg|g)?""", RegexOption.IGNORE_CASE)
    private val PER_HUNDRED = Regex("""\b100\s*(g|gr|gramm|grams?|ml)\b""", RegexOption.IGNORE_CASE)
    private val THOUSANDS = Regex("""[1-9]\d{0,2}[.,]\d{3}""")

    fun parse(calories: String?, protein: String?, servingSize: String? = null): SourceNutrition? {
        // Bezieht sich die Angabe auf 100 g, sagt sie nichts ueber die Portion.
        if (servingSize != null && PER_HUNDRED.containsMatchIn(servingSize)) return null

        val kcal = kcalIn(calories)
        val grams = gramsIn(protein)
        // Kein Eiweiss gibt es wirklich (ein Drink) - aber ohne Kalorien dazu ist es eher ein leeres Feld.
        if (kcal == null && (grams == null || grams == 0.0)) return null
        return SourceNutrition(kcal = kcal, proteinGrams = grams)
    }

    private fun kcalIn(text: String?): Int? {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return null

        val kcal = KCAL.find(value)?.let { numberIn(it.groupValues[1]) }
            ?: KILOJOULE.find(value)?.let { numberIn(it.groupValues[1]) }?.div(KJ_PER_KCAL)
            ?: ANY_NUMBER.find(value)?.let { numberIn(it.groupValues[1]) }
        return kcal?.roundToInt()?.takeIf { it in 1..MAX_KCAL }
    }

    private fun gramsIn(text: String?): Double? {
        val value = text?.trim().orEmpty()
        if (value.isEmpty()) return null

        val match = GRAMS.find(value) ?: return null
        val number = numberIn(match.groupValues[1]) ?: return null
        val grams = if (match.groupValues[2].equals("mg", ignoreCase = true)) number / MG_PER_GRAM else number
        return grams.takeIf { it in 0.0..MAX_PROTEIN }
    }

    /** "1'234", "1.234", "512,5", "512.5" - Tausenderzeichen und Komma, wie sie eben vorkommen. */
    private fun numberIn(raw: String): Double? {
        var text = raw.replace("'", "").replace("’", "").trimEnd('.', ',')
        val comma = text.lastIndexOf(',')
        val dot = text.lastIndexOf('.')
        text = when {
            // Beide Zeichen: Das hintere trennt die Nachkommastellen.
            comma >= 0 && dot >= 0 -> if (comma > dot) {
                text.replace(".", "").replace(',', '.')
            } else {
                text.replace(",", "")
            }
            // Genau drei Ziffern nach dem einzigen Zeichen: Tausender ("1.234 kcal"), keine Nachkommastellen.
            THOUSANDS.matches(text) -> text.replace(".", "").replace(",", "")
            else -> text.replace(',', '.')
        }
        return text.toDoubleOrNull()
    }

    private const val KJ_PER_KCAL = 4.184
    private const val MG_PER_GRAM = 1000.0

    /** Mehr hat keine Portion - eher ist dann das ganze Rezept gemeint oder die Einheit falsch. */
    private const val MAX_KCAL = 4000
    private const val MAX_PROTEIN = 300.0
}
