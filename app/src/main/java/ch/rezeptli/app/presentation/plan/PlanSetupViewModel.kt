package ch.rezeptli.app.presentation.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Die Seiten der Einrichtung, in der Reihenfolge, in der sie erscheinen: erst die
 * Begruessung, dann die Fragen.
 */
enum class SetupStep {
    /** "Schoen, dass du da bist" - noch keine Frage. */
    WILLKOMMEN,

    /** Was die App fuer einen tut - auch noch keine Frage. */
    UEBERBLICK,
    NAME,
    DESIGN,
    LAND,
    KUECHEN,
    ERNAEHRUNG,
    ABNEIGUNGEN,
    PERSONEN,
    TAGE,
    LADEN,
    BUDGET,
    STIMMUNG,
    GERAETE,
    ;

    val isFirst: Boolean get() = ordinal == 0
    val isLast: Boolean get() = ordinal == entries.lastIndex

    /** Begruessung und Ueberblick fragen nichts - sie zaehlen nicht als Schritt. */
    val isIntro: Boolean get() = this == WILLKOMMEN || this == UEBERBLICK

    companion object {
        /** Die eigentlichen Fragen - "Schritt 3 von 12" zaehlt nur sie. */
        val questions: List<SetupStep> = entries.filterNot { it.isIntro }
    }
}

data class PlanSetupUiState(
    val step: SetupStep = SetupStep.WILLKOMMEN,
    val profile: UserProfile = UserProfile(
        householdSize = 2,
        cookDays = Weekday.entries.take(UserProfile.DEFAULT_COOK_DAYS).toSet(),
        appliances = setOf(Appliance.HERD, Appliance.BACKOFEN),
    ),
    /** Das gewaehlte Aussehen. Es gilt sofort und gehoert nicht zum Profil - siehe AppearanceRepository. */
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val isSaving: Boolean = false,
) {
    /** Die wievielte Frage das ist - 0 auf den Seiten davor. */
    val stepNumber: Int get() = SetupStep.questions.indexOf(step) + 1
    val stepCount: Int get() = SetupStep.questions.size
    val progress: Float get() = stepNumber.toFloat() / stepCount
}

sealed interface PlanSetupEvent {
    /** Die Angaben sind gespeichert - als Naechstes wird der Plan gebaut. */
    data object Finished : PlanSetupEvent
}

/**
 * Fuehrt durch die Einrichtung: begruessen, erklaeren, fragen.
 *
 * Niemand wird beim ersten Oeffnen gleich nach seinem Namen gefragt. Zuerst sagt die App,
 * was sie ist und was sie fuer einen tut - dann, wer man ist und was man mag (Name,
 * Aussehen, Land, Kuechen, Ernaehrung, Abneigungen), dann, wie geplant wird. Vorbelegt ist, was die meisten wollen: zwei Personen, fuenf
 * Tage, Herd und Ofen. Jeder Schritt laesst sich ohne Auswahl weiterklicken; gespeichert
 * wird erst am Ende, damit ein Abbruch in der Mitte nichts halb Fertiges hinterlaesst.
 */
@HiltViewModel
class PlanSetupViewModel @Inject constructor(
    private val profileRepository: UserProfileRepository,
    private val appearanceRepository: AppearanceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlanSetupUiState())
    val uiState: StateFlow<PlanSetupUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlanSetupEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        appearanceRepository.theme
            .onEach { theme -> _uiState.update { it.copy(theme = theme) } }
            .launchIn(viewModelScope)
    }

    /**
     * Das Aussehen gilt sofort, nicht erst am Ende: Wer "dunkel" antippt, soll die App in
     * derselben Sekunde dunkel sehen - sonst waere die Frage ein Ratespiel.
     */
    fun onThemeSelect(choice: ThemeChoice) {
        viewModelScope.launch { appearanceRepository.setTheme(choice) }
    }

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
            // Die Einrichtung fragt alles ab, was im Profil steht - also ersetzt sie es ganz.
            profileRepository.update { answers.copy(firstName = answers.firstName.trim(), onboardingCompleted = true) }
            _events.send(PlanSetupEvent.Finished)
        }
    }

    private fun edit(transform: (UserProfile) -> UserProfile) {
        _uiState.update { it.copy(profile = transform(it.profile)) }
    }
}
