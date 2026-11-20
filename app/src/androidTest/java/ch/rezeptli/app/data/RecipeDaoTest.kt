package ch.rezeptli.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ch.rezeptli.app.data.local.RezeptliDatabase
import ch.rezeptli.app.data.local.dao.RecipeDao
import ch.rezeptli.app.data.local.entity.IngredientEntity
import ch.rezeptli.app.data.local.entity.PlanEntryEntity
import ch.rezeptli.app.data.local.entity.RecipeEntity
import ch.rezeptli.app.data.local.entity.RecipeIdentity
import ch.rezeptli.app.data.local.entity.RecipeStepEntity
import ch.rezeptli.app.data.mapper.toDomain
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.Weekday
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Prueft die Abfragen, die sich nicht sinnvoll ohne echtes SQLite testen lassen. */
@RunWith(AndroidJUnit4::class)
class RecipeDaoTest {
    private lateinit var database: RezeptliDatabase
    private lateinit var dao: RecipeDao

    @Before
    fun setUp() {
        database = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext<Context>(),
                RezeptliDatabase::class.java,
            ).build()
        dao = database.recipeDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertRecipe(
        title: String,
        prepTimeMinutes: Int? = null,
        tags: List<String> = emptyList(),
        ingredients: List<String> = emptyList(),
        steps: List<String> = emptyList(),
        lastCookedAt: Long? = null,
        sourceUrl: String? = null,
        inCollection: Boolean = true,
    ): Long = dao.upsertRecipe(
        recipe = RecipeEntity(
            title = title,
            instructions = "",
            prepTimeMinutes = prepTimeMinutes,
            photoUri = null,
            createdAt = 0L,
            updatedAt = 0L,
            lastCookedAt = lastCookedAt,
            sourceUrl = sourceUrl,
            inCollection = inCollection,
        ),
        ingredients = ingredients.map { name ->
            IngredientEntity(
                recipeId = 0L,
                name = name,
                amount = null,
                unit = IngredientUnit.NONE,
                note = null,
                canonicalName = null,
                position = 0,
            )
        },
        tags = tags,
        steps = steps.mapIndexed { index, text ->
            RecipeStepEntity(recipeId = 0L, position = index, text = text, timerMinutes = null)
        },
    )

    @Test
    fun merktSichWasDieQuelleAnNaehrwertenNennt() = runTest {
        val mitAngabe = dao.upsertRecipe(
            recipe = RecipeEntity(
                title = "Risotto",
                instructions = "",
                prepTimeMinutes = null,
                photoUri = null,
                createdAt = 0L,
                updatedAt = 0L,
                lastCookedAt = null,
                servings = 4,
                kcalPerServing = 512,
                proteinPerServing = 23.5,
            ),
            ingredients = emptyList(),
            tags = emptyList(),
            steps = emptyList(),
        )
        val ohneAngabe = insertRecipe(title = "Omas Gratin")

        val risotto = dao.recipeWithDetails(mitAngabe)!!.toDomain()
        assertEquals(512, risotto.kcalPerServing)
        assertEquals(23.5, risotto.proteinPerServing!!, 0.001)
        assertEquals(4, risotto.servings)

        // Ein selbst erfasstes Rezept hat keine Angabe - dafuer schaetzt die App.
        val gratin = dao.recipeWithDetails(ohneAngabe)!!.toDomain()
        assertNull(gratin.kcalPerServing)
        assertNull(gratin.proteinPerServing)
    }

    @Test
    fun speichertRezeptMitZutatenUndTags() = runTest {
        val id = insertRecipe(
            title = "Älplermagronen",
            prepTimeMinutes = 40,
            tags = listOf("vegetarisch", "schnell"),
            ingredients = listOf("Hörnli", "Rahm"),
        )

        val stored = dao.recipeWithDetails(id)
        assertEquals("Älplermagronen", stored?.recipe?.title)
        assertEquals(listOf("Hörnli", "Rahm"), stored?.ingredients?.sortedBy { it.position }?.map { it.name })
        assertEquals(setOf("vegetarisch", "schnell"), stored?.tags?.map { it.tag }?.toSet())
    }

    @Test
    fun ersetztZutatenBeimAktualisieren() = runTest {
        val id = insertRecipe(title = "Suppe", ingredients = listOf("Bouillon", "Rüebli"))

        dao.upsertRecipe(
            recipe = dao.recipeWithDetails(id)!!.recipe,
            ingredients = listOf(
                IngredientEntity(
                    recipeId = id,
                    name = "Lauch",
                    amount = 1.0,
                    unit = IngredientUnit.STUECK,
                    note = null,
                    canonicalName = null,
                    position = 0,
                ),
            ),
            tags = emptyList(),
            steps = listOf(
                RecipeStepEntity(recipeId = id, position = 0, text = "Lauch schneiden.", timerMinutes = null),
            ),
        )

        val stored = dao.recipeWithDetails(id)
        assertEquals(listOf("Lauch"), stored?.ingredients?.map { it.name })
        assertEquals(listOf("Lauch schneiden."), stored?.steps?.map { it.text })
    }

    @Test
    fun ersetztSchritteBeimAktualisieren() = runTest {
        val id = insertRecipe(
            title = "Rösti",
            steps = listOf("Kartoffeln raffeln.", "Braten."),
        )
        assertEquals(2, dao.recipeWithDetails(id)?.steps?.size)

        dao.upsertRecipe(
            recipe = dao.recipeWithDetails(id)!!.recipe,
            ingredients = emptyList(),
            tags = emptyList(),
            steps = listOf(
                RecipeStepEntity(recipeId = id, position = 0, text = "Alles zusammen.", timerMinutes = 15),
            ),
        )

        val stored = dao.recipeWithDetails(id)
        assertEquals(listOf("Alles zusammen."), stored?.steps?.map { it.text })
        assertEquals(15, stored?.steps?.first()?.timerMinutes)
    }

    @Test
    fun findetRezepteUeberZutatennamen() = runTest {
        insertRecipe(title = "Älplermagronen", ingredients = listOf("Hörnli"))
        insertRecipe(title = "Rösti", ingredients = listOf("Kartoffeln"))

        val ids = dao.filteredRecipeIds(
            query = "Kartoffeln",
            tags = emptyList(),
            tagCount = 0,
            maxPrepTimeMinutes = null,
            cooked = null,
        )

        assertEquals(1, ids.size)
        assertEquals("Rösti", dao.recipeWithDetails(ids.single())?.recipe?.title)
    }

    @Test
    fun filtertNachMehrerenTagsMitUndVerknuepfung() = runTest {
        insertRecipe(title = "Gemüsecurry", tags = listOf("vegan", "schnell"))
        insertRecipe(title = "Salat", tags = listOf("vegan"))

        val ids = dao.filteredRecipeIds(
            query = "",
            tags = listOf("vegan", "schnell"),
            tagCount = 2,
            maxPrepTimeMinutes = null,
            cooked = null,
        )

        assertEquals(1, ids.size)
        assertEquals("Gemüsecurry", dao.recipeWithDetails(ids.single())?.recipe?.title)
    }

    @Test
    fun filtertNachZubereitungszeit() = runTest {
        insertRecipe(title = "Rührei", prepTimeMinutes = 10)
        insertRecipe(title = "Schmorbraten", prepTimeMinutes = 150)
        insertRecipe(title = "Ohne Zeitangabe")

        val ids = dao.filteredRecipeIds(
            query = "",
            tags = emptyList(),
            tagCount = 0,
            maxPrepTimeMinutes = 30,
            cooked = null,
        )

        assertEquals(1, ids.size)
        assertEquals("Rührei", dao.recipeWithDetails(ids.single())?.recipe?.title)
    }

    private suspend fun titel(cooked: Boolean?): List<String> = dao
        .filteredRecipeIds(query = "", tags = emptyList(), tagCount = 0, maxPrepTimeMinutes = null, cooked = cooked)
        .mapNotNull { dao.recipeWithDetails(it)?.recipe?.title }
        .sorted()

    @Test
    fun filtertNachGekochtUndNochNieGekocht() = runTest {
        insertRecipe(title = "Schon gekocht", lastCookedAt = 1_700_000_000_000L)
        insertRecipe(title = "Noch nie gekocht")

        assertEquals(listOf("Schon gekocht"), titel(cooked = true))
        assertEquals(listOf("Noch nie gekocht"), titel(cooked = false))
        assertEquals(listOf("Noch nie gekocht", "Schon gekocht"), titel(cooked = null))
    }

    @Test
    fun derVermerkGekochtLaesstSichSetzenUndZuruecknehmen() = runTest {
        val id = insertRecipe(title = "Rösti")

        dao.updateLastCookedAt(id, 42L)
        assertEquals(42L, dao.recipeWithDetails(id)?.recipe?.lastCookedAt)
        assertEquals(listOf("Rösti"), titel(cooked = true))

        dao.updateLastCookedAt(id, null)
        assertNull(dao.recipeWithDetails(id)?.recipe?.lastCookedAt)
        assertEquals(listOf("Rösti"), titel(cooked = false))
    }

    @Test
    fun dieMeistbenutztenSchlagworteStehenVorn() = runTest {
        insertRecipe(title = "Eins", tags = listOf("Suppe", "vegan", "Betty Bossi"))
        insertRecipe(title = "Zwei", tags = listOf("vegan", "Betty Bossi"))
        insertRecipe(title = "Drei", tags = listOf("Betty Bossi", "asiatisch"))

        // Dreimal, zweimal, je einmal - bei Gleichstand nach dem Alphabet, ohne Ruecksicht auf Grossschreibung.
        assertEquals(listOf("Betty Bossi", "vegan", "asiatisch", "Suppe"), dao.observeAllTags().first())
    }

    @Test
    fun wasNurFuerDenPlanGespeichertIstGehoertNichtZurSammlung() = runTest {
        insertRecipe(title = "Meins", tags = listOf("vegetarisch"), sourceUrl = "https://beispiel.ch/meins")
        val planId = insertRecipe(
            title = "Nur im Plan",
            tags = listOf("asiatisch"),
            sourceUrl = "https://beispiel.ch/plan",
            inCollection = false,
        )

        // Weder in der Liste noch in der Anzahl, bei den Tags oder unter den gespeicherten Adressen.
        assertEquals(listOf("Meins"), titel(cooked = null))
        assertEquals(1, dao.observeRecipeCount().first())
        assertEquals(listOf("vegetarisch"), dao.observeAllTags().first())
        assertEquals(listOf("https://beispiel.ch/meins"), dao.savedSourceUrls())
        // Gespeichert ist es trotzdem - der Plan muss es ja zeigen und kochen koennen.
        assertEquals("Nur im Plan", dao.recipeWithDetails(planId)?.recipe?.title)

        dao.setInCollection(planId, inCollection = true, now = 5L)

        assertEquals(listOf("Meins", "Nur im Plan"), titel(cooked = null))
        assertEquals(2, dao.observeRecipeCount().first())
    }

    @Test
    fun zumWiedererkennenNenntDieDatenbankAdresseTitelUndSammlung() = runTest {
        val url = "https://beispiel.ch/roesti"
        val imPlan = insertRecipe(title = "Rösti (Plan)", sourceUrl = url, inCollection = false)
        val gespeichert = insertRecipe(title = "Rösti", sourceUrl = url)
        val eigenes = insertRecipe(title = "Omas Gratin")

        // Alle Rezepte, auch die nur fuer den Plan gespeicherten - verglichen wird im Code.
        assertEquals(
            listOf(
                RecipeIdentity(imPlan, "Rösti (Plan)", url, inCollection = false),
                RecipeIdentity(gespeichert, "Rösti", url, inCollection = true),
                RecipeIdentity(eigenes, "Omas Gratin", null, inCollection = true),
            ),
            dao.identities().sortedBy { it.id },
        )
    }

    @Test
    fun raeumtNurPlanrezepteWegDieNichtMehrImPlanStehen() = runTest {
        val meins = insertRecipe(title = "Meins")
        val imPlan = insertRecipe(title = "Im Plan", inCollection = false)
        val verwaist = insertRecipe(title = "Aus dem Plan gefallen", inCollection = false)
        database.planDao().replacePlan(
            entries = listOf(
                PlanEntryEntity(
                    day = Weekday.MONTAG,
                    slot = MealSlot.ABEND,
                    recipeId = imPlan,
                    servings = 2,
                    cost = 8.0,
                    label = "ALLTAG",
                ),
            ),
            items = emptyList(),
        )

        dao.deleteUnusedPlanRecipes()

        assertEquals("Meins", dao.recipeWithDetails(meins)?.recipe?.title)
        assertEquals("Im Plan", dao.recipeWithDetails(imPlan)?.recipe?.title)
        assertNull(dao.recipeWithDetails(verwaist))
    }

    @Test
    fun derPlanHatProTagEinMittagUndEinAbendessenUndMerktSichGekochtes() = runTest {
        val roesti = insertRecipe(title = "Rösti")
        val risotto = insertRecipe(title = "Risotto")
        val curry = insertRecipe(title = "Curry")
        val plan = database.planDao()

        fun eintrag(slot: MealSlot, recipeId: Long, cookedAt: Long? = null) = PlanEntryEntity(
            day = Weekday.MONTAG,
            slot = slot,
            recipeId = recipeId,
            servings = 2,
            cost = 8.0,
            label = "ALLTAG",
            cookedAt = cookedAt,
        )
        plan.replacePlan(listOf(eintrag(MealSlot.MITTAG, roesti), eintrag(MealSlot.ABEND, risotto)), emptyList())

        assertEquals(setOf(MealSlot.MITTAG, MealSlot.ABEND), plan.rows().map { it.slot }.toSet())

        // Gekocht wird am Rezept vermerkt - die Mahlzeit im Plan ist damit abgehakt.
        plan.markCooked(roesti, 99L)
        assertEquals(99L, plan.rows().first { it.recipeId == roesti }.cookedAt)
        assertNull(plan.rows().first { it.recipeId == risotto }.cookedAt)

        // Getauscht wird genau ein Platz: Das Mittagessen bleibt, samt seinem Vermerk.
        plan.replaceEntry(eintrag(MealSlot.ABEND, curry), emptyList())
        val danach = plan.rows()
        assertEquals(2, danach.size)
        assertEquals(curry, danach.first { it.slot == MealSlot.ABEND }.recipeId)
        assertEquals(99L, danach.first { it.slot == MealSlot.MITTAG }.cookedAt)
    }

    @Test
    fun loeschtZutatenUndTagsMitDemRezept() = runTest {
        val id = insertRecipe(title = "Rösti", tags = listOf("vegetarisch"), ingredients = listOf("Kartoffeln"))

        dao.deleteRecipe(id)

        assertNull(dao.recipeWithDetails(id))
        val summaries = dao.summariesByIds(listOf(id))
        assertTrue(summaries.isEmpty())
    }
}
