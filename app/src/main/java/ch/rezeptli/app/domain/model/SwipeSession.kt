package ch.rezeptli.app.domain.model

/** Modus einer Swipe-Session. MULTIPLAYER ist fuer eine spaetere Version vorgesehen. */
enum class SwipeMode {
    SOLO,
    MULTIPLAYER,
}

/**
 * Eine Swipe-Session buendelt alle Entscheidungen eines Durchgangs.
 *
 * [finishedAt] ist `null`, solange die Session laeuft. Damit kann eine unterbrochene
 * Session spaeter fortgesetzt werden.
 */
data class SwipeSession(
    val id: Long = 0L,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val mode: SwipeMode = SwipeMode.SOLO,
) {
    val isFinished: Boolean get() = finishedAt != null
}

/**
 * Eine einzelne Wisch-Entscheidung.
 *
 * [participantId] ist im Solo-Modus immer [LOCAL_PARTICIPANT]. Das Feld existiert von
 * Anfang an, damit der spaetere Mehrspieler-Modus die Entscheidungen einer zweiten
 * Person in derselben Session ablegen kann, ohne Schema-Migration der Kernlogik.
 */
data class SwipeDecision(
    val sessionId: Long,
    val recipeId: Long,
    val liked: Boolean,
    val decidedAt: Long,
    val participantId: String = LOCAL_PARTICIPANT,
) {
    companion object {
        const val LOCAL_PARTICIPANT = "local"
    }
}
