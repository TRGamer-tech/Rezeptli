package ch.rezeptli.app.data.web

import ch.rezeptli.app.domain.model.WebRecipe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.inject.Inject

/**
 * Liest ein Rezept aus den strukturierten Daten einer Webseite.
 *
 * Bewusst quellenunabhaengig: Ausgewertet wird das, was die Seiten fuer Suchmaschinen
 * ohnehin mitliefern (schema.org), nicht ihr HTML-Geruest. Damit funktioniert derselbe
 * Code fuer alle Quellen, und ein Redesign der Seite bricht den Import nicht.
 *
 * Drei Strategien, in dieser Reihenfolge:
 * 1. JSON-LD vom Typ `Recipe` - der Normalfall (Betty Bossi, Gutekueche, Migusto).
 * 2. JSON-LD vom Typ `HowTo` fuer Titel und Zubereitung, kombiniert mit
 *    Microdata-Zutaten - so liefert Swissmilk seine Rezepte aus.
 * 3. Reine Microdata nach schema.org als Rueckfall.
 */
class StructuredRecipeExtractor @Inject constructor() {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun extract(html: String, sourceUrl: String, sourceName: String): WebRecipe? {
        val document = Jsoup.parse(html, sourceUrl)
        val jsonLd = document
            .select("script[type=application/ld+json]")
            .mapNotNull { element -> runCatching { json.parseToJsonElement(element.data()) }.getOrNull() }

        return fromRecipeNode(jsonLd, document, sourceUrl, sourceName)
            ?: fromHowTo(jsonLd, document, sourceUrl, sourceName)
            ?: fromMicrodata(document, sourceUrl, sourceName)
    }

    // ------------------------------------------------------------------ JSON-LD Recipe

    private fun fromRecipeNode(
        jsonLd: List<JsonElement>,
        document: Document,
        sourceUrl: String,
        sourceName: String,
    ): WebRecipe? {
        val recipe = jsonLd.firstNotNullOfOrNull { findNodeOfType(it, "Recipe") } ?: return null

        val ingredients = recipe
            .stringList("recipeIngredient")
            .ifEmpty { recipe.stringList("ingredients") }
            .ifEmpty { document.microdataIngredients() }

        return WebRecipe(
            title = recipe.stringOrNull("name")?.cleanText().orEmpty(),
            ingredientLines = ingredients,
            instructions = recipe["recipeInstructions"].toInstructions(),
            imageUrl = recipe["image"].toImageUrl(),
            totalMinutes = IsoDuration.toMinutes(recipe.stringOrNull("totalTime"))
                ?: sumOf(recipe.stringOrNull("prepTime"), recipe.stringOrNull("cookTime")),
            servings = recipe["recipeYield"].toYield(),
            keywords = recipe.toKeywords(),
            sourceUrl = sourceUrl,
            sourceName = sourceName,
        ).takeIf { it.isUsable }
    }

    // ------------------------------------------------------------------ JSON-LD HowTo

    private fun fromHowTo(
        jsonLd: List<JsonElement>,
        document: Document,
        sourceUrl: String,
        sourceName: String,
    ): WebRecipe? {
        val howTo = jsonLd.firstNotNullOfOrNull { findNodeOfType(it, "HowTo") } ?: return null

        return WebRecipe(
            title = howTo.stringOrNull("name")?.cleanText().orEmpty(),
            ingredientLines = howTo
                .stringList("supply")
                .ifEmpty { document.microdataIngredients() },
            instructions = howTo["step"].toInstructions(),
            imageUrl = howTo["image"].toImageUrl() ?: document.metaImage(),
            totalMinutes = IsoDuration.toMinutes(howTo.stringOrNull("totalTime")),
            servings = howTo["yield"].toYield(),
            keywords = howTo.toKeywords(),
            sourceUrl = sourceUrl,
            sourceName = sourceName,
        ).takeIf { it.isUsable }
    }

    // ------------------------------------------------------------------ Microdata

    private fun fromMicrodata(document: Document, sourceUrl: String, sourceName: String): WebRecipe? {
        val ingredients = document.microdataIngredients()
        if (ingredients.isEmpty()) return null

        val title = document.selectFirst("[itemprop=name]")?.text()?.cleanText()
            ?: document.selectFirst("h1")?.text()?.cleanText()
            ?: document.title().cleanText()

        val instructions = document
            .select("[itemprop=recipeInstructions]")
            .joinToString("\n") { it.text().cleanText() }
            .trim()

        return WebRecipe(
            title = title,
            ingredientLines = ingredients,
            instructions = instructions,
            imageUrl = document.metaImage(),
            sourceUrl = sourceUrl,
            sourceName = sourceName,
        ).takeIf { it.isUsable }
    }

    private fun Document.microdataIngredients(): List<String> =
        select("[itemprop=recipeIngredient], [itemprop=ingredients]")
            .map { it.text().cleanText() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun Document.metaImage(): String? =
        selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }

    // ------------------------------------------------------------------ JSON-Hilfen

    /** Sucht rekursiv den ersten Knoten des gewuenschten Typs, auch in `@graph`. */
    private fun findNodeOfType(element: JsonElement, type: String): JsonObject? = when (element) {
        is JsonArray -> element.firstNotNullOfOrNull { findNodeOfType(it, type) }
        is JsonObject -> {
            if (element.typeNames().any { it.equals(type, ignoreCase = true) }) {
                element
            } else {
                CONTAINER_KEYS.firstNotNullOfOrNull { key ->
                    element[key]?.let { findNodeOfType(it, type) }
                }
            }
        }

        else -> null
    }

    private fun JsonObject.typeNames(): List<String> = when (val type = this["@type"]) {
        is JsonPrimitive -> listOf(type.contentOrEmpty())
        is JsonArray -> type.mapNotNull { (it as? JsonPrimitive)?.contentOrEmpty() }
        else -> emptyList()
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrEmpty()?.takeIf { it.isNotBlank() }

    private fun JsonObject.stringList(key: String): List<String> = when (val value = this[key]) {
        is JsonArray -> value.mapNotNull { it.toPlainText() }
        is JsonPrimitive -> listOfNotNull(value.contentOrEmpty().takeIf { it.isNotBlank() })
        else -> emptyList()
    }.map { it.cleanText() }.filter { it.isNotBlank() }

    private fun JsonObject.toKeywords(): List<String> {
        val raw = stringList("keywords") + stringList("recipeCategory") + stringList("recipeCuisine")
        return raw
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length <= MAX_KEYWORD_LENGTH }
            .distinctBy { it.lowercase() }
            .take(MAX_KEYWORDS)
    }

    /**
     * Zubereitungsschritte, wie sie in freier Wildbahn vorkommen: als einzelner Text,
     * als Liste von Texten, als `HowToStep` oder als `HowToSection` mit Unterschritten.
     */
    private fun JsonElement?.toInstructions(): String = when (this) {
        null -> ""
        is JsonPrimitive -> contentOrEmpty().cleanText()
        is JsonArray -> mapNotNull { it.toStepText() }.filter { it.isNotBlank() }.joinToString("\n")
        is JsonObject -> toStepText().orEmpty()
        else -> ""
    }.trim()

    private fun JsonElement.toStepText(): String? = when (this) {
        is JsonPrimitive -> contentOrEmpty().cleanText()
        is JsonObject -> {
            val nested = CONTAINER_KEYS.firstNotNullOfOrNull { key -> this[key] }
            when {
                this["text"] != null -> stringOrNull("text")?.cleanText()
                nested != null -> nested.toInstructions()
                else -> stringOrNull("name")?.cleanText()
            }
        }

        else -> null
    }

    private fun JsonElement?.toImageUrl(): String? = when (this) {
        null -> null
        is JsonPrimitive -> contentOrEmpty().takeIf { it.isNotBlank() }
        is JsonArray -> firstNotNullOfOrNull { it.toImageUrl() }
        is JsonObject -> stringOrNull("url") ?: stringOrNull("contentUrl")
        else -> null
    }

    private fun JsonElement?.toYield(): String? = when (this) {
        null -> null
        is JsonPrimitive -> contentOrEmpty().cleanText().takeIf { it.isNotBlank() }
        is JsonArray -> mapNotNull { it.toYield() }.maxByOrNull { it.length }
        else -> null
    }

    private fun JsonElement.toPlainText(): String? = when (this) {
        is JsonPrimitive -> contentOrEmpty()
        is JsonObject -> stringOrNull("name") ?: stringOrNull("text")
        else -> null
    }

    private fun JsonPrimitive.contentOrEmpty(): String = if (isString) content else content

    private fun sumOf(vararg durations: String?): Int? {
        val minutes = durations.mapNotNull { IsoDuration.toMinutes(it) }
        return minutes.takeIf { it.isNotEmpty() }?.sum()
    }

    /** Entfernt HTML-Reste, geschuetzte Leerzeichen und doppelte Abstaende. */
    private fun String.cleanText(): String = Jsoup
        .parse(this)
        .text()
        .replace(' ', ' ')
        .replace(WHITESPACE, " ")
        .trim()

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val CONTAINER_KEYS = listOf("@graph", "mainEntity", "itemListElement")
        const val MAX_KEYWORDS = 6
        const val MAX_KEYWORD_LENGTH = 30
    }
}
