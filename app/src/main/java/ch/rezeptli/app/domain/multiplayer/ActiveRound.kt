package ch.rezeptli.app.domain.multiplayer

/**
 * Eine laufende gemeinsame Runde, wie sie das Geraet sich merkt.
 *
 * Damit laesst sie sich fortsetzen, wenn die App zwischendurch geschlossen wurde oder man
 * versehentlich zurueckgegangen ist: Code, Rolle und die eigenen bisherigen Entscheidungen.
 * Die Karten selbst holt die App beim Fortsetzen neu vom Dienst - dort liegt der
 * eingefrorene, fuer beide gleiche Stapel.
 */
data class ActiveRound(
    val code: String,
    val isHost: Boolean,
    val votes: List<SharedVote> = emptyList(),
    val expiresAt: Long,
) {
    fun isExpired(now: Long): Boolean = expiresAt in 1..now
}

/** Merkt sich hoechstens eine laufende Runde. */
interface ActiveRoundRepository {
    suspend fun load(): ActiveRound?

    suspend fun save(round: ActiveRound)

    suspend fun clear()
}
