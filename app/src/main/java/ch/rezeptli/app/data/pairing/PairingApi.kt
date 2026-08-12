package ch.rezeptli.app.data.pairing

import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedSession
import ch.rezeptli.app.domain.multiplayer.SharedSessionState
import ch.rezeptli.app.domain.multiplayer.SharedVote
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Uebersetzt zwischen dem Pairing-Dienst und den Modellen der App.
 *
 * Bewusst von Hand statt mit erzeugtem Code: Es sind vier kleine Nachrichten, und so
 * bleibt sichtbar, was tatsaechlich ueber die Leitung geht - bei einem Dienst, der
 * Rezepttitel sieht, ist das keine Kleinigkeit.
 */
internal object PairingApi {
    fun createBody(participantId: String, recipes: List<SharedRecipe>): JsonObject = buildJsonObject {
        put("gastgeber", participantId)
        put(
            "rezepte",
            buildJsonArray {
                recipes.forEach { recipe ->
                    add(
                        buildJsonObject {
                            put("rezeptId", recipe.recipeId)
                            put("titel", recipe.title)
                            recipe.sourceUrl?.let { put("quelleUrl", it) }
                            recipe.imageUrl?.let { put("bildUrl", it) }
                            recipe.prepTimeMinutes?.let { put("zubereitungszeit", it) }
                        },
                    )
                }
            },
        )
    }

    fun joinBody(participantId: String): JsonObject = buildJsonObject {
        put("teilnehmer", participantId)
    }

    fun votesBody(participantId: String, votes: List<SharedVote>, finished: Boolean): JsonObject =
        buildJsonObject {
            put("teilnehmer", participantId)
            put("fertig", finished)
            put(
                "stimmen",
                buildJsonArray {
                    votes.forEach { vote ->
                        add(
                            buildJsonObject {
                                put("rezeptId", vote.recipeId)
                                put("mag", vote.liked)
                            },
                        )
                    }
                },
            )
        }

    /**
     * Liest die Antwort auf "Runde eroeffnen" oder "beitreten".
     *
     * Die beiden Antworten sehen im Feld `rezepte` verschieden aus: Beim Beitreten
     * steht dort die Liste, beim Eroeffnen nur deren Anzahl - der Gastgeber hat die
     * Rezepte ja gerade selbst geschickt. Wer das Feld blind als Liste liest, bekommt
     * beim Eroeffnen eine Ausnahme, und die Einladung scheitert mit einer nichts
     * sagenden Fehlermeldung. Deshalb [fallback]: was der Gastgeber gesendet hat.
     */
    fun sessionFrom(body: JsonObject, fallback: List<SharedRecipe> = emptyList()): SharedSession =
        SharedSession(
            code = body["code"]?.jsonPrimitive?.content.orEmpty(),
            expiresAt = body["verfaelltAm"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            recipes = (body["rezepte"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.toSharedRecipe() }
                ?: fallback,
        )

    fun stateFrom(body: JsonObject): SharedSessionState = SharedSessionState(
        code = body["code"]?.jsonPrimitive?.content.orEmpty(),
        participants = body["teilnehmer"]?.jsonPrimitive?.intOrZero() ?: 0,
        finished = body["fertig"]?.jsonPrimitive?.intOrZero() ?: 0,
        allFinished = body["alleFertig"]?.jsonPrimitive?.content == "true",
        matches = body["treffer"]?.jsonArray?.mapNotNull { it.jsonObject.toSharedRecipe() }.orEmpty(),
        expiresAt = body["verfaelltAm"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
    )

    /** Die Antwortcodes des Dienstes, uebersetzt in etwas, das die App erklaeren kann. */
    fun errorFor(statusCode: Int): PairingError = when (statusCode) {
        404 -> PairingError.UNKNOWN_CODE
        409 -> PairingError.FULL
        410 -> PairingError.EXPIRED
        400, 413 -> PairingError.NO_RECIPES
        else -> PairingError.UNKNOWN
    }

    private fun JsonObject.toSharedRecipe(): SharedRecipe? {
        val id = this["rezeptId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        val title = this["titel"]?.jsonPrimitive?.content ?: return null
        return SharedRecipe(
            recipeId = id,
            title = title,
            sourceUrl = this["quelleUrl"]?.jsonPrimitive?.contentOrNull(),
            imageUrl = this["bildUrl"]?.jsonPrimitive?.contentOrNull(),
            prepTimeMinutes = this["zubereitungszeit"]?.jsonPrimitive?.content?.toIntOrNull(),
        )
    }

    private fun JsonPrimitive.intOrZero(): Int = content.toIntOrNull() ?: 0

    /** JSON kennt `null` als Wort - das ist kein Inhalt. */
    private fun JsonPrimitive.contentOrNull(): String? = content.takeIf { it != "null" && it.isNotBlank() }
}
