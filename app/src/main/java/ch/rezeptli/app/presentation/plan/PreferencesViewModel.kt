package ch.rezeptli.app.presentation.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.profile.Appliance
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.Mood
import ch.rezeptli.app.domain.profile.ProfileEdits
import ch.rezeptli.app.domain.profile.StoreChain
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Die Vorlieben als Liste: dieselben Fragen wie die Einrichtung, jederzeit aenderbar.
 *
 * Jede Aenderung wird sofort gespeichert. Der bestehende Plan bleibt dabei unberuehrt -
 * erst "Neu bauen" wendet die neuen Angaben an, sonst wuerde das Antippen einer Stimmung
 * die Woche unter den Fuessen wegziehen.
 */
@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val profileRepository: UserProfileRepository,
) : ViewModel() {
    val profile: StateFlow<UserProfile> = profileRepository.profile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = UserProfile.EMPTY,
    )

    fun onStoreSelect(store: StoreChain) = edit { ProfileEdits.store(it, store) }

    fun onPersonsChange(delta: Int) = edit { ProfileEdits.persons(it, delta) }

    fun onDayToggle(day: Weekday) = edit { ProfileEdits.toggleDay(it, day) }

    fun onBudgetChange(value: Int) = edit { ProfileEdits.budget(it, value) }

    fun onMoodToggle(mood: Mood) = edit { ProfileEdits.toggleMood(it, mood) }

    fun onMainDiet(diet: Diet?) = edit { ProfileEdits.mainDiet(it, diet) }

    fun onDietToggle(diet: Diet) = edit { ProfileEdits.toggleDiet(it, diet) }

    fun onIntoleranceToggle(intolerance: Intolerance) = edit { ProfileEdits.toggleIntolerance(it, intolerance) }

    fun onApplianceToggle(appliance: Appliance) = edit { ProfileEdits.toggleAppliance(it, appliance) }

    private fun edit(transform: (UserProfile) -> UserProfile) {
        viewModelScope.launch { profileRepository.update(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
