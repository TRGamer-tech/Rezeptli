package ch.rezeptli.app.domain.parser

import ch.rezeptli.app.domain.model.IngredientNameNormalizer
import ch.rezeptli.app.domain.model.SuggestedTags
import javax.inject.Inject

/**
 * Ergebnis des Imports eines kompletten Rezepttexts.
 *
 * Alle Felder sind Vorschlaege - die Import-UI zeigt sie zur Kontrolle an, bevor
 * gespeichert wird.
 */
data class ParsedRecipe(
    val title: String = "",
    val ingredients: List<ParsedIngredient> = emptyList(),
    val instructions: String = "",
    val tags: List<String> = emptyList(),
    val prepTimeMinutes: Int? = null,
) {
    val isEmpty: Boolean
        get() = title.isBlank() && ingredients.isEmpty() && instructions.isBlank()
}

/**
 * Zerlegt einen kompletten, aus einer anderen Quelle kopierten Rezepttext
 * (z. B. aus Google Docs) in Titel, Zutaten, Zubereitung, Tags und Zubereitungszeit.
 *
 * Das Vorgehen ist bewusst konservativ: Erkannte Abschnittsueberschriften steuern die
 * Zuordnung, und wo keine Ueberschrift vorhanden ist, gilt eine Zeile nur dann als
 * Zutat, wenn [IngredientTextParser] sie sicher genug erkennt. Alles andere landet in
 * der Zubereitung, wo es sichtbar bleibt statt verloren zu gehen.
 */
class RecipeTextParser @Inject constructor(
    private val ingredientParser: IngredientTextParser,
) {
    fun parse(text: String): ParsedRecipe {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')

        var section = Section.UNKNOWN
        var title = ""
        val ingredients = mutableListOf<ParsedIngredient>()
        val instructions = mutableListOf<String>()
        var prepTimeMinutes: Int? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                if (section == Section.INSTRUCTIONS && instructions.isNotEmpty()) {
                    instructions.add("")
                }
                continue
            }

            val headerSection = sectionForHeader(line)
            if (headerSection != null) {
                section = headerSection
                continue
            }

            // Meta-Angaben (Portionen, Zeiten, Quelle) sind keine Zutaten und kein Fliesstext.
            // Nur aus ihnen wird die Zubereitungszeit gelesen - so wird aus einem
            // "20 Minuten koecheln lassen" in der Zubereitung nie versehentlich eine Zeitangabe.
            if (isMetaLine(line)) {
                if (prepTimeMinutes == null) {
                    prepTimeMinutes = extractPrepTime(line)
                }
                continue
            }

            when (section) {
                Section.INGREDIENTS -> addIngredientOrFallback(line, ingredients, instructions)

                Section.INSTRUCTIONS -> instructions.add(line)

                Section.UNKNOWN -> when {
                    title.isEmpty() && !ingredientParser.startsWithAmountOrBullet(line) -> title = line
                    ingredientParser.looksLikeIngredientLine(line) ->
                        addIngredientOrFallback(line, ingredients, instructions)

                    else -> instructions.add(line)
                }
            }
        }

        val instructionText = instructions
            .joinToString("\n")
            .trim()
            .replace(Regex("\n{3,}"), "\n\n")

        return ParsedRecipe(
            title = title.removeSuffix(":").trim(),
            ingredients = ingredients,
            instructions = instructionText,
            tags = detectTags(text, prepTimeMinutes),
            prepTimeMinutes = prepTimeMinutes,
        )
    }

    /**
     * Uebernimmt eine Zeile als Zutat - aber nur, wenn der Parser sie sicher genug
     * erkennt. Alles andere landet in der Zubereitung, damit keine Zeile verloren geht.
     */
    private fun addIngredientOrFallback(
        line: String,
        ingredients: MutableList<ParsedIngredient>,
        instructions: MutableList<String>,
    ) {
        val parsed = ingredientParser.parseLine(line)
        if (parsed != null && parsed.confidence != ParseConfidence.LOW) {
            ingredients.add(parsed)
        } else {
            instructions.add(line)
        }
    }

    private fun sectionForHeader(line: String): Section? {
        val normalized = IngredientNameNormalizer.normalize(line.removeSuffix(":"))
        if (normalized.isEmpty()) return null
        if (normalized in INGREDIENT_HEADERS) return Section.INGREDIENTS
        if (normalized in INSTRUCTION_HEADERS) return Section.INSTRUCTIONS
        if (line.endsWith(":") && INGREDIENT_HEADER_PREFIXES.any { normalized.startsWith(it) }) {
            return Section.INGREDIENTS
        }
        return null
    }

    private fun isMetaLine(line: String): Boolean {
        if (TIME_ONLY_LINE.matches(line.trim())) return true
        val normalized = IngredientNameNormalizer.normalize(line)
        return META_KEYWORDS.any { normalized.startsWith(it) }
    }

    private fun extractPrepTime(line: String): Int? {
        HOURS_AND_MINUTES.find(line)?.let { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: return@let
            val minutes = match.groupValues[2].toIntOrNull() ?: 0
            return hours * MINUTES_PER_HOUR + minutes
        }
        HOURS.find(line)?.let { match ->
            val hours = match.groupValues[1].toIntOrNull() ?: return@let
            return hours * MINUTES_PER_HOUR
        }
        MINUTES.find(line)?.let { match ->
            return match.groupValues[1].toIntOrNull()
        }
        return null
    }

    private fun detectTags(text: String, prepTimeMinutes: Int?): List<String> {
        val normalized = IngredientNameNormalizer.normalize(text)
        val tags = SuggestedTags.ALL
            .filter { tag -> normalized.contains(IngredientNameNormalizer.normalize(tag)) }
            .toMutableList()

        if (prepTimeMinutes != null &&
            prepTimeMinutes <= FAST_RECIPE_MINUTES &&
            SuggestedTags.SCHNELL !in tags
        ) {
            tags.add(SuggestedTags.SCHNELL)
        }
        return tags
    }

    private enum class Section {
        UNKNOWN,
        INGREDIENTS,
        INSTRUCTIONS,
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val FAST_RECIPE_MINUTES = 30

        val INGREDIENT_HEADERS = setOf(
            "zutaten",
            "zutatenliste",
            "einkaufsliste",
            "das brauchst du",
            "du brauchst",
            "ingredients",
        )

        val INSTRUCTION_HEADERS = setOf(
            "zubereitung",
            "anleitung",
            "zubereitungsschritte",
            "schritte",
            "vorgehen",
            "so gehts",
            "und so gehts",
            "arbeitsschritte",
            "instructions",
        )

        val INGREDIENT_HEADER_PREFIXES = listOf("fuer den", "fuer die", "fuer das", "fuer")

        val META_KEYWORDS = listOf(
            "portionen",
            "portion",
            "menge",
            "ergibt",
            "zubereitungszeit",
            "arbeitszeit",
            "gesamtzeit",
            "kochzeit",
            "backzeit",
            "ruhezeit",
            "dauer",
            "schwierigkeit",
            "quelle",
        )

        val TIME_ONLY_LINE = Regex(
            "^\\d+\\s*(?:min\\.?|minuten?|h|std\\.?|stunden?)$",
            RegexOption.IGNORE_CASE,
        )
        val HOURS_AND_MINUTES = Regex(
            "(\\d+)\\s*(?:h\\b|std\\.?|stunden?)\\s*(\\d+)?\\s*(?:min\\.?|minuten?)?",
            RegexOption.IGNORE_CASE,
        )
        val HOURS = Regex("(\\d+)\\s*(?:h\\b|std\\.?|stunden?)", RegexOption.IGNORE_CASE)
        val MINUTES = Regex("(\\d+)\\s*(?:min\\b|min\\.|minuten?)", RegexOption.IGNORE_CASE)
    }
}
