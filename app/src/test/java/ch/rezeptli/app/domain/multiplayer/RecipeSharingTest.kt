package ch.rezeptli.app.domain.multiplayer

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.steps.RecipeStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Die Uebergabe eines selbst erfassten Rezepts von einem Telefon zum anderen.
 *
 * Bisher liess sich so ein Treffer nicht uebernehmen: Er hat keine Adresse, von der die
 * andere Person ihn holen koennte.
 */
class RecipeSharingTest {
    private val hackbraten = Recipe(
        id = 7L,
        title = "Omas Hackbraten",
        instructions = "Mischen, formen, backen.",
        steps = listOf(
            RecipeStep(position = 1, text = "Eine Stunde backen.", timerMinutes = 60),
            RecipeStep(position = 0, text = "Mischen und formen."),
        ),
        ingredients = listOf(
            Ingredient(name = "Zwiebeln", amount = 2.0, note = "fein gehackt", position = 1),
            Ingredient(name = "Hackfleisch", amount = 800.0, unit = IngredientUnit.GRAMM, position = 0),
            Ingredient(name = "Salz", position = 2),
        ),
        tags = listOf("Familie"),
        prepTimeMinutes = 75,
        servings = 4,
        photoUri = "content://media/foto-7",
        lastCookedAt = 1_700_000_000_000L,
    )

    private val karte = SharedRecipe(recipeId = 3L, title = "Omas Hackbraten", key = "e:anna:7")

    @Test
    fun `mit geht, was man zum Kochen braucht - in der Reihenfolge des Rezepts`() {
        val inhalt = RecipeSharing.contentOf(hackbraten)

        assertEquals(listOf("Hackfleisch", "Zwiebeln", "Salz"), inhalt.ingredients.map { it.name })
        assertEquals("GRAMM", inhalt.ingredients.first().unit)
        // "Ohne Einheit" ist keine Einheit, die verschickt werden muesste.
        assertNull(inhalt.ingredients[1].unit)
        assertEquals(listOf("Mischen und formen.", "Eine Stunde backen."), inhalt.steps)
        assertEquals(4, inhalt.servings)
        assertEquals(75, inhalt.prepTimeMinutes)
    }

    @Test
    fun `auf dem anderen Telefon entsteht daraus dasselbe Rezept - ohne Foto, Nummer und Kochdatum`() {
        val angekommen = RecipeSharing.recipeFrom(karte, RecipeSharing.contentOf(hackbraten))

        assertEquals(0L, angekommen.id)
        assertEquals("Omas Hackbraten", angekommen.title)
        assertEquals("Mischen, formen, backen.", angekommen.instructions)
        assertEquals(listOf("Mischen und formen.", "Eine Stunde backen."), angekommen.steps.map { it.text })
        assertEquals(listOf(0, 1), angekommen.steps.map { it.position })
        assertEquals(
            listOf(
                "Hackfleisch" to IngredientUnit.GRAMM,
                "Zwiebeln" to IngredientUnit.NONE,
                "Salz" to IngredientUnit.NONE,
            ),
            angekommen.ingredients.map { it.name to it.unit },
        )
        assertEquals("fein gehackt", angekommen.ingredients[1].note)
        assertEquals(listOf("Familie"), angekommen.tags)
        assertEquals(4, angekommen.servings)
        assertNull(angekommen.photoUri)
        assertNull(angekommen.lastCookedAt)
        assertNull(angekommen.sourceUrl)
    }

    @Test
    fun `eine Einheit, die diese App nicht kennt, kostet nicht die Zutat`() {
        val inhalt = SharedContent(ingredients = listOf(SharedIngredient("Mehl", amount = 2.0, unit = "SCHEFFEL")))

        val zutat = RecipeSharing.recipeFrom(karte, inhalt).ingredients.single()

        assertEquals("Mehl", zutat.name)
        assertEquals(2.0, zutat.amount)
        assertEquals(IngredientUnit.NONE, zutat.unit)
    }

    @Test
    fun `fehlt die Zubereitung als Text, wird sie aus den Schritten zusammengesetzt`() {
        val inhalt = SharedContent(steps = listOf("Teig kneten.", "Backen."))

        val rezept = RecipeSharing.recipeFrom(karte.copy(prepTimeMinutes = 40), inhalt)

        assertEquals("Teig kneten.\n\nBacken.", rezept.instructions)
        // Die Zeit steht dann wenigstens so da, wie die Karte sie nannte.
        assertEquals(40, rezept.prepTimeMinutes)
    }
}
