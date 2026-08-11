package ch.rezeptli.app.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Die Schritte des Onboardings, in der Reihenfolge, in der sie erscheinen. */
enum class OnboardingStep {
    /** Begruessung, Vorname und Wohnland - das Land steuert spaeter die Quellen. */
    WILLKOMMEN,

    KUECHEN,

    ERNAEHRUNG,

    HAUSHALT,
    ;

    val isFirst: Boolean get() = ordinal == 0
    val isLast: Boolean get() = ordinal == entries.lastIndex
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WILLKOMMEN,
    val profile: UserProfile = UserProfile.EMPTY,
    val isSaving: Boolean = false,
) {
    val stepNumber: Int get() = step.ordinal + 1
    val stepCount: Int get() = OnboardingStep.entries.size
    val progress: Float get() = stepNumber.toFloat() / stepCount
}

sealed interface OnboardingEvent {
    /** Das Onboarding ist vorbei - egal ob ausgefuellt oder uebersprungen. */
    data object Finished : OnboardingEvent
}

/**
 * Fuehrt durch die Fragen beim ersten Start.
 *
 * Keine Frage ist Pflicht: Jeder Schritt laesst sich weiterklicken, ohne etwas
 * auszuwaehlen, und das ganze Onboarding laesst sich jederzeit ueberspringen. Die
 * Antworten beeinflussen nur die Reihenfolge der Vorschlaege.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: UserProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _events = Channel<OnboardingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onFirstNameChange(name: String) = updateProfile {
        // Ein Vorname mit 80 Zeichen ist ein Versehen, kein Vorname.
        it.copy(firstName = name.take(MAX_NAME_LENGTH))
    }

    fun onCountrySelect(country: Country) = updateProfile {
        it.copy(country = if (it.country == country) null else country)
    }

    fun onCuisineToggle(cuisine: Cuisine) = updateProfile {
        it.copy(cuisines = it.cuisines.toggle(cuisine))
    }

    fun onDietToggle(diet: Diet) = updateProfile {
        it.copy(diets = it.diets.toggle(diet))
    }

    fun onIntoleranceToggle(intolerance: Intolerance) = updateProfile {
        it.copy(intolerances = it.intolerances.toggle(intolerance))
    }

    fun onHouseholdSizeChange(size: Int?) = updateProfile {
        it.copy(householdSize = size?.coerceIn(1, UserProfile.MAX_HOUSEHOLD_SIZE))
    }

    fun onBack() {
        _uiState.update { state ->
            val previous = OnboardingStep.entries.getOrNull(state.step.ordinal - 1)
            if (previous == null) state else state.copy(step = previous)
        }
    }

    fun onNext() {
        val state = _uiState.value
        if (state.step.isLast) {
            finish()
            return
        }
        _uiState.update {
            it.copy(step = OnboardingStep.entries[it.step.ordinal + 1])
        }
    }

    /** Ueberspringen behaelt, was bereits ausgefuellt wurde - es bricht nichts ab. */
    fun onSkip() = finish()

    private fun finish() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val answers = _uiState.value.profile
            // Die Antworten dieses Durchlaufs ersetzen den bisherigen Stand vollstaendig.
            profileRepository.update {
                answers.copy(onboardingCompleted = true, firstName = answers.firstName.trim())
            }
            _events.send(OnboardingEvent.Finished)
        }
    }

    private fun updateProfile(transform: (UserProfile) -> UserProfile) {
        _uiState.update { it.copy(profile = transform(it.profile)) }
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> =
        if (value in this) this - value else this + value

    private companion object {
        const val MAX_NAME_LENGTH = 40
    }
}
