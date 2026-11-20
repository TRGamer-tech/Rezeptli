package ch.rezeptli.app.domain.multiplayer

import ch.rezeptli.app.domain.deck.TitleKey
import ch.rezeptli.app.domain.deck.UrlKey
import ch.rezeptli.app.domain.plan.CourseChoice
import ch.rezeptli.app.domain.plan.DietaryFilter

/**
 * Eine Karte der gemeinsamen Runde, wie beide sie sehen.
 *
 * Bewusst wenig: Geteilt wird nur, was zum Entscheiden noetig ist - Titel, Bild und Zeit.
 * Zutaten und Zubereitung bleiben auf dem Geraet - mit einer Ausnahme: Wird ein selbst
 * erfasstes Rezept zum gemeinsamen Treffer, hinterlegt sein Geraet den Inhalt beim Dienst,
 * damit die andere Person es uebernehmen kann (siehe [SharedContent]).
 *
 * [recipeId] vergibt der Dienst und gilt nur in dieser Runde: Die Rezeptnummern der beiden
 * Telefone beginnen beide bei 1 und sagen einander nichts. Wiedererkannt wird ein Rezept
 * am [key] (siehe [RoundKeys]).
 */
data class SharedRecipe(
    val recipeId: Long,
    val title: String,
    val sourceUrl: String? = null,
    val imageUrl: String? = null,
    val prepTimeMinutes: Int? = null,
    val key: String = "",
    /** Hat dieses Geraet die Karte beigesteuert? */
    val isMine: Boolean = false,
    /** Nur bei Treffern: Liegt der Inhalt eines selbst erfassten Rezepts beim Dienst bereit? */
    val hasContent: Boolean = false,
) {
    /** Selbst erfasst: Es gibt keine Adresse, von der sich das Rezept holen liesse. */
    val isHandwritten: Boolean get() = sourceUrl == null
}

/**
 * Zutaten und Zubereitung eines selbst erfassten Rezepts - fuer die Uebergabe an die andere
 * Person, wenn es ein gemeinsamer Treffer geworden ist.
 *
 * Ohne Foto: Das liegt als Datei auf einem Telefon und waere fuer den kurzlebigen Dienst
 * zu gross. Einheiten reisen als Name der Aufzaehlung ("GRAMM"); eine, die die andere App
 * nicht kennt, faellt beim Lesen weg, die Zutat bleibt.
 */
data class SharedContent(
    val instructions: String = "",
    val steps: List<String> = emptyList(),
    val ingredients: List<SharedIngredient> = emptyList(),
    val servings: Int? = null,
    val prepTimeMinutes: Int? = null,
    val tags: List<String> = emptyList(),
)

data class SharedIngredient(
    val name: String,
    val amount: Double? = null,
    val unit: String? = null,
    val note: String? = null,
)

/** Eine Karte, die ein Geraet zum Stapel beisteuert - noch ohne Nummer, die vergibt der Dienst. */
data class RoundOffer(
    val key: String,
    val title: String,
    val sourceUrl: String? = null,
    val imageUrl: String? = null,
    val prepTimeMinutes: Int? = null,
)

/** Eine Entscheidung zu einer Karte. `liked = null` nimmt sie zurueck. */
data class SharedVote(
    val recipeId: Long,
    val liked: Boolean?,
)

/** Wo die Runde steht - so, wie der Dienst es fuehrt. */
enum class RoundPhase {
    /** Der Code steht; es wird festgelegt, was gesucht wird. */
    LOBBY,

    /** Beide Geraete steuern ihre Karten bei. */
    SAMMELN,

    /** Es wird gewischt. */
    LAEUFT,

    /** Der Stapel ist durch, das Ziel nicht erreicht: weitermachen oder aufhoeren? */
    FRAGE,

    BEENDET,
}

/** Woher die Karten einer Runde kommen. */
enum class RoundSource {
    /** Aus den gespeicherten Rezepten beider. */
    GESPEICHERT,

    /** Frisch aus dem Rezeptverzeichnis. */
    NEU,
}

/** Warum eine Runde zu Ende ist. */
enum class RoundEnd {
    /** Die gewuenschte Anzahl ist gefunden. */
    ZIEL,

    /** Es gab keine passenden Rezepte mehr. */
    LEER,

    /** Wer eroeffnet hat, hat aufgehoert. */
    GASTGEBER,

    /** Die eingeladene Person hat aufgehoert. */
    VERLASSEN,
}

/**
 * Der Stand der Runde aus Sicht dieses Geraets.
 *
 * [stack] und [votes] haengen nur dran, wenn das Geraet den Stapel der Runde noch nicht hat;
 * [matches] nur, wenn nicht mehr gewischt wird. Waehrend der Runde gibt es bloss
 * [matchCount] - am einzelnen Treffer liesse sich sonst ablesen, wie die andere Person
 * gerade gewischt hat.
 */
data class RoundState(
    val code: String,
    val expiresAt: Long = 0L,
    /** Zaehlt jede Aenderung beim Dienst - ein aelterer Stand ueberschreibt nie einen neueren. */
    val revision: Int = 0,
    val phase: RoundPhase = RoundPhase.LOBBY,
    val isHost: Boolean = false,
    val participants: Int = 1,
    /** Wie viele gemeinsame Gerichte gesucht werden. Legt fest, wer eroeffnet hat. */
    val target: Int = DEFAULT_TARGET,
    val course: CourseChoice = CourseChoice.ESSEN,
    val source: RoundSource = RoundSource.GESPEICHERT,
    val round: Int = 0,
    /** Was nicht auf den Tisch darf - die Angaben beider, vereinigt. Gilt fuer beide. */
    val restrictions: DietaryFilter = DietaryFilter(),
    /** Hat dieses Geraet seine Karten zur laufenden Runde schon beigesteuert? */
    val hasContributed: Boolean = false,
    /** Auf wie viele Beitraege der Dienst noch wartet. */
    val waitingFor: Int = 0,
    val cardCount: Int = 0,
    val decidedCount: Int = 0,
    /** Hat die andere Person alle Karten der Runde entschieden? */
    val othersDone: Boolean = false,
    val matchCount: Int = 0,
    val end: RoundEnd? = null,
    /**
     * Wie lange der Dienst noch auf fehlende Beitraege wartet, in Millisekunden ab diesem
     * Stand - `null`, solange niemand beigetragen hat. Danach beginnt die Runde mit den
     * Karten, die da sind.
     */
    val contributionWaitMs: Long? = null,
    val matches: List<SharedRecipe>? = null,
    val stack: List<SharedRecipe>? = null,
    val votes: List<SharedVote>? = null,
) {
    /** Es fehlt noch jemand - der Einladungscode sollte also noch sichtbar sein. */
    val isWaitingForPartner: Boolean get() = participants < 2

    companion object {
        const val DEFAULT_TARGET = 3
        const val MAX_TARGET = 20
    }
}

/** Was das regelmaessige Nachfragen ergeben hat. */
sealed interface RoundPoll {
    data class Changed(val state: RoundState) : RoundPoll

    /** Der Dienst ist erreichbar, aber es hat sich nichts getan. */
    data object Quiet : RoundPoll

    data class Problem(val error: PairingError) : RoundPoll
}

/**
 * Woran ein Rezept in einer Runde wiedererkannt wird.
 *
 * Ein Rezept aus dem Netz traegt den Fingerabdruck seiner Adresse, ein selbst erfasstes den
 * seines Titels. Kennen beide ein Rezept, hat es deshalb auf beiden Telefonen denselben
 * Schluessel und liegt nur einmal im Stapel - auch ein selbst erfasstes, das die eine Person
 * in einer frueheren Runde von der anderen uebernommen hat. Frueher nannte der Schluessel
 * eines selbst erfassten Rezepts das Geraet und die dortige Nummer; dasselbe Gericht lag dann
 * zweimal im Stapel, sobald beide es hatten.
 */
object RoundKeys {
    fun forUrl(url: String): String = "u" + fingerprint(UrlKey.of(url))

    fun forTitle(title: String): String = "t" + fingerprint(TitleKey.of(title))

    /**
     * FNV-1a ueber 64 Bit. Kein Geheimnis und keine Sicherheit - nur ein kurzer, auf beiden
     * Geraeten gleicher Name fuer eine lange Adresse.
     */
    private fun fingerprint(text: String): String {
        var hash = FNV_OFFSET
        text.encodeToByteArray().forEach { byte ->
            hash = hash xor (byte.toLong() and BYTE_MASK)
            hash *= FNV_PRIME
        }
        return java.lang.Long
            .toHexString(hash)
            .padStart(HEX_LENGTH, '0')
    }

    private const val HEX_LENGTH = 16
    private const val BYTE_MASK = 0xffL
    private const val FNV_PRIME = 0x100000001b3L
    private const val FNV_OFFSET = -0x340d631b7bdddcdbL
}

/** Warum etwas mit der Runde nicht geklappt hat - in Worten, die die App zeigen kann. */
enum class PairingError {
    NO_CONNECTION,

    /** Den Code gibt es nicht (oder er wurde vertippt), oder die Runde wurde geloescht. */
    UNKNOWN_CODE,

    /** Die Runde ist abgelaufen. */
    EXPIRED,

    /** Es sind schon zwei dabei, oder die Runde laeuft bereits. */
    FULL,

    /** Eine der beiden Apps ist zu alt fuer diese Runde. */
    OUTDATED,

    UNKNOWN,
}

sealed interface PairingResult<out T> {
    data class Success<T>(val value: T) : PairingResult<T>

    data class Failure(val error: PairingError) : PairingResult<Nothing>
}
