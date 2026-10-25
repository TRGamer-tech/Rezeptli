package ch.rezeptli.app.presentation.plan

import ch.rezeptli.app.domain.profile.Appliance
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Mood
import ch.rezeptli.app.domain.profile.StoreChain
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.fake.FakeUserProfileRepository
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
class PlanSetupViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repository = FakeUserProfileRepository(UserProfile(firstName = "Martina"))

    private fun createViewModel() = PlanSetupViewModel(repository)

    @Test
    fun `beginnt bei der Postleitzahl und ist vorbelegt`() {
        val state = createViewModel().uiState.value

        assertEquals(SetupStep.POSTLEITZAHL, state.step)
        assertEquals(2, state.profile.persons)
        assertEquals(UserProfile.DEFAULT_COOK_DAYS, state.profile.cookDays.size)
        assertEquals(setOf(Appliance.HERD, Appliance.BACKOFEN), state.profile.appliances)
    }

    @Test
    fun `fuehrt durch alle acht Schritte und zurueck`() {
        val viewModel = createViewModel()

        repeat(SetupStep.entries.size - 1) { viewModel.onNext() }
        assertTrue(viewModel.uiState.value.step.isLast)

        viewModel.onBack()
        assertEquals(SetupStep.ERNAEHRUNG, viewModel.uiState.value.step)
    }

    @Test
    fun `speichert die Antworten am Ende und behaelt den Namen`() = runTest {
        val viewModel = createViewModel()

        viewModel.onZipChange("8001")
        viewModel.onStoreSelect(StoreChain.ALDI)
        viewModel.onPersonsChange(+2)
        viewModel.onDayToggle(Weekday.SAMSTAG)
        viewModel.onBudgetChange(150)
        viewModel.onMoodToggle(Mood.SCHNELL)
        viewModel.onMainDiet(Diet.VEGETARISCH)
        repeat(SetupStep.entries.size) { viewModel.onNext() }
        advanceUntilIdle()

        val stored = repository.current
        assertEquals("Martina", stored.firstName)
        assertEquals("8001", stored.zip)
        assertEquals(StoreChain.ALDI, stored.store)
        assertEquals(4, stored.householdSize)
        assertTrue(Weekday.SAMSTAG in stored.cookDays)
        assertEquals(150, stored.weeklyBudget)
        assertEquals(setOf(Mood.SCHNELL), stored.moods)
        assertEquals(setOf(Diet.VEGETARISCH), stored.diets)
        assertTrue(stored.onboardingCompleted)
    }

    @Test
    fun `ohne Abschluss wird nichts gespeichert`() = runTest {
        val viewModel = createViewModel()

        viewModel.onStoreSelect(StoreChain.LIDL)
        viewModel.onNext()
        advanceUntilIdle()

        assertFalse(repository.current.onboardingCompleted)
    }
}
