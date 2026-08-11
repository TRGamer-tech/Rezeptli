package ch.rezeptli.app.domain.multiplayer

/**
 * Eine gemeinsame Swipe-Runde.
 *
 * [code] ist das, was man der anderen Person zeigt oder schickt. Er ist kurz und
 * absichtlich ohne die Zeichen I, O, 0 und 1 - die verwechselt man beim Abtippen.
 */
data class SharedSession(
    val code: String,
    val expiresAt: Long,
    val recipes: List<SharedRecipe> = emptyList(),
) {
    val isEmpty: Boolean get() = recipes.isEmpty()
}

/**
 * Ein Rezept, wie es die andere Person sieht.
 *
 * Bewusst wenig: Rezept-Kennungen sind auf jedem Geraet andere, also muss die Runde
 * geteilt werden. Geteilt wird aber nur, was zum Entscheiden noetig ist - Titel, Bild
 * und Zeit. Zutaten und Zubereitung bleiben auf dem Geraet.
 */
data class SharedRecipe(
    val recipeId: Long,
    val title: String,
    val sourceUrl: String? = null,
    val imageUrl: String? = null,
    val prepTimeMinutes: Int? = null,
)

/** Eine einzelne Entscheidung, wie sie an den Dienst geht. */
data class SharedVote(
    val recipeId: Long,
    val liked: Boolean,
)

/**
 * Der Stand einer laufenden Runde.
 *
 * [matches] bleibt leer, solange nicht alle entschieden haben - der Dienst gibt die
 * Treffer erst dann heraus. Sonst liesse sich am Zwischenstand ablesen, was die andere
 * Person gewischt hat, und das ist beim gemeinsamen Aussuchen der halbe Reiz.
 */
data class SharedSessionState(
    val code: String,
    val participants: Int,
    val finished: Int,
    val allFinished: Boolean,
    val matches: List<SharedRecipe> = emptyList(),
    val expiresAt: Long = 0L,
) {
    /** Es fehlt noch jemand - der Einladungscode sollte also noch sichtbar sein. */
    val isWaitingForPartner: Boolean get() = participants < 2

    /** Alle sind da, aber jemand wischt noch. */
    val isWaitingForDecisions: Boolean get() = participants >= 2 && !allFinished
}

/** Warum eine Runde nicht zustande kam - in Worten, die die App zeigen kann. */
enum class PairingError {
    NO_CONNECTION,

    /** Den Code gibt es nicht (oder er wurde vertippt). */
    UNKNOWN_CODE,

    /** Die Runde ist abgelaufen oder wurde beendet. */
    EXPIRED,

    /** Es sind schon genug Leute dabei. */
    FULL,

    /** Ohne Rezepte laesst sich nichts teilen. */
    NO_RECIPES,

    UNKNOWN,
}

sealed interface PairingResult<out T> {
    data class Success<T>(val value: T) : PairingResult<T>

    data class Failure(val error: PairingError) : PairingResult<Nothing>
}
