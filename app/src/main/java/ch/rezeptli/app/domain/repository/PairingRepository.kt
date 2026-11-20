package ch.rezeptli.app.domain.repository

import ch.rezeptli.app.domain.multiplayer.PairingResult
import ch.rezeptli.app.domain.multiplayer.RoundOffer
import ch.rezeptli.app.domain.multiplayer.RoundSource
import ch.rezeptli.app.domain.multiplayer.RoundState
import ch.rezeptli.app.domain.multiplayer.SharedContent
import ch.rezeptli.app.domain.multiplayer.SharedVote
import ch.rezeptli.app.domain.plan.CourseChoice
import ch.rezeptli.app.domain.plan.DietaryFilter

/**
 * Zugriff auf den Pairing-Dienst.
 *
 * Alle Aufrufe koennen scheitern, ohne dass etwas kaputtgeht: Die gemeinsame Runde ist eine
 * Zugabe, kein Fundament. Faellt der Dienst aus, funktioniert die App unveraendert weiter -
 * nur eben allein.
 *
 * Jeder Aufruf, der die Runde betrifft, antwortet mit ihrem aktuellen Stand. [knownRound]
 * sagt dem Dienst, fuer welche Runde dieses Geraet den Stapel schon hat - nur ein anderer
 * wird mitgeschickt.
 */
interface PairingRepository {
    /**
     * Eroeffnet eine Runde. Der Code steht sofort fest - noch bevor entschieden ist, was
     * gesucht wird. [restrictions] ist, was die eroeffnende Person nicht auf dem Tisch will.
     */
    suspend fun createRound(restrictions: DietaryFilter): PairingResult<RoundState>

    /**
     * Tritt einer Runde bei - oder kehrt in eine zurueck. Beim ersten Beitritt legt der
     * Dienst [restrictions] mit denen der anderen Person zusammen; danach gelten sie fuer beide.
     */
    suspend fun joinRound(
        code: String,
        restrictions: DietaryFilter,
        knownRound: Int = NO_ROUND,
    ): PairingResult<RoundState>

    /** Legt fest, was gesucht wird. Nur wer eroeffnet hat, und nur vor dem Start. */
    suspend fun configureRound(
        code: String,
        target: Int,
        course: CourseChoice,
        source: RoundSource,
    ): PairingResult<RoundState>

    suspend fun startRound(code: String): PairingResult<RoundState>

    /** Steuert die eigenen Karten zur Runde [round] bei. Ein leerer Beitrag ist erlaubt. */
    suspend fun contribute(
        code: String,
        round: Int,
        offers: List<RoundOffer>,
        knownRound: Int = NO_ROUND,
    ): PairingResult<RoundState>

    /**
     * Sendet Entscheidungen. Mehrfach dasselbe zu senden ist unschaedlich - bei wackeligem
     * Netz versucht es die App noch einmal.
     */
    suspend fun sendVotes(code: String, votes: List<SharedVote>, knownRound: Int = NO_ROUND): PairingResult<RoundState>

    /** Der Stapel ist durch: mit neuen Rezepten weitermachen. Nur wer eroeffnet hat. */
    suspend fun continueRound(code: String, knownRound: Int = NO_ROUND): PairingResult<RoundState>

    /** Hoert auf und behaelt, was gefunden wurde. Die Runde endet damit fuer beide. */
    suspend fun endRound(code: String): PairingResult<RoundState>

    /** Loescht die Runde beim Dienst sofort und restlos. Nur wer sie eroeffnet hat, kann das. */
    suspend fun closeRound(code: String): PairingResult<Unit>

    /**
     * Der aktuelle Stand. Kennt das Geraet ihn schon ([revision] stimmt), kommt `null`
     * zurueck: nichts Neues.
     */
    suspend fun roundState(code: String, revision: Int? = null, knownRound: Int = NO_ROUND): PairingResult<RoundState?>

    /**
     * Hinterlegt den Inhalt eines selbst erfassten Rezepts dieses Geraets, das ein gemeinsamer
     * Treffer geworden ist - damit die andere Person es uebernehmen kann.
     */
    suspend fun shareContent(code: String, recipeId: Long, content: SharedContent): PairingResult<Unit>

    /** Holt den Inhalt eines Treffers der anderen Person. `null`: Er liegt noch nicht bereit. */
    suspend fun fetchContent(code: String, recipeId: Long): PairingResult<SharedContent?>

    /**
     * Die Kennung dieses Geraets innerhalb von Runden.
     *
     * Zufaellig erzeugt und nur hier bekannt: Sie ist keine Geraetekennung und laesst
     * sich nicht auf eine Person zurueckfuehren.
     */
    suspend fun participantId(): String

    companion object {
        /** Das Geraet hat noch keinen Stapel. */
        const val NO_ROUND = -1
    }
}
