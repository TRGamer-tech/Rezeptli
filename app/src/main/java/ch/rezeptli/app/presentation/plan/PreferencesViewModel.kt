package ch.rezeptli.app.presentation.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.plan.AdjustPlanUseCase
import ch.rezeptli.app.domain.profile.Appliance
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.Mood
import ch.rezeptli.app.domain.profile.ProfileEdits
import ch.rezeptli.app.domain.profile.StoreChain
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.domain.repository.UserProfileRepository
import ch.rezeptli.app.domain.settings.AppearanceRepository
import ch.rezeptli.app.domain.settings.ThemeChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreferencesUiState(
    val profile: UserProfile = UserProfile.EMPTY,
    /** Die App-Einstellung - ob Android selbst Animationen entfernt hat, weiss nur die Oberflaeche. */
    val reduceMotion: Boolean = false,
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val showResetDialog: Boolean = false,
)

/**
 * Die Vorlieben: alles, was die App ueber einen weiss, auf einer Seite.
 *
 * Frueher waren es zwei Tabs - "Vorlieben" fuer den Plan und "Einstellungen" fuer Name,
 * Land und Kuechen. Die Trennung war von innen gedacht; von aussen ist es eine Frage:
 * "Was soll Rezeptli ueber mich wissen?"
 *
 * Jede Aenderung wird sofort gespeichert. Die Gerichte des bestehenden Plans bleiben dabei
 * stehen - erst "Neu bauen" waehlt mit den neuen Angaben aus, sonst wuerde das Antippen
 * einer Stimmung die Woche unter den Fuessen wegziehen. Was sich ohne neue Auswahl
 * nachfuehren laesst, folgt dagegen sofort: Mengen fuer die Personenzahl und Preise fuer
 * den Hauptladen.
 */
@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val profileRepository: UserProfileRepository,
    private val appearanceRepository: AppearanceRepository,
    private val adjustPlan: AdjustPlanUseCase,
) : ViewModel() {
    private val dialogVisible = MutableStateFlow(false)

    val uiState: StateFlow<PreferencesUiState> = combine(
        profileRepository.profile,
        appearanceRepository.reduceMotion,
        appearanceRepository.theme,
        dialogVisible.asStateFlow(),
    ) { profile, reduceMotion, theme, showDialog ->
        PreferencesUiState(profile = profile, reduceMotion = reduceMotion, theme = theme, showResetDialog = showDialog)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PreferencesUiState(),
    )

    fun onNameChange(name: String) = edit { ProfileEdits.name(it, name) }

    fun onCountrySelect(country: Country) = edit { ProfileEdits.country(it, country) }

    fun onCuisineToggle(cuisine: Cuisine) = edit { ProfileEdits.toggleCuisine(it, cuisine) }

    fun onStoreToggle(store: StoreChain) = edit { ProfileEdits.toggleStore(it, store) }

    fun onMainStore(store: StoreChain) = edit { ProfileEdits.mainStore(it, store) }

    fun onPersonsChange(delta: Int) = edit { ProfileEdits.persons(it, delta) }

    fun onDayToggle(day: Weekday) = edit { ProfileEdits.toggleDay(it, day) }

    fun onSlotToggle(slot: MealSlot) = edit { ProfileEdits.toggleSlot(it, slot) }

    fun onBudgetChange(value: Int) = edit { ProfileEdits.budget(it, value) }

    fun onMoodToggle(mood: Mood) = edit { ProfileEdits.toggleMood(it, mood) }

    fun onMainDiet(diet: Diet?) = edit { ProfileEdits.mainDiet(it, diet) }

    fun onDietToggle(diet: Diet) = edit { ProfileEdits.toggleDiet(it, diet) }

    fun onIntoleranceToggle(intolerance: Intolerance) = edit { ProfileEdits.toggleIntolerance(it, intolerance) }

    fun onDislikeToggle(dislike: Dislike) = edit { ProfileEdits.toggleDislike(it, dislike) }

    fun onCustomDislikeAdd(text: String) = edit { ProfileEdits.addCustomDislike(it, text) }

    fun onCustomDislikeRemove(text: String) = edit { ProfileEdits.removeCustomDislike(it, text) }

    fun onApplianceToggle(appliance: Appliance) = edit { ProfileEdits.toggleAppliance(it, appliance) }

    fun onReduceMotionChange(reduce: Boolean) {
        viewModelScope.launch { appearanceRepository.setReduceMotion(reduce) }
    }

    fun onThemeSelect(theme: ThemeChoice) {
        viewModelScope.launch { appearanceRepository.setTheme(theme) }
    }

    fun onResetRequest() {
        dialogVisible.value = true
    }

    fun onResetDismiss() {
        dialogVisible.value = false
    }

    fun onResetConfirm() {
        dialogVisible.value = false
        change {
            profileRepository.clear()
            // Das Onboarding gilt weiterhin als gesehen - sonst startet es beim
            // naechsten Start ungefragt neu, obwohl niemand danach gefragt hat.
            profileRepository.update { it.copy(onboardingCompleted = true) }
        }
    }

    private fun edit(transform: (UserProfile) -> UserProfile) = change { profileRepository.update(transform) }

    /**
     * Fuehrt eine Aenderung am Profil aus und zieht den bestehenden Plan nach, wenn sie
     * Mengen oder Preise betrifft. Die Gerichte bleiben dabei, wie sie sind.
     */
    private fun change(block: suspend () -> Unit) {
        viewModelScope.launch {
            val before = profileRepository.profile.first()
            block()
            val after = profileRepository.profile.first()
            if (before.persons != after.persons || before.priceFactor != after.priceFactor) adjustPlan()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
