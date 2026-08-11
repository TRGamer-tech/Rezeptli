package ch.rezeptli.app.domain.steps

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StepIngredientMatcherTest {
    private val matcher = StepIngredientMatcher()

    private val zwiebel = Ingredient(name = "Zwiebel", amount = 2.0, unit = IngredientUnit.STUECK)
    private val ruebli = Ingredient(
        name = "Rüebli",
        amount = 300.0,
        unit = IngredientUnit.GRAMM,
        canonicalName = "Karotte",
    )
    private val mehl = Ingredient(name = "Mehl", amount = 250.0, unit = IngredientUnit.GRAMM)
    private val tomaten = Ingredient(name = "gehackte Tomaten", amount = 1.0, unit = IngredientUnit.DOSE)
    private val eier = Ingredient(name = "Eier", amount = 3.0, unit = IngredientUnit.STUECK)

    private val alle = listOf(zwiebel, ruebli, mehl, tomaten, eier)

    private fun namesFor(text: String) =
        matcher.ingredientsFor(RecipeStep(text = text), alle).map { it.name }

    @Test
    fun `findet eine Zutat, die im Schritt genannt wird`() {
        assertEquals(listOf("Zwiebel"), namesFor("Die Zwiebel fein hacken."))
    }

    @Test
    fun `findet die Mehrzahl`() {
        assertEquals(listOf("Zwiebel"), namesFor("Die Zwiebeln andaempfen."))
    }

    @Test
    fun `findet ueber Schweizer Synonyme hinweg`() {
        assertEquals(listOf("Rüebli"), namesFor("Die Karotten in Scheiben schneiden."))
        assertEquals(listOf("Rüebli"), namesFor("Rüebli raffeln."))
    }

    @Test
    fun `findet mehrteilige Namen nur, wenn alle Teile vorkommen`() {
        assertTrue("gehackte Tomaten" in namesFor("Die gehackten Tomaten dazugeben."))
        assertTrue("gehackte Tomaten" !in namesFor("Die Tomaten waschen."))
    }

    @Test
    fun `nennt mehrere Zutaten eines Schritts`() {
        val gefunden = namesFor("Mehl und Eier zu einem Teig verruehren.")

        assertEquals(setOf("Mehl", "Eier"), gefunden.toSet())
    }

    @Test
    fun `behauptet nichts, wenn keine Zutat vorkommt`() {
        assertTrue(namesFor("Den Ofen auf 200 Grad vorheizen.").isEmpty())
    }

    @Test
    fun `verwechselt kurze Woerter nicht mit Wortbestandteilen`() {
        val ei = listOf(Ingredient(name = "Ei", amount = 1.0, unit = IngredientUnit.STUECK))

        val gefunden = matcher.ingredientsFor(RecipeStep(text = "Alles eingiessen."), ei)

        assertTrue(gefunden.isEmpty())
    }

    @Test
    fun `ordnet jedem Schritt seine Zutaten zu`() {
        val steps = listOf(
            RecipeStep(position = 0, text = "Zwiebeln hacken."),
            RecipeStep(position = 1, text = "Mehl einruehren."),
            RecipeStep(position = 2, text = "Servieren."),
        )

        val zuordnung = matcher.byStep(steps, alle)

        assertEquals(listOf("Zwiebel"), zuordnung.getValue(0).map { it.name })
        assertEquals(listOf("Mehl"), zuordnung.getValue(1).map { it.name })
        assertTrue(zuordnung.getValue(2).isEmpty())
    }
}
