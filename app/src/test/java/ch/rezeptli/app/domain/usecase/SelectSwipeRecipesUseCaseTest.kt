package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.plan.RecipeClassifier
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.fake.FakeUserProfileRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Wischen durch die eigene Sammlung - mit den Vorlieben.
 *
 * Gemeldet war: Rezepte mit Pilzen trotz "keine Pilze". Die eigene Sammlung kam ungeprueft auf
 * den Stapel; wer Rezepte einer anderen Person uebernommen hatte, bekam deren Pilzgerichte
 * vorgelegt.
 */
class SelectSwipeRecipesUseCaseTest {
    private fun rezept(id: Long, titel: String, vararg zutaten: String, tags: List<String> = emptyList()) = Recipe(
        id = id,
        title = titel,
        ingredients = zutaten.map { Ingredient(name = it) },
        tags = tags,
    )

    private val sammlung = FakeRecipeRepository(
        listOf(
            rezept(1L, "Rösti", "Kartoffeln", "Bratbutter", tags = listOf("schnell")),
            rezept(2L, "Risotto", "Risottoreis", "braune Champignons", "Weisswein"),
            rezept(3L, "Rahmschnitzel", "Schweinsschnitzel", "Schwammerl", "Rahm"),
            rezept(4L, "Lachs mit Reis", "Lachsfilet", "Reis", tags = listOf("schnell")),
            rezept(5L, "Müesli", "Haferflocken", "Rosinen", "Milch"),
        ),
    )

    private fun auswahl(profil: UserProfile) =
        SelectSwipeRecipesUseCase(sammlung, FakeUserProfileRepository(profil), RecipeClassifier())

    @Test
    fun `ohne Vorlieben kommt die ganze Sammlung auf den Stapel`() = runTest {
        val gewaehlt = auswahl(UserProfile.EMPTY)()

        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), gewaehlt.recipeIds.toSet())
        assertEquals(0, gewaehlt.hiddenByPreferences)
    }

    @Test
    fun `wer keine Pilze mag, bekommt auch aus der eigenen Sammlung keine`() = runTest {
        val gewaehlt = auswahl(UserProfile(dislikes = setOf(Dislike.PILZE)))()

        // Champignons und Schwammerl - beides Pilze, beides bleibt draussen.
        assertEquals(setOf(1L, 4L, 5L), gewaehlt.recipeIds.toSet())
        assertEquals(2, gewaehlt.hiddenByPreferences)
    }

    @Test
    fun `Ernaehrung und eigene Stichworte gelten genauso`() = runTest {
        val vegetarisch = auswahl(UserProfile(diets = setOf(Diet.VEGETARISCH)))()
        val ohneRosinen = auswahl(UserProfile(customDislikes = listOf("Rosinen")))()

        assertEquals(setOf(1L, 2L, 5L), vegetarisch.recipeIds.toSet())
        assertEquals(2, vegetarisch.hiddenByPreferences)
        assertFalse(5L in ohneRosinen.recipeIds)
        assertEquals(1, ohneRosinen.hiddenByPreferences)
    }

    @Test
    fun `gezaehlt wird, was zum Filter des Bildschirms passt - nicht die ganze Sammlung`() = runTest {
        val gewaehlt = auswahl(UserProfile(dislikes = setOf(Dislike.FISCH)))(RecipeFilter(tags = setOf("schnell")))

        assertEquals(listOf(1L), gewaehlt.recipeIds)
        assertEquals(1, gewaehlt.hiddenByPreferences)
        assertTrue(sammlung.visible(RecipeFilter()).size == 5, "die Sammlung selbst bleibt, wie sie ist")
    }
}
