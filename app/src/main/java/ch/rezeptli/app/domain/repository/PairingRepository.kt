package ch.rezeptli.app.domain.repository

import ch.rezeptli.app.domain.multiplayer.PairingResult
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedSession
import ch.rezeptli.app.domain.multiplayer.SharedSessionState
import ch.rezeptli.app.domain.multiplayer.SharedVote

/**
 * Zugriff auf den Pairing-Dienst.
 *
 * Alle Aufrufe koennen scheitern, ohne dass etwas kaputtgeht: Der Mehrspieler-Modus
 * ist eine Zugabe, kein Fundament. Faellt der Dienst aus, funktioniert die App
 * unveraendert weiter - nur eben allein.
 */
interface PairingRepository {
    /** Eroeffnet eine Runde mit den uebergebenen Rezepten und liefert den Code. */
    suspend fun createSession(recipes: List<SharedRecipe>): PairingResult<SharedSession>

    /** Tritt einer Runde bei und holt die Rezepte, ueber die abgestimmt wird. */
    suspend fun joinSession(code: String): PairingResult<SharedSession>

    /**
     * Sendet Entscheidungen. Mehrfach dasselbe zu senden ist unschaedlich - bei
     * wackeligem Netz versucht es die App noch einmal.
     */
    suspend fun sendVotes(code: String, votes: List<SharedVote>, finished: Boolean): PairingResult<Unit>

    suspend fun sessionState(code: String): PairingResult<SharedSessionState>

    /** Beendet die Runde sofort. Nur wer sie eroeffnet hat, kann das. */
    suspend fun closeSession(code: String): PairingResult<Unit>

    /**
     * Die Kennung dieses Geraets innerhalb von Runden.
     *
     * Zufaellig erzeugt und nur hier bekannt: Sie ist keine Geraetekennung und laesst
     * sich nicht auf eine Person zurueckfuehren.
     */
    suspend fun participantId(): String
}
