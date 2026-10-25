package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.profile.Appliance
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.Mood
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecipeClassifierTest {
    private val classifier = RecipeClassifier()

    private fun recipe(
        title: String,
        vararg zutaten: String,
        minutes: Int? = null,
        text: String = "In der Pfanne anbraten.",
    ) = Recipe(
        title = title,
        instructions = text,
        ingredients = zutaten.map { Ingredient(name = it) },
        prepTimeMinutes = minutes,
    )

    @Test
    fun `Fleisch macht ein Rezept zum Fleischgericht`() {
        val traits = classifier.classify(recipe("Gulasch", "Rindfleisch", "Zwiebeln"))

        assertEquals(DietClass.FLEISCH, traits.diet)
    }

    @Test
    fun `Fisch ist kein Fleisch - auch nicht als Lachssteak`() {
        val traits = classifier.classify(recipe("Lachs", "Lachssteak", "Zitrone"))

        assertEquals(DietClass.FISCH, traits.diet)
    }

    @Test
    fun `Milch und Eier machen vegetarisch, Kokosmilch bleibt vegan`() {
        assertEquals(DietClass.VEGETARISCH, classifier.classify(recipe("Omelette", "Eier", "Butter")).diet)
        assertEquals(DietClass.VEGAN, classifier.classify(recipe("Curry", "Kokosmilch", "Kichererbsen", "Reis")).diet)
    }

    @Test
    fun `Reis ist kein Ei`() {
        val traits = classifier.classify(recipe("Reispfanne", "Reis", "Karotten"))

        assertEquals(DietClass.VEGAN, traits.diet)
        assertFalse(Intolerance.EIER in traits.allergens)
    }

    @Test
    fun `erkennt Allergene`() {
        val traits = classifier.classify(
            recipe("Pasta", "Spaghetti", "Parmesan", "Walnüsse", "Ei", "Garnelen", "Sojasauce"),
        )

        assertTrue(
            traits.allergens.containsAll(
                setOf(
                    Intolerance.GLUTEN,
                    Intolerance.LAKTOSE,
                    Intolerance.NUESSE,
                    Intolerance.EIER,
                    Intolerance.MEERESFRUECHTE,
                    Intolerance.SOJA,
                ),
            ),
        )
    }

    @Test
    fun `Muskatnuss und Kokosnuss sind keine Nussallergie, Buchweizen kein Gluten`() {
        val traits = classifier.classify(recipe("Galette", "Buchweizenmehl", "Muskatnuss", "Kokosnuss"))

        assertFalse(Intolerance.NUESSE in traits.allergens)
        assertFalse(Intolerance.GLUTEN in traits.allergens)
    }

    @Test
    fun `Stimmung schnell hangt an der Zeit`() {
        assertTrue(Mood.SCHNELL in classifier.classify(recipe("Rührei", "Eier", minutes = 15)).moods)
        assertFalse(Mood.SCHNELL in classifier.classify(recipe("Braten", "Rindfleisch", minutes = 120)).moods)
        assertFalse(Mood.SCHNELL in classifier.classify(recipe("Unbekannt", "Eier")).moods)
    }

    @Test
    fun `Fakeaway und Familienliebling kommen aus dem Titel`() {
        assertTrue(Mood.FAKEAWAY in classifier.classify(recipe("Chicken Burger", "Poulet", "Brötchen")).moods)
        assertTrue(Mood.FAMILIE in classifier.classify(recipe("Spaghetti Bolognese", "Hackfleisch")).moods)
    }

    @Test
    fun `Geraete ergeben sich aus der Zubereitung`() {
        val ofen = classifier.classify(recipe("Gratin", "Kartoffeln", text = "Im Backofen 30 Minuten backen."))
        val pfanne = classifier.classify(recipe("Rührei", "Eier"))
        val luft = classifier.classify(recipe("Pommes", "Kartoffeln", text = "In der Heissluftfritteuse garen."))

        assertEquals(setOf(Appliance.BACKOFEN), ofen.requiredAppliances)
        assertEquals(setOf(Appliance.HERD), pfanne.requiredAppliances)
        assertEquals(setOf(Appliance.HEISSLUFT), luft.requiredAppliances)
    }

    @Test
    fun `Etikett folgt der Rangfolge der Stimmungen`() {
        val traits = classifier.classify(recipe("Chicken Burger", "Poulet", "Brötchen", minutes = 20))

        assertEquals(MealLabel.PROTEIN, traits.label)
    }

    @Test
    fun `versteht Schweizer Zutaten`() {
        val poulet = classifier.classify(recipe("Rahmgeschnetzeltes", "Pouletbrust", "Vollrahm", "Hörnli"))
        val gemuese = classifier.classify(recipe("Gemüsepfanne", "Rüebli", "Peperoni", "Zucchetti"))
        val hack = classifier.classify(recipe("Hörnli mit Gehacktem", "Gehacktes", "Hörnli"))

        assertEquals(DietClass.FLEISCH, poulet.diet)
        assertTrue(Intolerance.LAKTOSE in poulet.allergens)
        assertTrue(Intolerance.GLUTEN in poulet.allergens)
        assertEquals(DietClass.VEGAN, gemuese.diet)
        assertEquals(DietClass.FLEISCH, hack.diet)
    }
}
