package ch.rezeptli.app.presentation.cooking

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.steps.RecipeStep
import ch.rezeptli.app.domain.steps.StepIngredientMatcher
import ch.rezeptli.app.domain.timer.CookingTimer
import ch.rezeptli.app.domain.usecase.MarkRecipeCookedUseCase
import ch.rezeptli.app.domain.usecase.ObserveRecipeUseCase
import ch.rezeptli.app.fake.FakePlanMemory
import ch.rezeptli.app.fake.FakePlanRepository
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.presentation.navigation.Destinations
import ch.rezeptli.app.util.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Der Kochmodus rund um den Timer: stellen, nachstellen, beim Blaettern im Blick behalten -
 * und nicht stillschweigend verlieren, wenn der Kochmodus geschlossen wird.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CookingViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val risotto = Recipe(
        id = 7L,
        title = "Risotto",
        instructions = "Zwiebeln hacken. 20 Minuten köcheln. Anrichten.",
        steps = listOf(
            RecipeStep(id = 1L, recipeId = 7L, position = 0, text = "Zwiebeln hacken."),
            RecipeStep(id = 2L, recipeId = 7L, position = 1, text = "20 Minuten köcheln.", timerMinutes = 20),
            RecipeStep(id = 3L, recipeId = 7L, position = 2, text = "1 Minute ruhen lassen.", timerMinutes = 1),
            RecipeStep(id = 4L, recipeId = 7L, position = 3, text = "Anrichten."),
        ),
    )

    private val recipes = FakeRecipeRepository(listOf(risotto))
    private lateinit var timer: CookingTimer

    private fun TestScope.createViewModel(): CookingViewModel {
        timer = CookingTimer(backgroundScope) { testScheduler.currentTime }
        return CookingViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Destinations.ARG_RECIPE_ID to risotto.id)),
            observeRecipe = ObserveRecipeUseCase(recipes),
            markCooked = MarkRecipeCookedUseCase(recipes, FakePlanRepository(), FakePlanMemory()),
            ingredientMatcher = StepIngredientMatcher(),
            cookingTimer = timer,
        ).also { runCurrent() }
    }

    private fun TestScope.warte(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private val CookingViewModel.state get() = uiState.value

    @Test
    fun `der Vorschlag aus dem Rezept laesst sich vor dem Start verstellen`() = runTest {
        val viewModel = createViewModel()
        viewModel.onNextStep()
        val schritt = viewModel.state.currentStep!!
        assertEquals(20, viewModel.state.minutesFor(schritt))

        viewModel.onPlannedMinutesChange(+1)
        viewModel.onPlannedMinutesChange(+1)
        viewModel.onPlannedMinutesChange(-1)
        assertEquals(21, viewModel.state.minutesFor(schritt))
        // Verstellt ist nur dieser Schritt - und noch laeuft nichts.
        assertEquals(1, viewModel.state.minutesFor(viewModel.state.steps[2]))
        assertNull(viewModel.state.timer)

        viewModel.onStartTimer()
        runCurrent()

        val laeuft = viewModel.state.timerForCurrentStep!!
        assertEquals(21 * 60, laeuft.totalSeconds)
        assertEquals(21 * 60, laeuft.remainingSeconds)
        assertEquals("Risotto", laeuft.title)
    }

    @Test
    fun `kuerzer als eine Minute laesst sich kein Timer stellen`() = runTest {
        val viewModel = createViewModel()
        viewModel.onNextStep()
        viewModel.onNextStep()

        viewModel.onPlannedMinutesChange(-1)

        assertEquals(1, viewModel.state.minutesFor(viewModel.state.currentStep!!))
    }

    @Test
    fun `ein Schritt ohne Zeitangabe bietet keinen Timer an`() = runTest {
        val viewModel = createViewModel()

        viewModel.onPlannedMinutesChange(+1)
        viewModel.onStartTimer()
        runCurrent()

        assertNull(viewModel.state.minutesFor(viewModel.state.currentStep!!))
        assertNull(viewModel.state.timer)
    }

    @Test
    fun `der laufende Timer laesst sich um Minuten verschieben`() = runTest {
        val viewModel = createViewModel()
        viewModel.onNextStep()
        viewModel.onStartTimer()
        warte(30_000)
        assertEquals(20 * 60 - 30, viewModel.state.timer!!.remainingSeconds)

        viewModel.onAdjustTimer(+1)
        runCurrent()
        assertEquals(21 * 60 - 30, viewModel.state.timer!!.remainingSeconds)

        viewModel.onAdjustTimer(-1)
        viewModel.onAdjustTimer(-1)
        runCurrent()
        assertEquals(19 * 60 - 30, viewModel.state.timer!!.remainingSeconds)
    }

    @Test
    fun `ist die Zeit um, klingelt es - stoppen macht Ruhe`() = runTest {
        val viewModel = createViewModel()
        viewModel.onNextStep()
        viewModel.onNextStep()
        viewModel.onStartTimer()

        warte(60_000)
        assertTrue(viewModel.state.timer!!.isFinished)
        assertTrue(viewModel.state.timer!!.isRinging)

        viewModel.onStopTimer()
        runCurrent()
        assertNull(viewModel.state.timer)
        assertNull(timer.state.value)
    }

    @Test
    fun `noch eine Minute nach dem Klingeln laesst ihn weiterlaufen`() = runTest {
        val viewModel = createViewModel()
        viewModel.onNextStep()
        viewModel.onNextStep()
        viewModel.onStartTimer()
        warte(60_000)

        viewModel.onAdjustTimer(+1)
        runCurrent()

        assertFalse(viewModel.state.timer!!.isFinished)
        assertFalse(viewModel.state.timer!!.isRinging)
        assertEquals(60, viewModel.state.timer!!.remainingSeconds)
    }

    @Test
    fun `beim Weiterblaettern bleibt der Timer im Blick und fuehrt zurueck zu seinem Schritt`() = runTest {
        val viewModel = createViewModel()
        viewModel.onNextStep()
        viewModel.onStartTimer()
        runCurrent()
        assertNotNull(viewModel.state.timerForCurrentStep)
        assertNull(viewModel.state.timerElsewhere)

        viewModel.onNextStep()
        viewModel.onNextStep()

        assertNull(viewModel.state.timerForCurrentStep)
        assertNotNull(viewModel.state.timerElsewhere)
        assertEquals(2, viewModel.state.timerStepNumber)

        viewModel.onShowTimerStep()
        assertEquals(1, viewModel.state.stepIndex)
        assertNotNull(viewModel.state.timerForCurrentStep)
    }

    @Test
    fun `schliessen mit laufendem Timer fragt erst nach`() = runTest {
        val viewModel = createViewModel()
        viewModel.onNextStep()
        viewModel.onStartTimer()
        runCurrent()

        viewModel.events.test {
            viewModel.onCloseRequest()
            runCurrent()
            assertEquals(CookingExit.CLOSE, viewModel.state.pendingExit)
            expectNoEvents()

            // "Weiterkochen": Es bleibt alles, wie es war.
            viewModel.onExitDismiss()
            assertNull(viewModel.state.pendingExit)
            assertNotNull(timer.state.value)
            expectNoEvents()

            viewModel.onCloseRequest()
            viewModel.onExitConfirm()
            runCurrent()
            assertEquals(CookingEvent.Finished, awaitItem())
            assertNull(timer.state.value)
            assertNull(viewModel.state.pendingExit)
        }
        // Geschlossen ist nicht gekocht.
        assertTrue(recipes.cookedIds.isEmpty())
    }

    @Test
    fun `fertig gekocht mit laufendem Timer fragt auch - und vermerkt es danach`() = runTest {
        val viewModel = createViewModel()
        viewModel.onNextStep()
        viewModel.onStartTimer()
        runCurrent()

        viewModel.events.test {
            viewModel.onFinish()
            runCurrent()
            assertEquals(CookingExit.FINISH, viewModel.state.pendingExit)
            assertTrue(recipes.cookedIds.isEmpty())
            expectNoEvents()

            viewModel.onExitConfirm()
            runCurrent()
            assertEquals(CookingEvent.Finished, awaitItem())
        }
        assertEquals(listOf(risotto.id), recipes.cookedIds)
        assertNull(timer.state.value)
    }

    @Test
    fun `ohne Timer und mit abgelaufenem Timer schliesst es ohne Rueckfrage`() = runTest {
        val viewModel = createViewModel()

        viewModel.events.test {
            viewModel.onFinish()
            runCurrent()
            assertEquals(CookingEvent.Finished, awaitItem())
            assertNull(viewModel.state.pendingExit)
        }
        assertEquals(listOf(risotto.id), recipes.cookedIds)

        val zweites = createViewModel()
        zweites.onNextStep()
        zweites.onNextStep()
        zweites.onStartTimer()
        warte(60_000)
        assertTrue(zweites.state.timer!!.isRinging)

        zweites.events.test {
            zweites.onCloseRequest()
            runCurrent()
            assertEquals(CookingEvent.Finished, awaitItem())
        }
        // Mit dem Kochmodus endet auch das Klingeln.
        assertNull(timer.state.value)
    }

    @Test
    fun `der Timer eines anderen Rezepts ist hier weder zu sehen noch zu stoppen`() = runTest {
        val viewModel = createViewModel()
        timer.start(recipeId = 99L, stepPosition = 1, title = "Zopf", minutes = 30)
        runCurrent()

        assertNull(viewModel.state.timer)

        viewModel.onAdjustTimer(+1)
        viewModel.onStopTimer()
        runCurrent()

        assertEquals(99L, timer.state.value?.recipeId)
        assertEquals(30 * 60, timer.state.value?.remainingSeconds)

        // Auch schliessen laesst ihn in Ruhe - und fragt nicht nach.
        viewModel.events.test {
            viewModel.onCloseRequest()
            runCurrent()
            assertEquals(CookingEvent.Finished, awaitItem())
        }
        assertEquals(99L, timer.state.value?.recipeId)
    }
}
