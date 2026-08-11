package ch.rezeptli.app.fake

import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Haelt das Profil im Speicher - schnell genug fuer Tests, ohne DataStore. */
class FakeUserProfileRepository(
    initial: UserProfile = UserProfile.EMPTY,
) : UserProfileRepository {
    private val state = MutableStateFlow(initial)

    override val profile: Flow<UserProfile> = state

    override suspend fun update(transform: (UserProfile) -> UserProfile) {
        state.update(transform)
    }

    override suspend fun clear() {
        state.value = UserProfile.EMPTY
    }

    /** Nur fuer Tests: der aktuelle Stand ohne Umweg ueber den Flow. */
    val current: UserProfile get() = state.value
}
