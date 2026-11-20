package ch.rezeptli.app.domain.nutrition

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.Recipe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Was die App an Kalorien und Eiweiss zeigt: zuerst die Angabe der Quelle, sonst eine Schaetzung.
 *
 * So war es gewuenscht - "wenn vorhanden, von der Quelle holen, erst dann schaetzen".
 */
class RecipeNutritionTest {
    /** Fuer vier: 400 g Spaghetti und 200 g Speck - zusammen 2080 kcal und 80 g Eiweiss. */
    private val zutaten = listOf(
        Ingredient(name = "Spaghetti", amount = 400.0, unit = IngredientUnit.GRAMM),
        Ingredient(name = "Speck", amount = 200.0, unit = IngredientUnit.GRAMM),
    )

    private fun rezept(portionen: Int? = 4, kcal: Int? = null, eiweiss: Double? = null) = Recipe(
        title = "Spaghetti mit Speck",
        ingredients = zutaten,
        servings = portionen,
        kcalPerServing = kcal,
        proteinPerServing = eiweiss,
    )

    @Test
    fun `nennt die Quelle beide Werte, gelten sie - geschaetzt wird nichts`() {
        val werte = RecipeNutrition.of(rezept(kcal = 612, eiweiss = 23.4))!!

        assertEquals(NutritionValue(612, estimated = false), werte.kcal)
        assertEquals(NutritionValue(23, estimated = false), werte.protein)
        assertEquals(NutritionBasis.SERVING, werte.basis)
        assertTrue(werte.isFromSource)
        assertFalse(werte.isEstimate)
    }

    @Test
    fun `ohne Angabe der Quelle wird aus den Zutaten geschaetzt - pro Portion und gerundet`() {
        val werte = RecipeNutrition.of(rezept())!!

        // 2080 kcal durch vier sind 520; 80 g durch vier sind 20.
        assertEquals(NutritionValue(520, estimated = true), werte.kcal)
        assertEquals(NutritionValue(20, estimated = true), werte.protein)
        assertEquals(NutritionBasis.SERVING, werte.basis)
        assertTrue(werte.isEstimate)
    }

    @Test
    fun `eine Schaetzung tut nicht genauer, als sie ist`() {
        // Durch drei: 693.3 kcal und 26.7 g - gezeigt werden 690 und 27.
        val werte = RecipeNutrition.of(rezept(portionen = 3))!!

        assertEquals(690, werte.kcal?.amount)
        assertEquals(27, werte.protein?.amount)
    }

    @Test
    fun `sagt das Rezept nicht, fuer wie viele es reicht, gilt die Schaetzung fuers Ganze`() {
        val werte = RecipeNutrition.of(rezept(portionen = null))!!

        assertEquals(NutritionBasis.RECIPE, werte.basis)
        assertEquals(2080, werte.kcal?.amount)
        assertEquals(80, werte.protein?.amount)
    }

    @Test
    fun `nennt die Quelle nur die Kalorien, wird das Eiweiss dazu geschaetzt`() {
        val werte = RecipeNutrition.of(rezept(kcal = 612))!!

        assertEquals(NutritionValue(612, estimated = false), werte.kcal)
        assertEquals(NutritionValue(20, estimated = true), werte.protein)
        assertFalse(werte.isFromSource)
        assertFalse(werte.isEstimate)
    }

    @Test
    fun `ohne Portionszahl laesst sich der fehlende Wert der Quelle nicht ergaenzen`() {
        // Die Quelle rechnet pro Portion - eine Schaetzung fuers ganze Rezept daneben waere irrefuehrend.
        val werte = RecipeNutrition.of(rezept(portionen = null, eiweiss = 23.0))!!

        assertNull(werte.kcal)
        assertEquals(NutritionValue(23, estimated = false), werte.protein)
        assertEquals(NutritionBasis.SERVING, werte.basis)
    }

    @Test
    fun `ohne Angabe und ohne erkennbare Zutaten gibt es nichts zu zeigen`() {
        assertNull(RecipeNutrition.of(Recipe(title = "Geheimrezept", servings = 4)))
        assertNull(RecipeNutrition.of(emptyList(), servings = null, source = SourceNutrition()))
    }

    @Test
    fun `die Angabe der Quelle gilt auch dann, wenn die Zutaten nichts hergeben`() {
        val werte = RecipeNutrition.of(
            emptyList(),
            servings = null,
            source = SourceNutrition(kcal = 480, proteinGrams = 31.0),
        )!!

        assertEquals(480, werte.kcal?.amount)
        assertEquals(31, werte.protein?.amount)
        assertTrue(werte.isFromSource)
    }
}
