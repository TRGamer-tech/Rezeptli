package ch.rezeptli.app.presentation.swipe

import androidx.lifecycle.SavedStateHandle
import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.nutrition.NutritionBasis
import ch.rezeptli.app.domain.nutrition.NutritionValue
import ch.rezeptli.app.domain.swipe.SwipeMatcher
import ch.rezeptli.app.domain.usecase.FinishSwipeSessionUseCase
import ch.rezeptli.app.domain.usecase.RecordSwipeUseCase
import ch.rezeptli.app.domain.usecase.StartSwipeSessionUseCase
import ch.rezeptli.app.domain.usecase.UndoLastSwipeUseCase
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.fake.FakeSwipeSessionRepository
import ch.rezeptli.app.util.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class SwipeViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val recipeRepository = FakeRecipeRepository(
        listOf(
            Recipe(id = 1L, title = "Älplermagronen"),
            // Von einer Rezeptseite, die ihre Naehrwerte nennt.
            Recipe(
                id = 2L,
                title = "Zürcher Geschnetzeltes",
                ingredients = listOf(Ingredient(name = "Kalbfleisch", amount = 500.0, unit = IngredientUnit.GRAMM)),
                servings = 4,
                kcalPerServing = 430,
                proteinPerServing = 31.0,
                sourceName = "Beispiel",
            ),
            // Selbst erfasst: Hier kann die App nur schaetzen.
            Recipe(
                id = 3L,
                title = "Rösti",
                ingredients = listOf(
                    Ingredient(name = "Kartoffeln", amount = 800.0, unit = IngredientUnit.GRAMM),
                    Ingredient(name = "Eier", amount = 4.0),
                ),
                servings = 4,
            ),
        ),
    )
    private val sessionRepository = FakeSwipeSessionRepository()
    private val matcher = SwipeMatcher()

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()) = SwipeViewModel(
        savedStateHandle = savedStateHandle,
        startSwipeSession = StartSwipeSessionUseCase(recipeRepository, sessionRepository),
        recordSwipe = RecordSwipeUseCase(sessionRepository),
        undoLastSwipe = UndoLastSwipeUseCase(sessionRepository),
        finishSwipeSession = FinishSwipeSessionUseCase(sessionRepository),
        recipeRepository = recipeRepository,
        sessionRepository = sessionRepository,
        matcher = matcher,
    )

    @Test
    fun `startet eine Session mit allen passenden Rezepten`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(3, state.totalCount)
        assertEquals(0, state.decidedCount)
        assertEquals(3, state.cards.size, "Es sollen drei Karten im Fenster liegen")
        assertFalse(state.canUndo, "Ohne Entscheidung gibt es nichts rueckgaengig zu machen")
    }

    @Test
    fun `haelt eine Entscheidung fest und ruecken die naechste Karte nach`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val firstCard = viewModel.uiState.value.topCard!!

        viewModel.onSwipe(firstCard.id, liked = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.decidedCount)
        assertTrue(state.canUndo)
        assertEquals(2, state.cards.size)
        assertTrue(state.cards.none { it.id == firstCard.id }, "Die entschiedene Karte ist weg")

        val decisions = sessionRepository.getDecisions(state.sessionId)
        assertEquals(1, decisions.size)
        assertTrue(decisions.single().liked)
    }

    @Test
    fun `nimmt die letzte Entscheidung zurueck und legt die Karte wieder obenauf`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val firstCard = viewModel.uiState.value.topCard!!

        viewModel.onSwipe(firstCard.id, liked = false)
        advanceUntilIdle()
        viewModel.onUndo()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.decidedCount)
        assertFalse(state.canUndo)
        assertEquals(firstCard.id, state.topCard?.id, "Die zurueckgenommene Karte liegt wieder oben")
        assertTrue(sessionRepository.getDecisions(state.sessionId).isEmpty())
    }

    @Test
    fun `markiert die Session als beendet wenn alle Karten entschieden sind`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        repeat(3) {
            val card = viewModel.uiState.value.topCard
            if (card != null) {
                viewModel.onSwipe(card.id, liked = true)
                advanceUntilIdle()
            }
        }

        val state = viewModel.uiState.value
        assertTrue(state.isFinished)
        assertEquals(3, state.decidedCount)
        assertTrue(state.cards.isEmpty())
        assertTrue(
            sessionRepository.getSession(state.sessionId)?.isFinished == true,
            "Die Session wird beim letzten Wisch abgeschlossen",
        )
    }

    @Test
    fun `setzt eine unterbrochene Session fort statt eine neue zu starten`() = runTest {
        val savedStateHandle = SavedStateHandle()
        val first = createViewModel(savedStateHandle)
        advanceUntilIdle()
        val sessionId = first.uiState.value.sessionId
        val firstCard = first.uiState.value.topCard!!
        first.onSwipe(firstCard.id, liked = true)
        advanceUntilIdle()

        // Prozesstod simulieren: neues ViewModel, aber derselbe SavedStateHandle.
        val restored = createViewModel(savedStateHandle)
        advanceUntilIdle()

        val state = restored.uiState.value
        assertEquals(sessionId, state.sessionId, "Es wird dieselbe Session fortgesetzt")
        assertEquals(3, state.totalCount)
        assertEquals(1, state.decidedCount)
        assertTrue(state.cards.none { it.id == firstCard.id })
    }

    @Test
    fun `antippen zeigt unter den Zutaten Kalorien und Eiweiss - von der Quelle, sonst geschaetzt`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onInfoRequest(2L)
        advanceUntilIdle()
        val vonDerQuelle = viewModel.uiState.value.info!!
        assertTrue(vonDerQuelle.showsNutrition)
        assertEquals(NutritionValue(430, estimated = false), vonDerQuelle.nutrition?.kcal)
        assertEquals(NutritionValue(31, estimated = false), vonDerQuelle.nutrition?.protein)

        viewModel.onInfoRequest(3L)
        advanceUntilIdle()
        // 616 kcal Kartoffeln und 330 kcal Eier, durch vier - gerundet auf Zehner.
        val geschaetzt = viewModel.uiState.value.info!!
            .nutrition!!
        assertEquals(NutritionValue(240, estimated = true), geschaetzt.kcal)
        assertEquals(NutritionValue(11, estimated = true), geschaetzt.protein)
        assertEquals(NutritionBasis.SERVING, geschaetzt.basis)

        // Ein Rezept ohne Zutaten: nichts zu zeigen, auch kein "liess sich nicht schaetzen".
        viewModel.onInfoRequest(1L)
        advanceUntilIdle()
        assertFalse(
            viewModel.uiState.value.info!!
                .showsNutrition,
        )
    }
}
