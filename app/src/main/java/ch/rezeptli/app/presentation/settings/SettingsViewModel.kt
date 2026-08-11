package ch.rezeptli.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val profile: UserProfile = UserProfile.EMPTY,
    val showResetDialog: Boolean = false,
)

/**
 * Die Einstellungen zeigen dieselben Fragen wie das Onboarding, nur ohne Fuehrung.
 *
 * Jede Aenderung wird sofort gespeichert - es gibt keinen Speichern-Knopf, weil es
 * nichts gibt, was man abbrechen koennte.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: UserProfileRepository,
) : ViewModel() {
    private val dialogVisible = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> =
        combine(profileRepository.profile, dialogVisible.asStateFlow()) { profile, showDialog ->
            SettingsUiState(profile = profile, showResetDialog = showDialog)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SettingsUiState(),
        )

    fun onFirstNameChange(name: String) = edit { it.copy(firstName = name.take(MAX_NAME_LENGTH)) }

    fun onCountrySelect(country: Country) = edit {
        it.copy(country = if (it.country == country) null else country)
    }

    fun onCuisineToggle(cuisine: Cuisine) = edit { it.copy(cuisines = it.cuisines.toggle(cuisine)) }

    fun onDietToggle(diet: Diet) = edit { it.copy(diets = it.diets.toggle(diet)) }

    fun onIntoleranceToggle(intolerance: Intolerance) = edit {
        it.copy(intolerances = it.intolerances.toggle(intolerance))
    }

    fun onHouseholdSizeChange(size: Int?) = edit {
        it.copy(householdSize = size?.coerceIn(1, UserProfile.MAX_HOUSEHOLD_SIZE))
    }

    fun onResetRequest() {
        dialogVisible.value = true
    }

    fun onResetDismiss() {
        dialogVisible.value = false
    }

    fun onResetConfirm() {
        dialogVisible.value = false
        viewModelScope.launch {
            profileRepository.clear()
            // Das Onboarding gilt weiterhin als gesehen - sonst startet es beim
            // naechsten Start ungefragt neu, obwohl niemand danach gefragt hat.
            profileRepository.update { it.copy(onboardingCompleted = true) }
        }
    }

    private fun edit(transform: (UserProfile) -> UserProfile) {
        viewModelScope.launch { profileRepository.update(transform) }
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (value in this) this - value else this + value

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val MAX_NAME_LENGTH = 40
    }
}
