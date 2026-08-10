package ch.rezeptli.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ch.rezeptli.app.data.local.RezeptliDatabase
import ch.rezeptli.app.data.local.dao.RecipeDao
import ch.rezeptli.app.data.local.entity.IngredientEntity
import ch.rezeptli.app.data.local.entity.RecipeEntity
import ch.rezeptli.app.domain.model.IngredientUnit
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
    ): Long = dao.upsertRecipe(
        recipe = RecipeEntity(
            title = title,
            instructions = "",
            prepTimeMinutes = prepTimeMinutes,
            photoUri = null,
            createdAt = 0L,
            updatedAt = 0L,
            lastCookedAt = null,
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
    )

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
        )

        val stored = dao.recipeWithDetails(id)
        assertEquals(listOf("Lauch"), stored?.ingredients?.map { it.name })
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
        )

        assertEquals(1, ids.size)
        assertEquals("Rührei", dao.recipeWithDetails(ids.single())?.recipe?.title)
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
