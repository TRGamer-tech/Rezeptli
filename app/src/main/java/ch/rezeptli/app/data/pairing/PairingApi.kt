package ch.rezeptli.app.data.pairing

import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.RoundEnd
import ch.rezeptli.app.domain.multiplayer.RoundOffer
import ch.rezeptli.app.domain.multiplayer.RoundPhase
import ch.rezeptli.app.domain.multiplayer.RoundSource
import ch.rezeptli.app.domain.multiplayer.RoundState
import ch.rezeptli.app.domain.multiplayer.SharedContent
import ch.rezeptli.app.domain.multiplayer.SharedIngredient
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedVote
import ch.rezeptli.app.domain.plan.CourseChoice
import ch.rezeptli.app.domain.plan.DietaryFilter
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Uebersetzt zwischen dem Pairing-Dienst und den Modellen der App.
 *
 * Bewusst von Hand statt mit erzeugtem Code: Es sind wenige kleine Nachrichten, und so
 * bleibt sichtbar, was tatsaechlich ueber die Leitung geht - bei einem Dienst, der
 * Rezepttitel und Angaben zur Ernaehrung sieht, ist das keine Kleinigkeit.
 *
 * Gelesen wird nachsichtig: Ein unbekanntes Feld oder ein unbekannter Wert (die andere
 * Person hat vielleicht schon eine neuere App) bringt nichts zum Absturz, sondern faellt
 * auf eine vernuenftige Vorgabe zurueck.
 */
internal object PairingApi {
    // --------------------------------------------------------------------- was die App sendet

    fun createBody(participantId: String, restrictions: DietaryFilter): JsonObject = buildJsonObject {
        put("teilnehmer", participantId)
        put("einschraenkungen", restrictionsJson(restrictions))
    }

    fun joinBody(participantId: String, restrictions: DietaryFilter, knownRound: Int): JsonObject = buildJsonObject {
        put("teilnehmer", participantId)
        put("einschraenkungen", restrictionsJson(restrictions))
        put("kenntRunde", knownRound)
    }

    fun settingsBody(participantId: String, target: Int, course: CourseChoice, source: RoundSource): JsonObject =
        buildJsonObject {
            put("teilnehmer", participantId)
            put("ziel", target)
            put("art", course.wire)
            put("quelle", source.wire)
        }

    /** Fuer alles, was nur sagt "ich bin's": starten, weitermachen, aufhoeren, loeschen. */
    fun plainBody(participantId: String, knownRound: Int): JsonObject = buildJsonObject {
        put("teilnehmer", participantId)
        put("kenntRunde", knownRound)
    }

    fun contributionBody(participantId: String, round: Int, offers: List<RoundOffer>, knownRound: Int): JsonObject =
        buildJsonObject {
            put("teilnehmer", participantId)
            put("runde", round)
            put("kenntRunde", knownRound)
            put(
                "rezepte",
                buildJsonArray {
                    offers.forEach { offer ->
                        add(
                            buildJsonObject {
                                put("schluessel", offer.key)
                                put("titel", offer.title)
                                offer.sourceUrl?.let { put("quelleUrl", it) }
                                offer.imageUrl?.let { put("bildUrl", it) }
                                offer.prepTimeMinutes?.let { put("zubereitungszeit", it) }
                            },
                        )
                    }
                },
            )
        }

    fun votesBody(participantId: String, votes: List<SharedVote>, knownRound: Int): JsonObject = buildJsonObject {
        put("teilnehmer", participantId)
        put("kenntRunde", knownRound)
        put(
            "stimmen",
            buildJsonArray {
                votes.forEach { vote ->
                    add(
                        buildJsonObject {
                            put("rezeptId", vote.recipeId)
                            // `null` heisst: Entscheidung zurueckgenommen.
                            put("mag", vote.liked?.let { JsonPrimitive(it) } ?: JsonNull)
                        },
                    )
                }
            },
        )
    }

    /**
     * Der Inhalt eines selbst erfassten Rezepts, das ein gemeinsamer Treffer geworden ist.
     *
     * Das ist die einzige Stelle, an der Zutaten und Zubereitung das Geraet verlassen.
     */
    fun contentBody(participantId: String, recipeId: Long, content: SharedContent): JsonObject = buildJsonObject {
        put("teilnehmer", participantId)
        put("rezeptId", recipeId)
        put(
            "inhalt",
            buildJsonObject {
                put("zubereitung", content.instructions)
                put("schritte", buildJsonArray { content.steps.forEach { add(it) } })
                put(
                    "zutaten",
                    buildJsonArray {
                        content.ingredients.forEach { ingredient ->
                            add(
                                buildJsonObject {
                                    put("name", ingredient.name)
                                    ingredient.amount?.let { put("menge", it) }
                                    ingredient.unit?.let { put("einheit", it) }
                                    ingredient.note?.let { put("notiz", it) }
                                },
                            )
                        }
                    },
                )
                content.servings?.let { put("portionen", it) }
                content.prepTimeMinutes?.let { put("zeit", it) }
                put("tags", buildJsonArray { content.tags.forEach { add(it) } })
            },
        )
    }

    /**
     * Was nicht auf den Tisch darf, in der Form des Dienstes.
     *
     * Die drei Listen bleiben getrennt, weil sie Verschiedenes bedeuten: Eine Ernaehrungsform
     * ist eine Bedingung ("nur vegan"), Unvertraeglichkeiten und Abneigungen sind Ausschluesse.
     */
    private fun restrictionsJson(filter: DietaryFilter): JsonObject = buildJsonObject {
        put("ernaehrung", buildJsonArray { filter.diets.forEach { add(it.name) } })
        put("unvertraeglich", buildJsonArray { filter.intolerances.forEach { add(it.name) } })
        put("ohne", buildJsonArray { filter.dislikes.forEach { add(it.name) } })
        put("eigene", buildJsonArray { filter.customDislikes.forEach { add(it) } })
    }

    // --------------------------------------------------------------------- was der Dienst antwortet

    /** Hat sich seit dem bekannten Stand nichts getan? Dann schickt der Dienst nur diese Auskunft. */
    fun isUnchanged(body: JsonObject): Boolean = body.bool("unveraendert")

    fun stateFrom(body: JsonObject): RoundState = RoundState(
        code = body.string("code").orEmpty(),
        expiresAt = body.long("verfaelltAm") ?: 0L,
        revision = body.int("standNr") ?: 0,
        phase = phaseFrom(body.string("zustand")),
        isHost = body.bool("gastgeber"),
        participants = body.int("teilnehmer") ?: 1,
        target = (body.int("ziel") ?: RoundState.DEFAULT_TARGET).coerceIn(1, RoundState.MAX_TARGET),
        course = CourseChoice.entries.firstOrNull { it.wire == body.string("art") } ?: CourseChoice.ESSEN,
        source = RoundSource.entries.firstOrNull { it.wire == body.string("quelle") } ?: RoundSource.GESPEICHERT,
        round = body.int("runde") ?: 0,
        restrictions = restrictionsFrom(body["einschraenkungen"] as? JsonObject),
        hasContributed = body.bool("beigetragen"),
        waitingFor = body.int("wartetAuf") ?: 0,
        cardCount = body.int("karten") ?: 0,
        decidedCount = body.int("entschieden") ?: 0,
        othersDone = body.bool("andereFertig"),
        matchCount = body.int("trefferAnzahl") ?: 0,
        end = endFrom(body.string("endeGrund")),
        contributionWaitMs = body.long("fristRestMs"),
        matches = (body["treffer"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.toSharedRecipe() },
        stack = (body["stapel"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.toSharedRecipe() },
        votes = (body["stimmen"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.toVote() },
    )

    /** Der hinterlegte Inhalt eines Treffers - `null`, wenn die Antwort keinen brauchbaren enthaelt. */
    fun contentFrom(body: JsonObject): SharedContent? {
        val inhalt = body["inhalt"] as? JsonObject ?: return null
        val ingredients = (inhalt["zutaten"] as? JsonArray)
            ?.mapNotNull { eintrag ->
                val zeile = eintrag as? JsonObject ?: return@mapNotNull null
                val name = zeile.string("name") ?: return@mapNotNull null
                SharedIngredient(
                    name = name,
                    amount = zeile.double("menge"),
                    unit = zeile.string("einheit"),
                    note = zeile.string("notiz"),
                )
            }.orEmpty()
        val content = SharedContent(
            instructions = inhalt.string("zubereitung").orEmpty(),
            steps = inhalt.names("schritte"),
            ingredients = ingredients,
            servings = inhalt.int("portionen"),
            prepTimeMinutes = inhalt.int("zeit"),
            tags = inhalt.names("tags"),
        )
        val leer = content.ingredients.isEmpty() && content.instructions.isBlank() && content.steps.isEmpty()
        return content.takeUnless { leer }
    }

    /** Die Antwortcodes des Dienstes, uebersetzt in etwas, das die App erklaeren kann. */
    fun errorFor(statusCode: Int): PairingError = when (statusCode) {
        // 403: Dieses Geraet gehoert nicht (mehr) zur Runde - fuer die Person dasselbe wie "gibt es nicht".
        403, 404 -> PairingError.UNKNOWN_CODE
        409 -> PairingError.FULL
        410 -> PairingError.EXPIRED
        426 -> PairingError.OUTDATED
        else -> PairingError.UNKNOWN
    }

    private fun phaseFrom(value: String?): RoundPhase = when (value) {
        "lobby" -> RoundPhase.LOBBY
        "sammeln" -> RoundPhase.SAMMELN
        "laeuft" -> RoundPhase.LAEUFT
        "frage" -> RoundPhase.FRAGE
        "beendet" -> RoundPhase.BEENDET
        // Ein Zustand, den diese App nicht kennt: lieber warten als raten.
        else -> RoundPhase.SAMMELN
    }

    private fun endFrom(value: String?): RoundEnd? = when (value) {
        "ziel" -> RoundEnd.ZIEL
        "leer" -> RoundEnd.LEER
        "gastgeber" -> RoundEnd.GASTGEBER
        "verlassen" -> RoundEnd.VERLASSEN
        else -> null
    }

    private fun restrictionsFrom(body: JsonObject?): DietaryFilter {
        if (body == null) return DietaryFilter()
        return DietaryFilter(
            diets = body.names("ernaehrung").mapNotNullTo(linkedSetOf(), Diet::fromName),
            intolerances = body.names("unvertraeglich").mapNotNullTo(linkedSetOf(), Intolerance::fromName),
            dislikes = body.names("ohne").mapNotNullTo(linkedSetOf(), Dislike::fromName),
            customDislikes = body.names("eigene"),
        )
    }

    private fun JsonObject.toSharedRecipe(): SharedRecipe? {
        val id = long("rezeptId") ?: return null
        val title = string("titel") ?: return null
        return SharedRecipe(
            recipeId = id,
            title = title,
            sourceUrl = string("quelleUrl"),
            imageUrl = string("bildUrl"),
            prepTimeMinutes = int("zubereitungszeit"),
            key = string("schluessel").orEmpty(),
            isMine = bool("vonMir"),
            hasContent = bool("inhalt"),
        )
    }

    private fun JsonObject.toVote(): SharedVote? {
        val id = long("rezeptId") ?: return null
        return SharedVote(recipeId = id, liked = bool("mag"))
    }

    private val CourseChoice.wire: String
        get() = when (this) {
            CourseChoice.ESSEN -> "essen"
            CourseChoice.GETRAENKE -> "getraenke"
            CourseChoice.BEIDES -> "beides"
        }

    private val RoundSource.wire: String
        get() = when (this) {
            RoundSource.GESPEICHERT -> "gespeichert"
            RoundSource.NEU -> "neu"
        }

    /** JSON kennt `null` als Wort - das ist kein Inhalt. */
    private fun JsonElement?.primitive(): JsonPrimitive? = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }

    private fun JsonObject.string(name: String): String? = this[name].primitive()?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(name: String): Int? = this[name].primitive()?.content?.toIntOrNull()

    private fun JsonObject.long(name: String): Long? = this[name].primitive()?.content?.toLongOrNull()

    private fun JsonObject.double(name: String): Double? =
        this[name]
            .primitive()
            ?.content
            ?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0 }

    private fun JsonObject.bool(name: String): Boolean = this[name].primitive()?.content == "true"

    private fun JsonObject.names(name: String): List<String> =
        (this[name] as? JsonArray)?.mapNotNull { it.primitive()?.content?.takeIf(String::isNotBlank) }.orEmpty()
}
