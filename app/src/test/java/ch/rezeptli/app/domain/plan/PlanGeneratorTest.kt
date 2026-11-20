package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.profile.Appliance
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.Mood
import ch.rezeptli.app.domain.profile.StoreChain
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.domain.shopping.ShoppingListAggregator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
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

    /**
     * Ohne weitere Angabe plant das Testprofil nur das Abendessen - so bleibt "ein Tag, eine
     * Mahlzeit" in den Tests lesbar. Mit [beideMahlzeiten] gilt die Vorgabe der App: Mittag und Abend.
     */
    private fun profile(
        tage: Int = 3,
        beideMahlzeiten: Boolean = false,
        block: (UserProfile) -> UserProfile = { it },
    ) = block(
        UserProfile(
            householdSize = 2,
            cookDays = Weekday.entries.take(tage).toSet(),
            mealSlots = if (beideMahlzeiten) emptySet() else setOf(MealSlot.ABEND),
            weeklyBudget = 400,
        ),
    )

    private fun PlanDraft.titles() = meals.map { it.recipe.title }

    @Test
    fun `plant genau so viele Mahlzeiten wie Kochtage`() {
        val plan = generator.generate(pool, profile(tage = 3), Random(1))

        assertEquals(3, plan.meals.size)
        assertEquals(listOf(Weekday.MONTAG, Weekday.DIENSTAG, Weekday.MITTWOCH), plan.meals.map { it.day })
        assertTrue(plan.meals.all { it.slot == MealSlot.ABEND })
    }

    @Test
    fun `ohne eigene Angabe gibt es an jedem Kochtag Mittag- und Abendessen`() {
        val plan = generator.generate(pool, profile(tage = 3, beideMahlzeiten = true), Random(1))

        assertEquals(
            listOf(
                PlanPlace(Weekday.MONTAG, MealSlot.MITTAG),
                PlanPlace(Weekday.MONTAG, MealSlot.ABEND),
                PlanPlace(Weekday.DIENSTAG, MealSlot.MITTAG),
                PlanPlace(Weekday.DIENSTAG, MealSlot.ABEND),
                PlanPlace(Weekday.MITTWOCH, MealSlot.MITTAG),
                PlanPlace(Weekday.MITTWOCH, MealSlot.ABEND),
            ),
            plan.meals.map { it.place },
        )
        // Sechs Mahlzeiten sind sechs verschiedene Rezepte.
        assertEquals(6, plan.titles().toSet().size)
    }

    @Test
    fun `reichen die Rezepte nicht fuer alles, bekommt zuerst jeder Tag ein Abendessen`() {
        val plan = generator.generate(pool.take(4), profile(tage = 3, beideMahlzeiten = true), Random(2))

        assertEquals(4, plan.meals.size)
        assertEquals(
            listOf(Weekday.MONTAG, Weekday.DIENSTAG, Weekday.MITTWOCH),
            plan.meals.filter { it.slot == MealSlot.ABEND }.map { it.day },
        )
        assertEquals(1, plan.meals.count { it.slot == MealSlot.MITTAG })
    }

    @Test
    fun `mittags kommt auf den Tisch, was schnell geht`() {
        val zwei = listOf(
            candidate("Schmorbraten", "Rindfleisch" to 800.0, "Karotten" to 300.0, minutes = 150),
            candidate("Omelette", "Eier" to 6.0, "Butter" to 20.0, minutes = 10),
        )

        val plan = generator.generate(zwei, profile(tage = 1, beideMahlzeiten = true), Random(3))

        assertEquals(
            "Omelette",
            plan.meals
                .single { it.slot == MealSlot.MITTAG }
                .recipe.title,
        )
        assertEquals(
            "Schmorbraten",
            plan.meals
                .single { it.slot == MealSlot.ABEND }
                .recipe.title,
        )
    }

    @Test
    fun `Getraenke, Desserts und Beiwerk sind keine Mahlzeiten und kommen nicht in den Plan`() {
        val keineMahlzeiten = listOf(
            candidate("Erdbeer-Smoothie", "Erdbeeren" to 300.0, "Joghurt" to 200.0),
            candidate("Schokoladenkuchen", "Mehl" to 200.0, "Zucker" to 200.0, "Schokolade" to 200.0),
            candidate("Vanilleglace", "Rahm" to 300.0, "Zucker" to 100.0),
            candidate("Tomatensauce", "Tomaten" to 800.0, "Zwiebeln" to 100.0),
            candidate("Hugo", "Prosecco" to 100.0, "Holunderblütensirup" to 20.0),
        )

        // Vierzehn Plaetze, aber nur sieben Mahlzeiten im Vorrat: Die Luecke wird nicht mit Kuchen gefuellt.
        val plan = generator.generate(pool + keineMahlzeiten, profile(tage = 7, beideMahlzeiten = true), Random(4))

        assertEquals(pool.map { it.recipe.title }.toSet(), plan.titles().toSet())
    }

    @Test
    fun `was jemand nicht mag, kommt nicht in den Plan`() {
        val plan = generator.generate(
            pool,
            profile(tage = 7) { it.copy(dislikes = setOf(Dislike.FISCH, Dislike.RIND)) },
            Random(5),
        )

        assertEquals(5, plan.meals.size)
        assertFalse("Lachs mit Reis" in plan.titles())
        assertFalse("Gulasch" in plan.titles())
    }

    @Test
    fun `ein eigenes Stichwort schliesst aus, was es enthaelt`() {
        val plan = generator.generate(
            pool,
            profile(tage = 7) { it.copy(customDislikes = listOf("Linsen", "Kichererbsen")) },
            Random(6),
        )

        assertFalse("Linsensuppe" in plan.titles())
        assertFalse("Gemüsecurry" in plan.titles())
        assertEquals(5, plan.meals.size)
    }

    @Test
    fun `eine Kueche ist eine Vorliebe - Gewaehltes kommt zuerst, aber nichts faellt heraus`() {
        val italienisch = profile(tage = 1) { it.copy(cuisines = setOf(Cuisine.ITALIENISCH)) }

        // Bei nur einem Platz gewinnt das italienische Gericht - gleich mit welchem Zufall.
        (1..8).forEach { seed ->
            assertEquals(listOf("Pasta Pesto"), generator.generate(pool, italienisch, Random(seed)).titles())
        }

        // Bei sieben Plaetzen ist trotzdem alles dabei: Die Vorliebe sortiert, sie streicht nicht.
        val woche = generator.generate(pool, italienisch.copy(cookDays = Weekday.entries.toSet()), Random(1))
        assertEquals(7, woche.meals.size)
    }

    @Test
    fun `der Hauptladen bestimmt die Preise - weitere Laeden aendern nichts daran`() {
        fun kosten(vararg laeden: StoreChain) =
            generator.generate(pool, profile(tage = 3) { it.copy(stores = laeden.toList()) }, Random(7)).basketCost

        val aldi = kosten(StoreChain.ALDI)
        val coop = kosten(StoreChain.COOP)

        assertTrue(aldi < coop)
        assertEquals(aldi, kosten(StoreChain.ALDI, StoreChain.COOP, StoreChain.VOLG))
        assertEquals(coop, kosten(StoreChain.COOP, StoreChain.ALDI))
    }

    @Test
    fun `im Ausland gilt das Preisniveau des Landes`() {
        val schweiz = generator.generate(pool, profile(tage = 3), Random(7)).basketCost
        val deutschland = generator
            .generate(pool, profile(tage = 3) { it.copy(country = Country.DEUTSCHLAND) }, Random(7))
            .basketCost

        assertTrue(deutschland < schweiz)
    }

    @Test
    fun `ein Rezept fuer den Dampfgarer gibt es nur mit Dampfgarer - dann aber bevorzugt`() {
        val gedaempft = candidate(
            "Lachs aus dem Steamer",
            "Lachs" to 400.0,
            "Broccoli" to 300.0,
            text = "Im Steamer bei 80 Grad 15 Minuten garen.",
        )
        assertEquals(setOf(Appliance.DAMPFGARER), gedaempft.traits.requiredAppliances)

        val ohne = profile(tage = 7) { it.copy(appliances = setOf(Appliance.HERD, Appliance.BACKOFEN)) }
        assertFalse("Lachs aus dem Steamer" in generator.generate(pool + gedaempft, ohne, Random(8)).titles())

        val mit = profile(tage = 1) { it.copy(appliances = setOf(Appliance.HERD, Appliance.DAMPFGARER)) }
        (1..8).forEach { seed ->
            assertEquals(
                listOf("Lachs aus dem Steamer"),
                generator.generate(pool + gedaempft, mit, Random(seed)).titles(),
            )
        }
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
                c
                    .copy(
                        recipe = c.recipe.copy(prepTimeMinutes = 10),
                    ).let { it.copy(traits = classifier.classify(it.recipe)) }
            } else {
                c
            }
        }

        val plan = generator.generate(schnell, profile(tage = 1) { it.copy(moods = setOf(Mood.SCHNELL)) }, Random(5))

        assertEquals(
            "Omelette",
            plan.meals
                .single()
                .recipe.title,
        )
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
    fun `ersetzen tauscht genau einen Platz und nimmt nichts aus dem Plan doppelt`() {
        val plan = generator.generate(pool, profile(tage = 3), Random(9))
        val vorher = plan.titles()

        val platz = PlanPlace(Weekday.DIENSTAG, MealSlot.ABEND)
        val neu = generator.replace(plan, platz, pool, profile(tage = 3), random = Random(10))
        val nachher = neu.titles()

        assertEquals(vorher[0], nachher[0])
        assertEquals(vorher[2], nachher[2])
        assertFalse(nachher[1] == vorher[1])
        assertEquals(nachher.size, nachher.toSet().size)
    }

    @Test
    fun `ersetzen tauscht nur die eine Mahlzeit des Tages`() {
        val profil = profile(tage = 2, beideMahlzeiten = true)
        val plan = generator.generate(pool, profil, Random(9))

        val platz = PlanPlace(Weekday.MONTAG, MealSlot.MITTAG)
        val neu = generator.replace(plan, platz, pool, profil, random = Random(10))

        val geaendert = plan.meals.zip(neu.meals).filter { (alt, jetzt) -> alt.recipe.title != jetzt.recipe.title }
        assertEquals(listOf(platz), geaendert.map { it.first.place })
    }

    @Test
    fun `wiederholtes Ersetzen bringt jedes Mal etwas Neues und dreht sich nicht im Kreis`() {
        // Genau das ging schief: Das eben Verworfene war sofort wieder der beste freie Kandidat,
        // und "ersetzen" wechselte nur zwischen denselben zwei Rezepten hin und her.
        val profil = profile(tage = 3)
        val platz = PlanPlace(Weekday.DIENSTAG, MealSlot.ABEND)
        var plan = generator.generate(pool, profil, Random(9))
        val amAnfang = plan.titles().toSet()

        val verworfen = mutableSetOf<String>()
        val vorgeschlagen = mutableListOf<String>()
        repeat(4) { runde ->
            verworfen += plan.meals
                .first { it.place == platz }
                .candidate.key
            plan = generator.replace(plan, platz, pool, profil, avoid = verworfen, random = Random(runde))
            vorgeschlagen += plan.meals
                .first { it.place == platz }
                .recipe.title
        }

        // Sieben Rezepte, drei im Plan: Es gibt genau vier andere - und jedes kommt einmal dran.
        assertEquals(4, vorgeschlagen.toSet().size)
        assertTrue(vorgeschlagen.none { it in amAnfang })

        // Danach ist nichts Neues mehr da. Der Plan bleibt, wie er ist, statt Altes zu wiederholen.
        verworfen += plan.meals
            .first { it.place == platz }
            .candidate.key
        assertSame(plan, generator.replace(plan, platz, pool, profil, avoid = verworfen, random = Random(99)))
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
