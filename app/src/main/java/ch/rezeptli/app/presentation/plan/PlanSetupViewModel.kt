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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Die Fragen der Einrichtung, in der Reihenfolge, in der sie erscheinen. */
enum class SetupStep {
    POSTLEITZAHL,
    LADEN,
    PERSONEN,
    TAGE,
    BUDGET,
    STIMMUNG,
    ERNAEHRUNG,
    GERAETE,
    ;

    val isFirst: Boolean get() = ordinal == 0
    val isLast: Boolean get() = ordinal == entries.lastIndex
}

data class PlanSetupUiState(
    val step: SetupStep = SetupStep.POSTLEITZAHL,
    val profile: UserProfile = UserProfile(
        householdSize = 2,
        weeklyBudget = UserProfile.DEFAULT_BUDGET,
        cookDays = Weekday.entries.take(UserProfile.DEFAULT_COOK_DAYS).toSet(),
        appliances = setOf(Appliance.HERD, Appliance.BACKOFEN),
    ),
    val isSaving: Boolean = false,
) {
    val stepNumber: Int get() = step.ordinal + 1
    val stepCount: Int get() = SetupStep.entries.size
    val progress: Float get() = stepNumber.toFloat() / stepCount
}

sealed interface PlanSetupEvent {
    /** Die Angaben sind gespeichert - als Naechstes wird der Plan gebaut. */
    data object Finished : PlanSetupEvent
}

/**
 * Fuehrt durch die Fragen, aus denen der Wochenplan entsteht.
 *
 * Vorbelegt ist, was die meisten wollen (zwei Personen, fuenf Abende, Herd und Ofen).
 * Jeder Schritt laesst sich ohne Auswahl weiterklicken; gespeichert wird erst am Ende,
 * damit ein Abbruch in der Mitte nichts halb Fertiges hinterlaesst.
 */
@HiltViewModel
class PlanSetupViewModel @Inject constructor(
    private val profileRepository: UserProfileRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlanSetupUiState())
    val uiState: StateFlow<PlanSetupUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlanSetupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onZipChange(text: String) = edit { ProfileEdits.zip(it, text) }

    fun onStoreSelect(store: StoreChain) = edit { ProfileEdits.store(it, store) }

    fun onPersonsChange(delta: Int) = edit { ProfileEdits.persons(it, delta) }

    fun onDayToggle(day: Weekday) = edit { ProfileEdits.toggleDay(it, day) }

    fun onBudgetChange(value: Int) = edit { ProfileEdits.budget(it, value) }

    fun onMoodToggle(mood: Mood) = edit { ProfileEdits.toggleMood(it, mood) }

    fun onMainDiet(diet: Diet?) = edit { ProfileEdits.mainDiet(it, diet) }

    fun onDietToggle(diet: Diet) = edit { ProfileEdits.toggleDiet(it, diet) }

    fun onIntoleranceToggle(intolerance: Intolerance) = edit { ProfileEdits.toggleIntolerance(it, intolerance) }

    fun onApplianceToggle(appliance: Appliance) = edit { ProfileEdits.toggleAppliance(it, appliance) }

    fun onBack() {
        _uiState.update { state ->
            val previous = SetupStep.entries.getOrNull(state.step.ordinal - 1)
            if (previous == null) state else state.copy(step = previous)
        }
    }

    fun onNext() {
        if (_uiState.value.step.isLast) {
            finish()
        } else {
            _uiState.update { it.copy(step = SetupStep.entries[it.step.ordinal + 1]) }
        }
    }

    private fun finish() {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val answers = _uiState.value.profile
            // Name, Land und Kuechen stammen nicht aus diesen Fragen und bleiben erhalten.
            profileRepository.update { current ->
                current.copy(
                    zip = answers.zip,
                    country = current.country ?: answers.country,
                    store = answers.store,
                    householdSize = answers.householdSize,
                    cookDays = answers.cookDays,
                    weeklyBudget = answers.weeklyBudget,
                    moods = answers.moods,
                    diets = answers.diets,
                    intolerances = answers.intolerances,
                    appliances = answers.appliances,
                    onboardingCompleted = true,
                )
            }
            _events.send(PlanSetupEvent.Finished)
        }
    }

    private fun edit(transform: (UserProfile) -> UserProfile) {
        _uiState.update { it.copy(profile = transform(it.profile)) }
    }
}
