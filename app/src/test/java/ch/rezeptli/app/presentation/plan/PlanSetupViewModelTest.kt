package ch.rezeptli.app.presentation.plan

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

    private val repository = FakeUserProfileRepository()

    private fun createViewModel() = PlanSetupViewModel(repository)

    @Test
    fun `beginnt beim Namen und ist vorbelegt`() {
        val state = createViewModel().uiState.value

        assertEquals(SetupStep.NAME, state.step)
        assertEquals(2, state.profile.persons)
        assertEquals(UserProfile.DEFAULT_COOK_DAYS, state.profile.cookDays.size)
        assertEquals(setOf(Appliance.HERD, Appliance.BACKOFEN), state.profile.appliances)
    }

    @Test
    fun `fragt nach Name, Land und Geschmack, bevor es um den Plan geht`() {
        // Genau diese drei Fragen fehlten: Name, wo man kocht, was man gern isst.
        assertEquals(
            listOf(SetupStep.NAME, SetupStep.LAND, SetupStep.KUECHEN, SetupStep.ERNAEHRUNG, SetupStep.ABNEIGUNGEN),
            SetupStep.entries.take(5),
        )
        // Die Laeden kommen nach dem Land - sie haengen davon ab.
        assertTrue(SetupStep.LAND.ordinal < SetupStep.LADEN.ordinal)
    }

    @Test
    fun `fuehrt durch alle Schritte und zurueck`() {
        val viewModel = createViewModel()

        repeat(SetupStep.entries.size - 1) { viewModel.onNext() }
        assertTrue(viewModel.uiState.value.step.isLast)
        assertEquals(SetupStep.GERAETE, viewModel.uiState.value.step)

        viewModel.onBack()
        assertEquals(SetupStep.STIMMUNG, viewModel.uiState.value.step)
    }

    @Test
    fun `speichert am Ende jede Antwort`() = runTest {
        val viewModel = createViewModel()

        viewModel.onNameChange("  Martina ")
        viewModel.onCountrySelect(Country.SCHWEIZ)
        viewModel.onCuisineToggle(Cuisine.ITALIENISCH)
        viewModel.onMainDiet(Diet.VEGETARISCH)
        viewModel.onIntoleranceToggle(Intolerance.NUESSE)
        viewModel.onDislikeToggle(Dislike.PILZE)
        viewModel.onCustomDislikeAdd("Rosinen")
        viewModel.onPersonsChange(+2)
        viewModel.onDayToggle(Weekday.SAMSTAG)
        viewModel.onSlotToggle(MealSlot.MITTAG)
        viewModel.onStoreToggle(StoreChain.ALDI)
        viewModel.onStoreToggle(StoreChain.COOP)
        viewModel.onBudgetChange(150)
        viewModel.onMoodToggle(Mood.SCHNELL)
        viewModel.onApplianceToggle(Appliance.DAMPFGARER)
        repeat(SetupStep.entries.size) { viewModel.onNext() }
        advanceUntilIdle()

        val stored = repository.current
        assertEquals("Martina", stored.firstName)
        assertEquals(Country.SCHWEIZ, stored.country)
        assertEquals(setOf(Cuisine.ITALIENISCH), stored.cuisines)
        assertEquals(setOf(Diet.VEGETARISCH), stored.diets)
        assertEquals(setOf(Intolerance.NUESSE), stored.intolerances)
        assertEquals(setOf(Dislike.PILZE), stored.dislikes)
        assertEquals(listOf("Rosinen"), stored.customDislikes)
        assertEquals(4, stored.householdSize)
        assertTrue(Weekday.SAMSTAG in stored.cookDays)
        assertEquals(listOf(MealSlot.ABEND), stored.slots)
        assertEquals(listOf(StoreChain.ALDI, StoreChain.COOP), stored.stores)
        assertEquals(StoreChain.ALDI, stored.store)
        assertEquals(150, stored.weeklyBudget)
        assertEquals(setOf(Mood.SCHNELL), stored.moods)
        assertEquals(setOf(Appliance.HERD, Appliance.BACKOFEN, Appliance.DAMPFGARER), stored.appliances)
        assertTrue(stored.onboardingCompleted)
    }

    @Test
    fun `die Laeden folgen dem gewaehlten Land`() {
        val viewModel = createViewModel()

        viewModel.onCountrySelect(Country.SCHWEIZ)
        viewModel.onStoreToggle(StoreChain.MIGROS)
        assertEquals(listOf(StoreChain.MIGROS), viewModel.uiState.value.profile.stores)

        // Wer sich beim Land umentscheidet, behaelt keinen Laden, den es dort nicht gibt.
        viewModel.onCountrySelect(Country.DEUTSCHLAND)
        val profil = viewModel.uiState.value.profile
        assertTrue(profil.stores.isEmpty())
        assertTrue(StoreChain.DE_REWE in profil.market.stores)
        assertFalse(StoreChain.MIGROS in profil.market.stores)

        viewModel.onStoreToggle(StoreChain.MIGROS)
        assertTrue(
            viewModel.uiState.value.profile.stores
                .isEmpty(),
        )
        viewModel.onStoreToggle(StoreChain.DE_REWE)
        assertEquals(listOf(StoreChain.DE_REWE), viewModel.uiState.value.profile.stores)
    }

    @Test
    fun `ohne Abschluss wird nichts gespeichert`() = runTest {
        val viewModel = createViewModel()

        viewModel.onStoreToggle(StoreChain.LIDL)
        viewModel.onNext()
        advanceUntilIdle()

        assertFalse(repository.current.onboardingCompleted)
        assertTrue(repository.current.stores.isEmpty())
    }
}
