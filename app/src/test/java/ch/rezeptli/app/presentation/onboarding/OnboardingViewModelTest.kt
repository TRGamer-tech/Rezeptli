package ch.rezeptli.app.presentation.onboarding

import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.fake.FakeUserProfileRepository
import ch.rezeptli.app.util.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repository = FakeUserProfileRepository()

    private fun createViewModel() = OnboardingViewModel(repository)

    @Test
    fun `sammelt die Antworten aller Schritte und speichert sie am Ende`() = runTest {
        val viewModel = createViewModel()

        viewModel.onFirstNameChange("Martina")
        viewModel.onCountrySelect(Country.SCHWEIZ)
        viewModel.onNext()
        viewModel.onCuisineToggle(Cuisine.ITALIENISCH)
        viewModel.onCuisineToggle(Cuisine.SCHWEIZER_KLASSIKER)
        viewModel.onNext()
        viewModel.onDietToggle(Diet.VEGETARISCH)
        viewModel.onIntoleranceToggle(Intolerance.LAKTOSE)
        viewModel.onNext()
        viewModel.onHouseholdSizeChange(3)
        viewModel.onNext()
        advanceUntilIdle()

        val stored = repository.current
        assertEquals("Martina", stored.firstName)
        assertEquals(Country.SCHWEIZ, stored.country)
        assertEquals(setOf(Cuisine.ITALIENISCH, Cuisine.SCHWEIZER_KLASSIKER), stored.cuisines)
        assertEquals(setOf(Diet.VEGETARISCH), stored.diets)
        assertEquals(setOf(Intolerance.LAKTOSE), stored.intolerances)
        assertEquals(3, stored.householdSize)
        assertTrue(stored.onboardingCompleted)
    }

    @Test
    fun `ueberspringen merkt sich, dass gefragt wurde, ohne Antworten zu erzwingen`() = runTest {
        val viewModel = createViewModel()

        viewModel.onSkip()
        advanceUntilIdle()

        val stored = repository.current
        assertTrue(stored.onboardingCompleted)
        assertEquals("", stored.firstName)
        assertNull(stored.country)
        assertTrue(stored.cuisines.isEmpty())
    }

    @Test
    fun `ueberspringen behaelt, was bereits ausgefuellt wurde`() = runTest {
        val viewModel = createViewModel()

        viewModel.onFirstNameChange("Nina")
        viewModel.onSkip()
        advanceUntilIdle()

        assertEquals("Nina", repository.current.firstName)
    }

    @Test
    fun `nochmaliges Antippen hebt eine Auswahl wieder auf`() = runTest {
        val viewModel = createViewModel()

        viewModel.onCountrySelect(Country.DEUTSCHLAND)
        viewModel.onCountrySelect(Country.DEUTSCHLAND)
        viewModel.onCuisineToggle(Cuisine.ASIATISCH)
        viewModel.onCuisineToggle(Cuisine.ASIATISCH)

        assertNull(viewModel.uiState.value.profile.country)
        assertTrue(
            viewModel.uiState.value.profile.cuisines
                .isEmpty(),
        )
    }

    @Test
    fun `zurueck fuehrt zum vorherigen Schritt und bleibt beim ersten stehen`() = runTest {
        val viewModel = createViewModel()

        viewModel.onNext()
        assertEquals(OnboardingStep.KUECHEN, viewModel.uiState.value.step)

        viewModel.onBack()
        assertEquals(OnboardingStep.WILLKOMMEN, viewModel.uiState.value.step)

        viewModel.onBack()
        assertEquals(OnboardingStep.WILLKOMMEN, viewModel.uiState.value.step)
        assertFalse(repository.current.onboardingCompleted)
    }

    @Test
    fun `eine unsinnige Haushaltsgroesse wird auf das Erlaubte begrenzt`() = runTest {
        val viewModel = createViewModel()

        viewModel.onHouseholdSizeChange(99)
        assertEquals(UserProfile.MAX_HOUSEHOLD_SIZE, viewModel.uiState.value.profile.householdSize)

        viewModel.onHouseholdSizeChange(0)
        assertEquals(1, viewModel.uiState.value.profile.householdSize)

        viewModel.onHouseholdSizeChange(null)
        assertNull(viewModel.uiState.value.profile.householdSize)
    }

    @Test
    fun `leerraum um den Vornamen wird beim Speichern entfernt`() = runTest {
        val viewModel = createViewModel()

        viewModel.onFirstNameChange("  Livia  ")
        viewModel.onSkip()
        advanceUntilIdle()

        assertEquals("Livia", repository.current.firstName)
    }
}
