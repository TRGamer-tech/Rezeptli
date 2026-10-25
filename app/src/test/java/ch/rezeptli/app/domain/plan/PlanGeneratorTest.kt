package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.profile.Appliance
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.Mood
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.domain.shopping.ShoppingListAggregator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PlanGeneratorTest {
    private val classifier = RecipeClassifier()
    private val generator = PlanGenerator(ShoppingListAggregator())

    private fun candidate(
        title: String,
        vararg zutaten: Pair<String, Double>,
        minutes: Int? = 30,
        text: String = "In der Pfanne anbraten.",
    ): PlanCandidate {
        val recipe = Recipe(
            title = title,
            instructions = text,
            ingredients = zutaten.map { (name, gramm) ->
                Ingredient(name = name, amount = gramm, unit = IngredientUnit.GRAMM)
            },
            prepTimeMinutes = minutes,
            servings = 4,
            sourceUrl = "https://beispiel.ch/${title.lowercase()}",
        )
        return PlanCandidate(recipe, classifier.classify(recipe))
    }

    private val pool = listOf(
        candidate("Gulasch", "Rindfleisch" to 600.0, "Zwiebeln" to 200.0),
        candidate("Gemüsecurry", "Kichererbsen" to 400.0, "Kokosmilch" to 400.0, "Reis" to 300.0),
        candidate("Omelette", "Eier" to 6.0, "Butter" to 20.0),
        candidate("Pasta Pesto", "Spaghetti" to 400.0, "Basilikum" to 30.0, "Parmesan" to 50.0),
        candidate("Lachs mit Reis", "Lachs" to 400.0, "Reis" to 300.0),
        candidate("Linsensuppe", "Linsen" to 300.0, "Karotten" to 200.0, "Zwiebeln" to 100.0),
        candidate("Gratin", "Kartoffeln" to 800.0, "Rahm" to 200.0, text = "Im Backofen backen."),
    )

    private fun profile(
        tage: Int = 3,
        block: (UserProfile) -> UserProfile = { it },
    ) = block(
        UserProfile(
            householdSize = 2,
            cookDays = Weekday.entries.take(tage).toSet(),
            weeklyBudget = 200,
        ),
    )

    @Test
    fun `plant genau so viele Mahlzeiten wie Kochtage`() {
        val plan = generator.generate(pool, profile(tage = 3), Random(1))

        assertEquals(3, plan.meals.size)
        assertEquals(listOf(Weekday.MONTAG, Weekday.DIENSTAG, Weekday.MITTWOCH), plan.meals.map { it.day })
    }

    @Test
    fun `vegetarisch schliesst Fleisch und Fisch aus`() {
        val plan = generator.generate(pool, profile(tage = 3) { it.copy(diets = setOf(Diet.VEGETARISCH)) }, Random(2))

        assertTrue(plan.meals.all { it.candidate.traits.diet.ordinal >= DietClass.VEGETARISCH.ordinal })
    }

    @Test
    fun `Allergien sind harte Grenzen`() {
        val plan = generator.generate(
            pool,
            profile(tage = 5) { it.copy(intolerances = setOf(Intolerance.LAKTOSE, Intolerance.GLUTEN)) },
            Random(3),
        )

        assertTrue(plan.meals.isNotEmpty())
        assertTrue(plan.meals.none { Intolerance.LAKTOSE in it.candidate.traits.allergens })
        assertTrue(plan.meals.none { Intolerance.GLUTEN in it.candidate.traits.allergens })
    }

    @Test
    fun `ohne Backofen kein Gratin`() {
        val plan = generator.generate(
            pool,
            profile(tage = 7) { it.copy(appliances = setOf(Appliance.HERD)) },
            Random(4),
        )

        assertFalse(plan.meals.any { it.recipe.title == "Gratin" })
    }

    @Test
    fun `Stimmung schiebt passende Rezepte nach vorn`() {
        val schnell = pool.map { c ->
            if (c.recipe.title == "Omelette") {
                c.copy(recipe = c.recipe.copy(prepTimeMinutes = 10)).let { it.copy(traits = classifier.classify(it.recipe)) }
            } else {
                c
            }
        }

        val plan = generator.generate(schnell, profile(tage = 1) { it.copy(moods = setOf(Mood.SCHNELL)) }, Random(5))

        assertEquals("Omelette", plan.meals.single().recipe.title)
    }

    @Test
    fun `der Einkauf wird auf die Personenzahl umgerechnet`() {
        val zwei = generator.generate(pool.take(1), profile(tage = 1), Random(6))
        val acht = generator.generate(pool.take(1), profile(tage = 1) { it.copy(householdSize = 8) }, Random(6))

        val fleischZwei = zwei.basket.first { it.name == "Rindfleisch" }
        val fleischAcht = acht.basket.first { it.name == "Rindfleisch" }

        assertEquals(300.0, fleischZwei.amount)
        assertEquals(IngredientUnit.GRAMM, fleischZwei.unit)
        // 1200 g schreibt der Einkaufszettel als 1.2 kg.
        assertEquals(1.2, fleischAcht.amount)
        assertEquals(IngredientUnit.KILOGRAMM, fleischAcht.unit)
    }

    @Test
    fun `der Einkauf traegt Packung und Preis und gehoert zum Plan`() {
        val plan = generator.generate(pool, profile(tage = 2), Random(7))

        assertTrue(plan.basket.isNotEmpty())
        assertTrue(plan.basket.all { it.fromPlan && it.packText != null && it.price != null })
        assertTrue(plan.basketCost > 0.0)
    }

    @Test
    fun `ein knappes Budget tauscht teure Gerichte gegen guenstigere`() {
        val offen = generator.generate(pool, profile(tage = 3) { it.copy(weeklyBudget = 400) }, Random(8))
        val knapp = generator.generate(pool, profile(tage = 3) { it.copy(weeklyBudget = 15) }, Random(8))

        assertTrue(knapp.basketCost <= offen.basketCost)
    }

    @Test
    fun `ersetzen tauscht genau einen Tag und nimmt nichts aus dem Plan doppelt`() {
        val plan = generator.generate(pool, profile(tage = 3), Random(9))
        val vorher = plan.meals.map { it.recipe.title }

        val neu = generator.replace(plan, Weekday.DIENSTAG, pool, profile(tage = 3), Random(10))
        val nachher = neu.meals.map { it.recipe.title }

        assertEquals(vorher[0], nachher[0])
        assertEquals(vorher[2], nachher[2])
        assertFalse(nachher[1] == vorher[1])
        assertEquals(nachher.size, nachher.toSet().size)
    }

    @Test
    fun `ohne passende Rezepte bleibt der Plan leer statt zu raten`() {
        val plan = generator.generate(
            pool,
            profile { it.copy(diets = setOf(Diet.VEGAN), appliances = setOf(Appliance.MIKROWELLE)) },
            Random(11),
        )

        assertTrue(plan.isIncomplete)
    }
}
