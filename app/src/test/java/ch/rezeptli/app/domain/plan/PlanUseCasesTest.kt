package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.deck.CuratedDeckBuilder
import ch.rezeptli.app.domain.deck.DeckEntry
import ch.rezeptli.app.domain.deck.DeckTitles
import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.model.WebRecipe
import ch.rezeptli.app.domain.parser.IngredientTextParser
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.ProfileEdits
import ch.rezeptli.app.domain.profile.StoreChain
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.domain.ranking.SourceOrigin
import ch.rezeptli.app.domain.repository.DeckPool
import ch.rezeptli.app.domain.shopping.ShoppingListAggregator
import ch.rezeptli.app.domain.steps.InstructionSplitter
import ch.rezeptli.app.domain.translate.NoTranslation
import ch.rezeptli.app.domain.usecase.AddRecipesToShoppingListUseCase
import ch.rezeptli.app.domain.usecase.BuildSwipeDeckUseCase
import ch.rezeptli.app.domain.usecase.DeleteRecipeUseCase
import ch.rezeptli.app.domain.usecase.KeepRecipeUseCase
import ch.rezeptli.app.domain.usecase.LoadWebRecipeUseCase
import ch.rezeptli.app.domain.usecase.MarkRecipeCookedUseCase
import ch.rezeptli.app.fake.FakePlanMemory
import ch.rezeptli.app.fake.FakePlanRepository
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.fake.FakeShoppingListRepository
import ch.rezeptli.app.fake.FakeUserProfileRepository
import ch.rezeptli.app.fake.FakeWebRecipeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Der Wochenplan vom Verzeichnis bis in die Datenbank - mit den echten Bausteinen, nur das
 * Netz und die Datenbank sind ersetzt.
 *
 * Geprueft wird, was auf dem Geraet aufgefallen ist: Der Plan fuellte ungefragt "Meine
 * Rezepte", enthielt Getraenke und Desserts, hatte nur eine Mahlzeit pro Tag, und
 * "ersetzen" wechselte bloss zwischen denselben Rezepten hin und her.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanUseCasesTest {
    private val mahlzeiten = listOf(
        "Spaghetti Bolognese" to listOf("400 g Spaghetti", "300 g Rindshackfleisch"),
        "Linsensuppe" to listOf("300 g Linsen", "2 Karotten"),
        "Kartoffelgratin" to listOf("800 g Kartoffeln", "2 dl Rahm"),
        "Gemüsecurry" to listOf("400 g Kichererbsen", "4 dl Kokosmilch"),
        "Rösti mit Spiegelei" to listOf("800 g Kartoffeln", "4 Eier"),
        "Tomatenrisotto" to listOf("300 g Risottoreis", "400 g Tomaten"),
        "Lachs mit Reis" to listOf("400 g Lachsfilet", "300 g Reis"),
        "Poulet-Geschnetzeltes" to listOf("500 g Pouletbrust", "2 dl Rahm"),
        "Älplermagronen" to listOf("400 g Hörnli", "200 g Gruyère"),
        "Gulasch" to listOf("600 g Rindfleisch", "200 g Zwiebeln"),
        "Omelette" to listOf("6 Eier", "20 g Butter"),
        "Chili sin Carne" to listOf("400 g Kidneybohnen", "400 g Tomaten"),
        "Ofengemüse mit Feta" to listOf("500 g Zucchetti", "200 g Feta"),
        "Hörnli mit Ghacktem" to listOf("400 g Hörnli", "400 g Hackfleisch"),
        "Wirzeintopf" to listOf("500 g Wirz", "300 g Kartoffeln"),
        "Kürbissuppe" to listOf("800 g Kürbis", "2 dl Bouillon"),
    )
    private val keineMahlzeiten = listOf(
        "Erdbeer-Smoothie" to listOf("300 g Erdbeeren", "2 dl Joghurt"),
        "Schokoladenkuchen" to listOf("200 g Mehl", "200 g Schokolade"),
        "Tomatensauce" to listOf("800 g Tomaten", "1 Zwiebel"),
    )

    private fun adresse(titel: String) = "https://a.example/" + titel.lowercase().replace(' ', '-')

    private val web = (mahlzeiten + keineMahlzeiten).let { alle ->
        FakeWebRecipeRepository(
            pool = DeckPool(
                entriesBySource = mapOf(
                    "a" to alle.map { (titel, _) ->
                        DeckEntry(
                            url = adresse(titel),
                            title = titel,
                            imageUrl = adresse(titel) + ".jpg",
                            sourceId = "a",
                            sourceName = "A",
                        )
                    },
                ),
                origins = mapOf("a" to SourceOrigin("a", Country.SCHWEIZ)),
            ),
            recipeByUrl = alle.associate { (titel, zutaten) ->
                adresse(titel) to WebRecipe(
                    title = titel,
                    ingredientLines = zutaten,
                    instructions = "Alles in der Pfanne zubereiten.",
                    servings = "4",
                    totalMinutes = 30,
                    sourceUrl = adresse(titel),
                    sourceName = "A",
                )
            },
        )
    }

    private val eigenes = Recipe(
        id = 1L,
        title = "Omas Hackbraten",
        instructions = "Im Ofen backen.",
        ingredients = listOf(
            Ingredient(name = "Hackfleisch", amount = 800.0, unit = IngredientUnit.GRAMM),
            Ingredient(name = "Zwiebeln", amount = 200.0, unit = IngredientUnit.GRAMM),
        ),
        servings = 4,
    )

    private val planRepository = FakePlanRepository()
    private val recipes = FakeRecipeRepository(listOf(eigenes)).also { it.plan = planRepository }
    private val shopping = FakeShoppingListRepository()
    private val memory = FakePlanMemory()
    private val classifier = RecipeClassifier()
    private val generator = PlanGenerator(ShoppingListAggregator())
    private val persister = PlanPersister(recipes, shopping, planRepository)

    private fun profil(block: (UserProfile) -> UserProfile = { it }) =
        FakeUserProfileRepository(block(UserProfile(country = Country.SCHWEIZ, householdSize = 2, weeklyBudget = 400)))

    private fun source(profile: FakeUserProfileRepository) = PlanCandidateSource(
        buildDeck = BuildSwipeDeckUseCase(
            webRepository = web,
            profileRepository = profile,
            builder = CuratedDeckBuilder(),
            titles = DeckTitles(classifier),
            dispatcher = UnconfinedTestDispatcher(),
        ),
        loadWebRecipe = LoadWebRecipeUseCase(web, IngredientTextParser(), InstructionSplitter(), NoTranslation),
        recipeRepository = recipes,
        classifier = classifier,
    )

    private class Planer(val bauen: GeneratePlanUseCase, val ersetzen: ReplaceMealUseCase)

    private fun planer(profile: FakeUserProfileRepository = profil()): Planer {
        val quelle = source(profile)
        return Planer(
            bauen = GeneratePlanUseCase(profile, quelle, generator, persister, memory),
            ersetzen = ReplaceMealUseCase(
                profile,
                planRepository,
                recipes,
                quelle,
                classifier,
                generator,
                persister,
                memory,
            ),
        )
    }

    private suspend fun titelImPlan(): List<String> =
        planRepository.currentPlan().mapNotNull { recipes.getRecipe(it.recipeId)?.title }

    @Test
    fun `der Plan hat an jedem Kochtag ein Mittag- und ein Abendessen`() = runTest {
        val ergebnis = planer().bauen()

        assertTrue(ergebnis is PlanOutcome.Built)
        val plan = planRepository.currentPlan()
        assertEquals(10, plan.size)
        Weekday.entries.take(5).forEach { tag ->
            assertEquals(
                listOf(MealSlot.MITTAG, MealSlot.ABEND),
                plan.filter { it.day == tag }.map { it.slot },
                tag.name,
            )
        }
        assertEquals(10, plan.map { it.recipeId }.toSet().size, "Zehn Mahlzeiten sind zehn verschiedene Rezepte")
    }

    @Test
    fun `wer nur das Abendessen plant, bekommt eine Mahlzeit pro Tag`() = runTest {
        planer(profil { it.copy(mealSlots = setOf(MealSlot.ABEND)) }).bauen()

        val plan = planRepository.currentPlan()
        assertEquals(5, plan.size)
        assertTrue(plan.all { it.slot == MealSlot.ABEND })
    }

    @Test
    fun `im Plan stehen nur Mahlzeiten - keine Getraenke, Desserts oder Saucen`() = runTest {
        // Sieben Tage mal zwei: Mit 17 Mahlzeiten im Vorrat ist die Woche voll, ohne dass ein
        // Smoothie oder Kuchen aushelfen muss.
        planer(profil { it.copy(cookDays = Weekday.entries.toSet()) }).bauen()

        val titel = titelImPlan()
        assertEquals(14, titel.size)
        assertTrue(titel.none { name -> keineMahlzeiten.any { it.first == name } }, titel.toString())
    }

    @Test
    fun `ein neuer Plan fuellt die eigene Sammlung nicht`() = runTest {
        planer().bauen()

        // In "Meine Rezepte" steht weiterhin nur, was ich selbst dort hingetan habe ...
        assertEquals(listOf("Omas Hackbraten"), recipes.visible(RecipeFilter()).map { it.title })
        // ... die Rezepte des Plans sind gespeichert, damit man sie kochen kann, aber nicht in der Sammlung.
        val nurImPlan = recipes.all().filterNot { it.inCollection }
        assertTrue(nurImPlan.size >= 9)
        assertTrue(planRepository.currentPlan().all { eintrag -> recipes.getRecipe(eintrag.recipeId) != null })
    }

    @Test
    fun `ein Rezept aus dem Plan laesst sich ausdruecklich behalten - und bleibt dann auch`() = runTest {
        val planer = planer()
        planer.bauen()
        val geplant = planRepository.currentPlan().first { recipes.getRecipe(it.recipeId)?.inCollection == false }

        KeepRecipeUseCase(recipes)(geplant.recipeId)
        val behalten = recipes.getRecipe(geplant.recipeId)!!

        assertTrue(behalten.title in recipes.visible(RecipeFilter()).map { it.title })

        // Auch wenn die naechste Woche ganz anders aussieht, ist es noch da.
        planRepository.savePlan(emptyList(), emptyList())
        recipes.deleteUnusedPlanRecipes()
        assertNotNull(recipes.getRecipe(geplant.recipeId))
    }

    @Test
    fun `was aus dem Plan faellt und nie behalten wurde, bleibt nicht als Karteileiche liegen`() = runTest {
        val planer = planer()
        planer.bauen()
        val vorher = planRepository.currentPlan().first { it.day == Weekday.MONTAG && it.slot == MealSlot.ABEND }
        val warNurImPlan = recipes.getRecipe(vorher.recipeId)?.inCollection == false

        assertTrue(planer.ersetzen(Weekday.MONTAG, MealSlot.ABEND))

        if (warNurImPlan) assertNull(recipes.getRecipe(vorher.recipeId))
        // Das eigene Rezept wird dabei nie angefasst.
        assertNotNull(recipes.getRecipe(eigenes.id))
    }

    @Test
    fun `ersetzen bringt jedes Mal ein Rezept, das diese Woche noch nicht vorkam`() = runTest {
        val planer = planer()
        planer.bauen()
        val amAnfang = titelImPlan().toSet()

        suspend fun montagabend() = planRepository
            .currentPlan()
            .first { it.day == Weekday.MONTAG && it.slot == MealSlot.ABEND }
            .let { recipes.getRecipe(it.recipeId)!!.title }

        val ersterVorschlag = montagabend()
        val vorschlaege = mutableListOf<String>()
        repeat(5) {
            assertTrue(planer.ersetzen(Weekday.MONTAG, MealSlot.ABEND))
            vorschlaege += montagabend()
        }

        // Fuenfmal getauscht, fuenf verschiedene Rezepte - und keines davon stand schon im Plan
        // oder wurde eben erst verworfen.
        assertEquals(5, vorschlaege.toSet().size, vorschlaege.toString())
        assertFalse(ersterVorschlag in vorschlaege)
        assertTrue(vorschlaege.none { it in amAnfang }, vorschlaege.toString())
        assertEquals(5, memory.rejected().size)
    }

    @Test
    fun `ersetzen tauscht nur die eine Mahlzeit und laesst den Rest der Woche stehen`() = runTest {
        val planer = planer()
        planer.bauen()
        val vorher = planRepository.currentPlan()

        planer.ersetzen(Weekday.DIENSTAG, MealSlot.MITTAG)

        val nachher = planRepository.currentPlan()
        val geaendert = vorher.zip(nachher).filter { (alt, neu) -> alt.recipeId != neu.recipeId }.map { it.first.place }
        assertEquals(listOf(PlanPlace(Weekday.DIENSTAG, MealSlot.MITTAG)), geaendert)
        assertEquals(10, nachher.size)
    }

    @Test
    fun `gibt es nichts anderes mehr, bleibt die Mahlzeit - statt Verworfenes zu wiederholen`() = runTest {
        // Wer nichts mit Rind, Poulet, Fisch, Schwein oder Zwiebeln will, hat wenig Auswahl.
        val heikel = profil {
            it.copy(
                cookDays = setOf(Weekday.MONTAG),
                mealSlots = setOf(MealSlot.ABEND),
                dislikes = Dislike.entries.toSet() - Dislike.SCHARF,
            )
        }
        val planer = planer(heikel)
        planer.bauen()

        var getauscht = 0
        repeat(30) { if (planer.ersetzen(Weekday.MONTAG, MealSlot.ABEND)) getauscht += 1 }

        // Irgendwann ist der Vorrat durch. Dann sagt "ersetzen" nein, statt von vorn anzufangen.
        assertTrue(getauscht < 30)
        assertFalse(planer.ersetzen(Weekday.MONTAG, MealSlot.ABEND))
        assertEquals(1, planRepository.currentPlan().size)
    }

    @Test
    fun `ein neuer Plan vergisst, was in der alten Woche verworfen wurde`() = runTest {
        val planer = planer()
        planer.bauen()
        planer.ersetzen(Weekday.MONTAG, MealSlot.ABEND)
        assertEquals(1, memory.rejected().size)

        planer.bauen()

        assertTrue(memory.rejected().isEmpty())
    }

    @Test
    fun `was im Kochmodus fertig gekocht wurde, ist im Plan abgehakt - und laesst sich zuruecknehmen`() = runTest {
        planer().bauen()
        val mahlzeit = planRepository.currentPlan().first()
        val vermerken = MarkRecipeCookedUseCase(recipes, planRepository)

        vermerken(mahlzeit.recipeId, cookedAt = 1_700_000_000_000L)

        assertEquals(1_700_000_000_000L, recipes.getRecipe(mahlzeit.recipeId)?.lastCookedAt)
        assertEquals(listOf(mahlzeit.place), planRepository.currentPlan().filter { it.isCooked }.map { it.place })

        vermerken(mahlzeit.recipeId, cookedAt = null)

        assertNull(recipes.getRecipe(mahlzeit.recipeId)?.lastCookedAt)
        assertTrue(planRepository.currentPlan().none { it.isCooked })
    }

    @Test
    fun `der Vermerk gekocht ueberlebt das Tauschen einer anderen Mahlzeit`() = runTest {
        val planer = planer()
        planer.bauen()
        val gekocht = planRepository.currentPlan().first { it.day == Weekday.MONTAG && it.slot == MealSlot.MITTAG }
        MarkRecipeCookedUseCase(recipes, planRepository)(gekocht.recipeId, cookedAt = 42L)

        planer.ersetzen(Weekday.FREITAG, MealSlot.ABEND)

        assertEquals(42L, planRepository.currentPlan().first { it.place == gekocht.place }.cookedAt)
    }

    private fun anpassen(profile: FakeUserProfileRepository) =
        AdjustPlanUseCase(profile, planRepository, recipes, classifier, generator, persister)

    @Test
    fun `wer fuer mehr Personen kocht, bekommt Mengen und Kosten umgerechnet - die Gerichte bleiben`() = runTest {
        val profil = profil()
        planer(profil).bauen()
        val vorher = planRepository.currentPlan()
        val einkaufVorher = planRepository.savedBasket.sumOf { it.price ?: 0.0 }
        assertTrue(vorher.all { it.servings == 2 })

        profil.update { it.copy(householdSize = 6) }
        assertTrue(anpassen(profil)())

        val nachher = planRepository.currentPlan()
        assertEquals(vorher.map { it.place to it.recipeId }, nachher.map { it.place to it.recipeId })
        assertTrue(nachher.all { it.servings == 6 })
        // Dreimal so viele Esser kosten rund dreimal so viel - nicht aufs Komma, weil Mengen
        // beim Umrechnen auf brauchbare Werte gerundet werden.
        vorher.zip(nachher).forEach { (alt, neu) -> assertTrue(neu.cost > alt.cost * 2, alt.title) }
        val verhaeltnis = nachher.sumOf { it.cost } / vorher.sumOf { it.cost }
        assertTrue(verhaeltnis in 2.5..3.5, "Verhaeltnis $verhaeltnis")
        assertTrue(planRepository.savedBasket.sumOf { it.price ?: 0.0 } > einkaufVorher)
    }

    @Test
    fun `wechselt der Hauptladen, folgen die Preise - nicht die Gerichte`() = runTest {
        val profil = profil { it.copy(stores = listOf(StoreChain.COOP, StoreChain.ALDI)) }
        planer(profil).bauen()
        val vorher = planRepository.currentPlan()
        val einkaufVorher = planRepository.savedBasket.sumOf { it.price ?: 0.0 }

        profil.update { ProfileEdits.mainStore(it, StoreChain.ALDI) }
        assertTrue(anpassen(profil)())

        val nachher = planRepository.currentPlan()
        assertEquals(vorher.map { it.place to it.recipeId }, nachher.map { it.place to it.recipeId })
        val verhaeltnis = StoreChain.ALDI.priceFactor / StoreChain.COOP.priceFactor
        vorher.zip(nachher).forEach { (alt, neu) -> assertEquals(alt.cost * verhaeltnis, neu.cost, 0.01, alt.title) }
        assertTrue(planRepository.savedBasket.sumOf { it.price ?: 0.0 } < einkaufVorher)
    }

    @Test
    fun `beim Umrechnen bleibt gekocht, was gekocht ist`() = runTest {
        val profil = profil()
        planer(profil).bauen()
        val gekocht = planRepository.currentPlan().first()
        MarkRecipeCookedUseCase(recipes, planRepository)(gekocht.recipeId, cookedAt = 42L)

        profil.update { it.copy(householdSize = 3) }
        anpassen(profil)()

        assertEquals(42L, planRepository.currentPlan().first { it.place == gekocht.place }.cookedAt)
        assertEquals(1, planRepository.currentPlan().count { it.isCooked })
    }

    @Test
    fun `ohne Plan gibt es nichts umzurechnen`() = runTest {
        assertFalse(anpassen(profil())())

        assertTrue(planRepository.currentPlan().isEmpty())
    }

    @Test
    fun `ein Rezept, das im Plan steht, verschwindet beim Entfernen nur aus der Sammlung`() = runTest {
        planRepository.savePlan(
            listOf(PlanWrite(Weekday.MONTAG, MealSlot.ABEND, eigenes.id, 2, 9.0, MealLabel.ALLTAG)),
            emptyList(),
        )

        DeleteRecipeUseCase(recipes, planRepository)(eigenes.id)

        assertNotNull(recipes.getRecipe(eigenes.id))
        assertTrue(recipes.visible(RecipeFilter()).isEmpty())

        // Steht es nicht mehr im Plan, ist es beim naechsten Aufraeumen ganz weg.
        planRepository.savePlan(emptyList(), emptyList())
        recipes.deleteUnusedPlanRecipes()
        assertNull(recipes.getRecipe(eigenes.id))
    }

    @Test
    fun `auf die Einkaufsliste kommt ein Rezept in der Menge fuer den eigenen Haushalt`() = runTest {
        val aufDieListe = AddRecipesToShoppingListUseCase(recipes, shopping, ShoppingListAggregator())

        aufDieListe(listOf(eigenes.id), persons = 2)

        // Das Rezept ist fuer vier gedacht - fuer zwei braucht es die Haelfte.
        val hackfleisch = shopping.getItems().first { it.name == "Hackfleisch" }
        assertEquals(400.0, hackfleisch.amount)
        assertEquals(IngredientUnit.GRAMM, hackfleisch.unit)
    }

    @Test
    fun `ohne Personenangabe kommen die Mengen so auf die Liste, wie das Rezept sie nennt`() = runTest {
        val aufDieListe = AddRecipesToShoppingListUseCase(recipes, shopping, ShoppingListAggregator())

        aufDieListe(listOf(eigenes.id))

        assertEquals(800.0, shopping.getItems().first { it.name == "Hackfleisch" }.amount)
    }
}
