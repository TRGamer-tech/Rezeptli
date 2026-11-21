package ch.rezeptli.app.presentation.swipe

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.plan.RecipeClassifier
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.usecase.ObserveTagsUseCase
import ch.rezeptli.app.domain.usecase.SelectSwipeRecipesUseCase
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.fake.FakeUserProfileRepository
import ch.rezeptli.app.util.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * "Bereit zum Swipen?" - wie viele Rezepte auf den Stapel kommen und wie viele wegen der
 * Vorlieben draussen bleiben.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwipeSetupViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private fun rezept(id: Long, titel: String, vararg zutaten: String, tags: List<String> = emptyList()) = Recipe(
        id = id,
        title = titel,
        ingredients = zutaten.map { Ingredient(name = it) },
        tags = tags,
    )

    private val sammlung = FakeRecipeRepository(
        listOf(
            rezept(1L, "Rösti", "Kartoffeln", "Bratbutter", tags = listOf("schnell")),
            rezept(2L, "Risotto", "Risottoreis", "braune Champignons", tags = listOf("schnell")),
            rezept(3L, "Rahmschnitzel", "Schweinsschnitzel", "Eierschwämmli", "Rahm"),
            rezept(4L, "Lachs mit Reis", "Lachsfilet", "Reis"),
        ),
    )

    private fun viewModel(vorlieben: FakeUserProfileRepository) = SwipeSetupViewModel(
        observeTags = ObserveTagsUseCase(sammlung),
        selectRecipes = SelectSwipeRecipesUseCase(sammlung, vorlieben, RecipeClassifier()),
    )

    @Test
    fun `zaehlt, was auf den Stapel kommt - und was wegen der Vorlieben fehlt`() = runTest {
        val viewModel = viewModel(FakeUserProfileRepository(UserProfile(dislikes = setOf(Dislike.PILZE))))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.matchingRecipeCount)
        assertEquals(2, state.hiddenByPreferences)
        assertFalse(state.isCounting)
    }

    @Test
    fun `der Filter des Bildschirms und die Vorlieben gelten zusammen`() = runTest {
        val viewModel = viewModel(FakeUserProfileRepository(UserProfile(dislikes = setOf(Dislike.PILZE))))
        advanceUntilIdle()

        viewModel.onToggleTag("schnell")
        advanceUntilIdle()

        // Zwei Rezepte tragen "schnell" - eines davon hat Pilze.
        assertEquals(1, viewModel.uiState.value.matchingRecipeCount)
        assertEquals(1, viewModel.uiState.value.hiddenByPreferences)
    }

    @Test
    fun `aendern sich die Vorlieben bei offenem Bildschirm, wird neu gezaehlt`() = runTest {
        val vorlieben = FakeUserProfileRepository(UserProfile.EMPTY)
        val viewModel = viewModel(vorlieben)
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.value.matchingRecipeCount)
        assertEquals(0, viewModel.uiState.value.hiddenByPreferences)

        vorlieben.update { it.copy(dislikes = setOf(Dislike.PILZE, Dislike.FISCH)) }
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.matchingRecipeCount)
        assertEquals(3, viewModel.uiState.value.hiddenByPreferences)
    }
}
