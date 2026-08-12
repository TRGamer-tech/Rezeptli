package ch.rezeptli.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Wohin die App beim Start fuehrt.
 *
 * [Unbekannt] ist der Zustand, solange die Einstellungen noch gelesen werden. In dieser
 * Zeit bleibt der Startbildschirm stehen - sonst blitzt kurz die Rezeptliste auf, bevor
 * das Onboarding sie ersetzt.
 */
sealed interface StartState {
    data object Unbekannt : StartState

    data object Onboarding : StartState

    /** Onboarding ist erledigt - es geht auf den Wischstapel, den Startbildschirm. */
    data object Bereit : StartState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    profileRepository: UserProfileRepository,
) : ViewModel() {
    val startState: StateFlow<StartState> = profileRepository.profile
        .map { if (it.onboardingCompleted) StartState.Bereit else StartState.Onboarding }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = StartState.Unbekannt,
        )
}
