package ch.rezeptli.app.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * Wie die App aussieht.
 *
 * [SYSTEM] folgt der Einstellung des Geraets und ist die Vorgabe. [MATERIAL_YOU] nimmt die
 * Farben aus dem Hintergrundbild (ab Android 12) und folgt bei Hell und Dunkel ebenfalls
 * dem Geraet; auf aelteren Geraeten sieht es aus wie [SYSTEM].
 */
enum class ThemeChoice {
    SYSTEM,
    HELL,
    DUNKEL,
    MATERIAL_YOU,
    ;

    companion object {
        fun fromName(value: String?): ThemeChoice = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * Einstellungen zur Darstellung, die nichts mit dem Kochen zu tun haben.
 *
 * Sie liegen bewusst nicht im [ch.rezeptli.app.domain.profile.UserProfile]: Wer die
 * Vorlieben zuruecksetzt, will nicht, dass die App ploetzlich wieder herumhuepft oder die
 * Farben wechselt.
 */
interface AppearanceRepository {
    /** Ob die App ihre Bewegungen reduziert - zusaetzlich zur Android-Einstellung. */
    val reduceMotion: Flow<Boolean>

    suspend fun setReduceMotion(reduce: Boolean)

    /** Hell, dunkel, wie das Geraet oder in den Farben des Hintergrundbilds. */
    val theme: Flow<ThemeChoice>

    suspend fun setTheme(choice: ThemeChoice)
}
