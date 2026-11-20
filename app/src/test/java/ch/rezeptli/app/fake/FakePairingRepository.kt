package ch.rezeptli.app.fake

import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.PairingResult
import ch.rezeptli.app.domain.multiplayer.RoundEnd
import ch.rezeptli.app.domain.multiplayer.RoundOffer
import ch.rezeptli.app.domain.multiplayer.RoundPhase
import ch.rezeptli.app.domain.multiplayer.RoundSource
import ch.rezeptli.app.domain.multiplayer.RoundState
import ch.rezeptli.app.domain.multiplayer.SharedContent
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedVote
import ch.rezeptli.app.domain.plan.CourseChoice
import ch.rezeptli.app.domain.plan.DietaryFilter
import ch.rezeptli.app.domain.repository.PairingRepository

/**
 * Ein Pairing-Dienst im Speicher - aus der Sicht EINES Telefons, mit einer zweiten Person,
 * die der Test spielt ([partnerJoins], [partnerContributes], [partnerVotes] ...).
 *
 * Er ahmt die Regeln des echten Dienstes nach, auf die es der App ankommt: Die Angaben
 * beider werden vereinigt, der Stapel entsteht erst aus beiden Beitraegen und liegt
 * abwechselnd, ein Treffer braucht beide Stimmen, beim Ziel ist Schluss, und ist der Stapel
 * durch, wird gefragt. Der echte Dienst selbst wird in `pairing/tests` geprueft.
 */
class FakePairingRepository(
    var failWith: PairingError? = null,
) : PairingRepository {
    var exists = false
        private set
    var phase = RoundPhase.LOBBY
        private set
    var iAmHost = false
        private set
    var iAmIn = false
        private set
    var partnerIsIn = false
        private set
    var target = RoundState.DEFAULT_TARGET
        private set
    var course = CourseChoice.ESSEN
        private set
    var source = RoundSource.GESPEICHERT
        private set
    var round = 0
        private set
    var restrictions = DietaryFilter()
        private set
    var end: RoundEnd? = null
        private set
    var revision = 0
        private set

    /** Hat die eroeffnende Person die Runde beim Dienst geloescht? */
    var closed = false
        private set

    /** Alles, was die App an Stimmen geschickt hat - in der Reihenfolge des Eintreffens. */
    val receivedVotes = mutableListOf<SharedVote>()

    /** Was die App je Runde beigesteuert hat. */
    val myContributions = mutableMapOf<Int, List<RoundOffer>>()

    /** So viele der naechsten Beitraege scheitern am Netz - alles andere klappt weiter. */
    var failNextContributions = 0

    /** Was die App an Rezeptinhalten hinterlegt hat - nur selbst erfasste Treffer gehoeren hierher. */
    val sharedContents = linkedMapOf<Long, SharedContent>()

    /** Zu welchen Karten die App einen Inhalt holen wollte - in der Reihenfolge der Anfragen. */
    val contentRequests = mutableListOf<Long>()

    /** So viele der naechsten Versuche, einen Inhalt zu hinterlegen oder zu holen, scheitern am Netz. */
    var failNextContentCalls = 0

    /** Wie oft der Stand abgefragt wurde, und wie oft dabei der Stapel mitkam. */
    var stateRequests = 0
        private set
    var stacksSent = 0
        private set

    private data class Card(val recipe: SharedRecipe, val round: Int)

    private val cards = mutableListOf<Card>()
    private val myVotes = linkedMapOf<Long, Boolean>()
    private val theirVotes = mutableMapOf<Long, Boolean>()
    private var partnerContribution: List<RoundOffer>? = null
    private val partnerContents = mutableMapOf<Long, SharedContent>()
    private var nextId = 1L

    /** Der Stapel der laufenden Runde, wie beide ihn sehen. */
    val stack: List<SharedRecipe> get() = cards.filter { it.round == round }.map { it.recipe }

    val matches: List<SharedRecipe>
        get() = cards.map { it.recipe }.filter { myVotes[it.recipeId] == true && theirVotes[it.recipeId] == true }

    // ------------------------------------------------------------------ die zweite Person

    /** Die andere Person hat eroeffnet - die App wird beitreten. */
    fun partnerHosts(wishes: DietaryFilter = DietaryFilter()) {
        exists = true
        iAmHost = false
        partnerIsIn = true
        restrictions = wishes
        changed()
    }

    fun partnerJoins(wishes: DietaryFilter = DietaryFilter()) {
        partnerIsIn = true
        restrictions += wishes
        changed()
    }

    /** Die andere Person legt fest, was gesucht wird (sie hat eroeffnet). */
    fun partnerConfigures(target: Int, course: CourseChoice = this.course, source: RoundSource = this.source) {
        this.target = target
        this.course = course
        this.source = source
        changed()
    }

    fun partnerStarts() {
        phase = RoundPhase.SAMMELN
        changed()
    }

    fun partnerContributes(vararg offers: RoundOffer) {
        partnerContribution = offers.toList()
        buildStackIfComplete()
        changed()
    }

    /**
     * Das andere Telefon meldet sich nicht - die Frist laeuft ab, und die Runde beginnt mit
     * dem, was dieses Geraet beigesteuert hat.
     */
    fun contributionDeadlinePasses() {
        if (phase != RoundPhase.SAMMELN || round !in myContributions) return
        partnerContribution = partnerContribution ?: emptyList()
        buildStackIfComplete()
        changed()
    }

    /** Die andere Person hinterlegt den Inhalt eines ihrer selbst erfassten Treffer. */
    fun partnerShares(recipeId: Long, content: SharedContent) {
        partnerContents[recipeId] = content
        changed()
    }

    fun partnerVotes(recipeId: Long, liked: Boolean) {
        if (phase != RoundPhase.LAEUFT) return
        theirVotes[recipeId] = liked
        settle()
        changed()
    }

    /** Die andere Person wischt den ganzen Stapel durch. */
    fun partnerVotesAll(liked: (SharedRecipe) -> Boolean) {
        stack.forEach { karte -> partnerVotes(karte.recipeId, liked(karte)) }
    }

    fun partnerContinues() {
        nextRound()
        changed()
    }

    fun partnerEnds() {
        phase = RoundPhase.BEENDET
        end = if (iAmHost) RoundEnd.VERLASSEN else RoundEnd.GASTGEBER
        changed()
    }

    /** Die Runde verschwindet beim Dienst - abgelaufen oder von der anderen Person geloescht. */
    fun vanish() {
        exists = false
    }

    // ------------------------------------------------------------------ die App

    override suspend fun createRound(restrictions: DietaryFilter): PairingResult<RoundState> = answer(NO_STACK) {
        exists = true
        iAmHost = true
        iAmIn = true
        this.restrictions = restrictions
    }

    override suspend fun joinRound(
        code: String,
        restrictions: DietaryFilter,
        knownRound: Int,
    ): PairingResult<RoundState> {
        if (failWith == null && (!exists || code != CODE)) return PairingResult.Failure(PairingError.UNKNOWN_CODE)
        if (failWith == null && !iAmIn && phase != RoundPhase.LOBBY) return PairingResult.Failure(PairingError.FULL)
        return answer(knownRound) {
            if (!iAmIn) {
                iAmIn = true
                this.restrictions += restrictions
            }
        }
    }

    override suspend fun configureRound(
        code: String,
        target: Int,
        course: CourseChoice,
        source: RoundSource,
    ): PairingResult<RoundState> = answer(NO_STACK) {
        if (iAmHost && phase == RoundPhase.LOBBY) {
            this.target = target
            this.course = course
            this.source = source
        }
    }

    override suspend fun startRound(code: String): PairingResult<RoundState> {
        if (failWith == null &&
            phase == RoundPhase.LOBBY &&
            !partnerIsIn
        ) {
            return PairingResult.Failure(PairingError.FULL)
        }
        return answer(NO_STACK) {
            if (iAmHost && phase == RoundPhase.LOBBY) phase = RoundPhase.SAMMELN
        }
    }

    override suspend fun contribute(
        code: String,
        round: Int,
        offers: List<RoundOffer>,
        knownRound: Int,
    ): PairingResult<RoundState> {
        if (failNextContributions > 0) {
            failNextContributions -= 1
            return PairingResult.Failure(PairingError.NO_CONNECTION)
        }
        return answer(knownRound) {
            if (phase == RoundPhase.SAMMELN && round == this.round && round !in myContributions) {
                myContributions[round] = offers
                buildStackIfComplete()
            }
        }
    }

    override suspend fun sendVotes(code: String, votes: List<SharedVote>, knownRound: Int): PairingResult<RoundState> =
        answer(knownRound) {
            receivedVotes += votes
            if (phase == RoundPhase.LAEUFT) {
                val offen = stack.mapTo(HashSet()) { it.recipeId }
                votes.filter { it.recipeId in offen }.forEach { vote ->
                    when (val liked = vote.liked) {
                        null -> myVotes.remove(vote.recipeId)
                        else -> {
                            myVotes.remove(vote.recipeId)
                            myVotes[vote.recipeId] = liked
                        }
                    }
                }
                settle()
            }
        }

    override suspend fun continueRound(code: String, knownRound: Int): PairingResult<RoundState> = answer(knownRound) {
        if (iAmHost && phase == RoundPhase.FRAGE) nextRound()
    }

    override suspend fun endRound(code: String): PairingResult<RoundState> = answer(NO_STACK) {
        if (phase != RoundPhase.BEENDET) {
            phase = RoundPhase.BEENDET
            end = if (iAmHost) RoundEnd.GASTGEBER else RoundEnd.VERLASSEN
        }
    }

    override suspend fun closeRound(code: String): PairingResult<Unit> {
        failWith?.let { return PairingResult.Failure(it) }
        closed = true
        exists = false
        return PairingResult.Success(Unit)
    }

    override suspend fun roundState(code: String, revision: Int?, knownRound: Int): PairingResult<RoundState?> {
        stateRequests += 1
        failWith?.let { return PairingResult.Failure(it) }
        if (!exists) return PairingResult.Failure(PairingError.UNKNOWN_CODE)
        if (revision == this.revision) return PairingResult.Success(null)
        return PairingResult.Success(state(knownRound))
    }

    override suspend fun shareContent(code: String, recipeId: Long, content: SharedContent): PairingResult<Unit> {
        failWith?.let { return PairingResult.Failure(it) }
        if (failNextContentCalls > 0) {
            failNextContentCalls -= 1
            return PairingResult.Failure(PairingError.NO_CONNECTION)
        }
        if (!exists) return PairingResult.Failure(PairingError.UNKNOWN_CODE)
        // Wie der echte Dienst: nur ein gemeinsamer Treffer, nur ohne Adresse, nur vom eigenen Geraet.
        val karte = matches.firstOrNull { it.recipeId == recipeId }
        if (karte == null || !karte.isMine || !karte.isHandwritten) return PairingResult.Failure(PairingError.FULL)
        sharedContents[recipeId] = content
        changed()
        return PairingResult.Success(Unit)
    }

    override suspend fun fetchContent(code: String, recipeId: Long): PairingResult<SharedContent?> {
        contentRequests += recipeId
        failWith?.let { return PairingResult.Failure(it) }
        if (failNextContentCalls > 0) {
            failNextContentCalls -= 1
            return PairingResult.Failure(PairingError.NO_CONNECTION)
        }
        if (!exists) return PairingResult.Failure(PairingError.UNKNOWN_CODE)
        return PairingResult.Success(partnerContents[recipeId] ?: sharedContents[recipeId])
    }

    override suspend fun participantId(): String = PARTICIPANT

    // ------------------------------------------------------------------ Regeln

    private fun answer(knownRound: Int, change: () -> Unit): PairingResult<RoundState> {
        failWith?.let { return PairingResult.Failure(it) }
        if (!exists && iAmIn) return PairingResult.Failure(PairingError.UNKNOWN_CODE)
        change()
        changed()
        return PairingResult.Success(state(knownRound))
    }

    private fun changed() {
        revision += 1
    }

    /** Haben beide beigetragen, entsteht der Stapel: abwechselnd, ohne Dubletten, ohne schon Gesehenes. */
    private fun buildStackIfComplete() {
        val mine = myContributions[round] ?: return
        val theirs = partnerContribution ?: return
        if (phase != RoundPhase.SAMMELN) return

        val (even, odd) = if (iAmHost) mine to theirs else theirs to mine
        val known = cards.mapTo(HashSet()) { it.recipe.key }
        val mixed = (0 until maxOf(even.size, odd.size))
            .flatMap { index -> listOfNotNull(even.getOrNull(index), odd.getOrNull(index)) }
            .filter { known.add(it.key) }

        when {
            mixed.isNotEmpty() -> {
                mixed.forEach { offer ->
                    val recipe = SharedRecipe(
                        recipeId = nextId++,
                        title = offer.title,
                        sourceUrl = offer.sourceUrl,
                        imageUrl = offer.imageUrl,
                        prepTimeMinutes = offer.prepTimeMinutes,
                        key = offer.key,
                        isMine = mine.any { it.key == offer.key },
                    )
                    cards += Card(recipe, round)
                }
                phase = RoundPhase.LAEUFT
            }

            source == RoundSource.GESPEICHERT -> nextRound()

            else -> {
                phase = RoundPhase.BEENDET
                end = RoundEnd.LEER
            }
        }
    }

    private fun nextRound() {
        round += 1
        source = RoundSource.NEU
        phase = RoundPhase.SAMMELN
        partnerContribution = null
    }

    /** Nach jeder Stimme: Ist das Ziel erreicht? Ist der Stapel durch? */
    private fun settle() {
        if (phase != RoundPhase.LAEUFT) return
        val offen = stack
        when {
            matches.size >= target -> {
                phase = RoundPhase.BEENDET
                end = RoundEnd.ZIEL
            }

            offen.all { it.recipeId in myVotes && it.recipeId in theirVotes } -> phase = RoundPhase.FRAGE
        }
    }

    private fun state(knownRound: Int): RoundState {
        val offen = stack
        val kennungen = offen.mapTo(HashSet()) { it.recipeId }
        val mitStapel = phase == RoundPhase.LAEUFT && knownRound != round
        if (mitStapel) stacksSent += 1
        return RoundState(
            code = CODE,
            expiresAt = EXPIRY,
            revision = revision,
            phase = phase,
            isHost = iAmHost,
            participants = listOf(iAmIn, partnerIsIn).count { it },
            target = target,
            course = course,
            source = source,
            round = round,
            restrictions = restrictions,
            hasContributed = round in myContributions,
            waitingFor = listOf(round in myContributions, partnerContribution != null).count { !it },
            cardCount = offen.size,
            decidedCount = myVotes.keys.count { it in kennungen },
            othersDone = offen.isNotEmpty() && offen.all { it.recipeId in theirVotes },
            matchCount = matches.size,
            end = end,
            // Die Frist laeuft, sobald genau eine Seite beigetragen hat.
            contributionWaitMs = DEADLINE_MS.takeIf {
                phase == RoundPhase.SAMMELN && (round in myContributions) != (partnerContribution != null)
            },
            matches = matches
                .map { it.copy(hasContent = it.recipeId in partnerContents || it.recipeId in sharedContents) }
                .takeIf { phase == RoundPhase.FRAGE || phase == RoundPhase.BEENDET },
            stack = offen.takeIf { mitStapel },
            votes = myVotes.filterKeys { it in kennungen }.map { SharedVote(it.key, it.value) }.takeIf { mitStapel },
        )
    }

    companion object {
        const val CODE = "ABC123"
        const val EXPIRY = 9_999_999_999_999L
        const val PARTICIPANT = "test-teilnehmer"
        const val DEADLINE_MS = 60_000L
        private const val NO_STACK = Int.MIN_VALUE
    }
}
