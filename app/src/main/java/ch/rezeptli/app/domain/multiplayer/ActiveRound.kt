package ch.rezeptli.app.domain.multiplayer

/**
 * Eine laufende gemeinsame Runde, wie sie das Geraet sich merkt.
 *
 * Damit laesst sie sich fortsetzen, wenn die App zwischendurch geschlossen wurde oder man
 * versehentlich zurueckgegangen ist. Gemerkt wird nur der Code: Stapel, Stand und die
 * eigenen bisherigen Entscheidungen liegen beim Dienst und kommen beim Fortsetzen von dort.
 */
data class ActiveRound(
    val code: String,
    val isHost: Boolean,
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
