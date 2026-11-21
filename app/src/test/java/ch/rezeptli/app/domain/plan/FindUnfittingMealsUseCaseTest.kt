package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.fake.FakeRecipeRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Der Plan nach einer Aenderung der Vorlieben.
 *
 * Gemeldet war: Pilzgerichte trotz "keine Pilze". Ein Plan, der vor der Abneigung gebaut wurde,
 * behaelt seine Gerichte - mit Absicht. Dann muss aber dastehen, welche nicht mehr passen.
 */
class FindUnfittingMealsUseCaseTest {
    private fun rezept(id: Long, titel: String, vararg zutaten: String) = Recipe(
        id = id,
        title = titel,
        ingredients = zutaten.map { Ingredient(name = it) },
        inCollection = false,
    )

    private val rezepte = FakeRecipeRepository(
        listOf(
            rezept(1L, "Risotto", "Risottoreis", "braune Champignons", "Weisswein"),
            rezept(2L, "Lachs mit Reis", "Lachsfilet", "Reis"),
            rezept(3L, "Rösti", "Kartoffeln", "Bratbutter"),
            rezept(4L, "Rahmschnitzel", "Schweinsschnitzel", "Rahm"),
        ),
    )

    private fun mahlzeit(tag: Weekday, rezept: Long, gekocht: Boolean = false, mittags: Boolean = false) = PlanEntry(
        day = tag,
        slot = if (mittags) MealSlot.MITTAG else MealSlot.ABEND,
        recipeId = rezept,
        title = "Rezept $rezept",
        photoUri = null,
        prepTimeMinutes = 30,
        servings = 2,
        cost = 8.0,
        label = MealLabel.ALLTAG,
        cookedAt = if (gekocht) 1_700_000_000_000L else null,
    )

    private val woche = listOf(
        mahlzeit(Weekday.MONTAG, 1L),
        mahlzeit(Weekday.DIENSTAG, 2L),
        mahlzeit(Weekday.MITTWOCH, 3L),
        mahlzeit(Weekday.DONNERSTAG, 4L),
    )

    private val finden = FindUnfittingMealsUseCase(rezepte, RecipeClassifier())

    @Test
    fun `ohne Einschraenkungen passt alles`() = runTest {
        assertTrue(finden(woche, UserProfile.EMPTY).isEmpty())
    }

    @Test
    fun `eine neue Abneigung trifft genau die Mahlzeiten, die sie enthalten`() = runTest {
        val unpassend = finden(woche, UserProfile(dislikes = setOf(Dislike.PILZE)))

        assertEquals(setOf(PlanPlace(Weekday.MONTAG, MealSlot.ABEND)), unpassend)
    }

    @Test
    fun `Ernaehrung und eigene Stichworte zaehlen genauso`() = runTest {
        val vegetarisch = finden(woche, UserProfile(diets = setOf(Diet.VEGETARISCH)))
        val ohneRahm = finden(woche, UserProfile(customDislikes = listOf("Rahm")))

        assertEquals(
            setOf(PlanPlace(Weekday.DIENSTAG, MealSlot.ABEND), PlanPlace(Weekday.DONNERSTAG, MealSlot.ABEND)),
            vegetarisch,
        )
        assertEquals(setOf(PlanPlace(Weekday.DONNERSTAG, MealSlot.ABEND)), ohneRahm)
    }

    @Test
    fun `was schon gekocht ist, wird nicht mehr beanstandet`() = runTest {
        val gegessen = listOf(mahlzeit(Weekday.MONTAG, 1L, gekocht = true), mahlzeit(Weekday.DIENSTAG, 1L))

        val unpassend = finden(gegessen, UserProfile(dislikes = setOf(Dislike.PILZE)))

        assertEquals(setOf(PlanPlace(Weekday.DIENSTAG, MealSlot.ABEND)), unpassend)
    }

    @Test
    fun `mittags und abends sind zwei Plaetze`() = runTest {
        val tag = listOf(mahlzeit(Weekday.MONTAG, 1L, mittags = true), mahlzeit(Weekday.MONTAG, 3L))

        val unpassend = finden(tag, UserProfile(dislikes = setOf(Dislike.PILZE)))

        assertEquals(setOf(PlanPlace(Weekday.MONTAG, MealSlot.MITTAG)), unpassend)
    }

    @Test
    fun `eine Mahlzeit, deren Rezept fehlt, bleibt unbeanstandet`() = runTest {
        val verwaist = listOf(mahlzeit(Weekday.MONTAG, 99L))

        assertTrue(finden(verwaist, UserProfile(dislikes = setOf(Dislike.PILZE))).isEmpty())
    }
}
