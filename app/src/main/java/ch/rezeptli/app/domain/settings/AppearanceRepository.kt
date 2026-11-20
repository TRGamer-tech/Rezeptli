package ch.rezeptli.app.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * Einstellungen zur Darstellung, die nichts mit dem Kochen zu tun haben.
 *
 * Sie liegen bewusst nicht im [ch.rezeptli.app.domain.profile.UserProfile]: Wer die
 * Vorlieben zuruecksetzt, will nicht, dass die App ploetzlich wieder herumhuepft.
 */
interface AppearanceRepository {
    /** Ob die App ihre Bewegungen reduziert - zusaetzlich zur Android-Einstellung. */
    val reduceMotion: Flow<Boolean>

    suspend fun setReduceMotion(reduce: Boolean)
}
