package ch.rezeptli.app.domain.repository

import ch.rezeptli.app.domain.profile.UserProfile
import kotlinx.coroutines.flow.Flow

/** Zugriff auf die Angaben aus dem Onboarding und den Einstellungen. */
interface UserProfileRepository {
    /** Das Profil, und jede spaetere Aenderung daran. */
    val profile: Flow<UserProfile>

    suspend fun update(transform: (UserProfile) -> UserProfile)

    /** Setzt alle Angaben zurueck - inklusive des Hinweises, dass das Onboarding lief. */
    suspend fun clear()
}
