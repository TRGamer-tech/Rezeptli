package ch.rezeptli.app.domain.parser

import ch.rezeptli.app.domain.model.IngredientNameNormalizer
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.SwissIngredientSynonyms
import javax.inject.Inject

/**
 * Regelbasierter Parser fuer Zutatenzeilen im Muster "<Menge> <Einheit> <Zutat>".
 *
 * Bewusst ohne Machine Learning und ohne Netzwerk: nachvollziehbar, offline und
 * deterministisch testbar. Der Parser raet nie - was er nicht sicher erkennt, markiert
 * er mit einer tieferen [ParseConfidence], damit die UI es hervorheben kann.
 *
 * Unterstuetzte Schreibweisen (Auswahl):
 * - "200 g Mehl", "200g Mehl", "1 dl Rahm", "2 EL Öl"
 * - "1/2 TL Salz", "½ TL Salz", "2 1/2 dl Milch", "1½ dl Milch"
 * - "2-3 EL Olivenöl" (untere Grenze wird uebernommen, Konfidenz sinkt)
 * - "ca. 200 g Mehl", "etwas Öl", "Salz nach Belieben"
 * - "1 Zwiebel, fein gehackt" (Zusatz wird als Notiz abgetrennt)
 */
class IngredientTextParser @Inject constructor() {
    /**
     * Parst einen mehrzeiligen Zutatenblock. Leerzeilen und Abschnittsueberschriften
     * ("Für den Teig:") werden uebersprungen.
     */
    fun parse(text: String): List<ParsedIngredient> =
        text
            .lineSequence()
            .mapNotNull { parseLine(it) }
            .toList()

    /**
     * Parst eine einzelne Zeile. Gibt `null` zurueck, wenn die Zeile keine Zutat ist
     * (leer, Ueberschrift oder reine Mengenangabe ohne Zutat).
     */
    fun parseLine(rawLine: String): ParsedIngredient? {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) return null

        var working = trimmed.replace(BULLET_PREFIX, "").trim().trimEnd(',', ';')
        working = working.replace(ORDERED_PREFIX, "").trim()
        if (working.isEmpty() || working.endsWith(":")) return null

        APPROXIMATE_PREFIX.find(working)?.let { match ->
            working = working.removeRange(match.range).trim()
        }

        val amountMatch = matchAmount(working)
        if (amountMatch != null) {
            working = working.substring(amountMatch.length).trim()
        }

        val unitMatch = matchUnit(working)
        var unit = IngredientUnit.NONE
        if (unitMatch != null) {
            if (working.length > unitMatch.length) {
                unit = unitMatch.unit
                working = working.substring(unitMatch.length).trim()
            } else if (amountMatch != null) {
                // "200 g" ohne Zutat: eine reine Mengenangabe ist keine verwertbare Zeile.
                return null
            }
            // Ohne Menge bleibt der Text der Zutatenname ("Prise" als eigene Zeile).
        }

        if (working.isEmpty()) return null

        var note: String? = null

        if (amountMatch == null) {
            QUANTIFIER_PREFIX.find(working)?.let { match ->
                note = match.value.trim().trimEnd('.')
                working = working.removeRange(match.range).trim()
            }
        }

        TRAILING_NOTE.find(working)?.let { match ->
            note = listOfNotNull(note, match.value.trim().trimStart(',', ' ')).joinToString(", ")
            working = working.removeRange(match.range).trim().trimEnd(',')
        }

        val commaIndex = working.indexOf(',')
        if (commaIndex > 0) {
            val candidateNote = working.substring(commaIndex + 1).trim()
            if (candidateNote.isNotEmpty() && looksLikePreparationHint(candidateNote)) {
                note = listOfNotNull(note, candidateNote).joinToString(", ")
                working = working.substring(0, commaIndex).trim()
            }
        }

        val name = working.trim().trim('.', '·', '-').trim()
        if (name.isEmpty()) return null

        return ParsedIngredient(
            rawLine = trimmed,
            name = name,
            amount = amountMatch?.value,
            unit = unit,
            note = note?.takeIf { it.isNotBlank() },
            canonicalName = SwissIngredientSynonyms.canonicalFor(name),
            confidence = confidenceOf(name, amountMatch?.value, unit, amountMatch?.approximate == true),
        )
    }

    /**
     * Strenge Pruefung: Beginnt die Zeile mit einem Aufzaehlungszeichen oder einer Menge?
     *
     * Damit erkennt der [RecipeTextParser] den Titel eines Rezepts zuverlaessig - ein
     * einzelnes Wort wie "Suppe" ist ohne diesen Test kaum von einer Zutat zu unterscheiden.
     */
    fun startsWithAmountOrBullet(rawLine: String): Boolean {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) return false
        if (BULLET_PREFIX.containsMatchIn(trimmed)) return true
        val withoutOrdering = trimmed.replace(ORDERED_PREFIX, "").trim()
        return matchAmount(withoutOrdering) != null
    }

    /**
     * Grobe Einschaetzung, ob eine Zeile ueberhaupt nach einer Zutat aussieht.
     * Wird vom [RecipeTextParser] genutzt, um Zutaten von Fliesstext zu trennen.
     */
    fun looksLikeIngredientLine(rawLine: String): Boolean {
        val parsed = parseLine(rawLine) ?: return false
        return parsed.confidence != ParseConfidence.LOW
    }

    private fun confidenceOf(
        name: String,
        amount: Double?,
        unit: IngredientUnit,
        approximate: Boolean,
    ): ParseConfidence {
        val wordCount = name.split(' ').count { it.isNotBlank() }
        if (wordCount > 8 || SENTENCE_MARKER.containsMatchIn(name)) return ParseConfidence.LOW

        val base = when {
            amount != null && unit != IngredientUnit.NONE -> ParseConfidence.HIGH
            amount != null && wordCount <= 4 -> ParseConfidence.HIGH
            amount != null -> ParseConfidence.MEDIUM
            wordCount <= 3 -> ParseConfidence.MEDIUM
            else -> ParseConfidence.LOW
        }

        return if (approximate) base.downgraded() else base
    }

    private fun ParseConfidence.downgraded(): ParseConfidence = when (this) {
        ParseConfidence.HIGH -> ParseConfidence.MEDIUM
        else -> ParseConfidence.LOW
    }

    private fun looksLikePreparationHint(text: String): Boolean {
        val normalized = IngredientNameNormalizer.normalize(text)
        if (normalized.isEmpty()) return false
        return PREPARATION_HINTS.any { hint -> normalized.contains(hint) }
    }

    private fun matchAmount(text: String): AmountMatch? {
        MIXED_FRACTION.find(text)?.let { match ->
            val (whole, numerator, denominator) = match.destructured
            val denominatorValue = denominator.toDouble()
            if (denominatorValue != 0.0) {
                return AmountMatch(whole.toDouble() + numerator.toDouble() / denominatorValue, match.value.length)
            }
        }
        NUMBER_WITH_GLYPH.find(text)?.let { match ->
            val (whole, glyph) = match.destructured
            val fraction = GLYPH_VALUES[glyph.first()]
            if (fraction != null) {
                return AmountMatch(whole.toDouble() + fraction, match.value.length)
            }
        }
        RANGE.find(text)?.let { match ->
            val lower = match.groupValues[1].toDecimal()
            if (lower != null) {
                return AmountMatch(lower, match.value.length, approximate = true)
            }
        }
        SIMPLE_FRACTION.find(text)?.let { match ->
            val (numerator, denominator) = match.destructured
            val denominatorValue = denominator.toDouble()
            if (denominatorValue != 0.0) {
                return AmountMatch(numerator.toDouble() / denominatorValue, match.value.length)
            }
        }
        GLYPH_ONLY.find(text)?.let { match ->
            val fraction = GLYPH_VALUES[match.value.first()]
            if (fraction != null) {
                return AmountMatch(fraction, match.value.length)
            }
        }
        DECIMAL.find(text)?.let { match ->
            val value = match.value.toDecimal()
            if (value != null) {
                return AmountMatch(value, match.value.length)
            }
        }
        return null
    }

    private fun matchUnit(text: String): UnitMatch? {
        val firstToken = LEADING_TOKEN.find(text)?.value ?: return null
        val afterFirst = text.substring(firstToken.length).trimStart()

        val secondToken = LEADING_TOKEN.find(afterFirst)?.value
        if (secondToken != null) {
            IngredientUnit.fromText("$firstToken $secondToken")?.let { unit ->
                val rest = afterFirst.substring(secondToken.length)
                return UnitMatch(unit, text.length - rest.length)
            }
        }

        IngredientUnit.fromText(firstToken)?.let { unit ->
            return UnitMatch(unit, firstToken.length)
        }
        return null
    }

    private fun String.toDecimal(): Double? = replace(',', '.').toDoubleOrNull()

    private data class AmountMatch(
        val value: Double,
        val length: Int,
        val approximate: Boolean = false,
    )

    private data class UnitMatch(
        val unit: IngredientUnit,
        val length: Int,
    )

    private companion object {
        const val GLYPHS = "½⅓⅔¼¾⅕⅛⅜⅝⅞"

        val GLYPH_VALUES: Map<Char, Double> = mapOf(
            '½' to 0.5,
            '⅓' to 1.0 / 3.0,
            '⅔' to 2.0 / 3.0,
            '¼' to 0.25,
            '¾' to 0.75,
            '⅕' to 0.2,
            '⅛' to 0.125,
            '⅜' to 0.375,
            '⅝' to 0.625,
            '⅞' to 0.875,
        )

        val BULLET_PREFIX = Regex("^[\\-–—*•·+>\\s]+")
        val ORDERED_PREFIX = Regex("^\\d{1,2}[.)]\\s+")
        val LEADING_TOKEN = Regex("^\\S+")

        val APPROXIMATE_PREFIX = Regex(
            "^(ca\\.?|zirka|circa|etwa|ungefähr|ungefaehr|knapp|gut)\\s+",
            RegexOption.IGNORE_CASE,
        )
        val QUANTIFIER_PREFIX = Regex(
            "^(ein wenig|ein paar|etwas|wenig|einige|einiges|reichlich|evtl\\.?|eventuell)\\s+",
            RegexOption.IGNORE_CASE,
        )
        val TRAILING_NOTE = Regex(
            ",?\\s*(nach Belieben|nach Geschmack|nach Bedarf|zum Bestreuen|zum Garnieren|" +
                "zum Servieren|zum Anrichten|zum Braten|zum Frittieren)\\s*$",
            RegexOption.IGNORE_CASE,
        )

        val MIXED_FRACTION = Regex("^(\\d+)\\s+(\\d+)\\s*[/⁄]\\s*(\\d+)")
        val NUMBER_WITH_GLYPH = Regex("^(\\d+)\\s*([$GLYPHS])")
        val RANGE = Regex("^(\\d+(?:[.,]\\d+)?)\\s*(?:[-–—]|bis)\\s*(\\d+(?:[.,]\\d+)?)")
        val SIMPLE_FRACTION = Regex("^(\\d+)\\s*[/⁄]\\s*(\\d+)")
        val GLYPH_ONLY = Regex("^[$GLYPHS]")
        val DECIMAL = Regex("^\\d+(?:[.,]\\d+)?")

        val SENTENCE_MARKER = Regex("[.!?]\\s+\\p{Lu}")

        val PREPARATION_HINTS = listOf(
            "gehackt",
            "geschnitten",
            "gerieben",
            "gewuerfelt",
            "gemahlen",
            "geschaelt",
            "gepresst",
            "halbiert",
            "geviertelt",
            "entkernt",
            "gehobelt",
            "in scheiben",
            "in streifen",
            "in wuerfel",
            "in stuecke",
            "in ringe",
            "fein",
            "grob",
            "frisch",
            "getrocknet",
            "weich",
            "zimmerwarm",
            "kalt",
            "warm",
            "optional",
            "gesalzen",
            "ungesalzen",
            "geputzt",
            "gewaschen",
            "abgetropft",
            "nach belieben",
            "nach geschmack",
            "zum bestreuen",
            "zum garnieren",
            "klein",
        )
    }
}
