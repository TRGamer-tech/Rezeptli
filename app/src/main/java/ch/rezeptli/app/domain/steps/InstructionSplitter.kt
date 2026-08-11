package ch.rezeptli.app.domain.steps

import javax.inject.Inject

/**
 * Zerlegt eine Zubereitung in einzelne Schritte.
 *
 * Reihenfolge der Versuche, vom Verlaesslichsten zum Unsichersten:
 *
 * 1. Nummerierung ("1.", "2)", "Schritt 3:") - wer so schreibt, hat die Schritte
 *    selbst gesetzt, und daran wird nichts geraten.
 * 2. Leerzeilen - der uebliche Absatz.
 * 3. Einzelne Zeilenumbrueche, wenn es mehrere gibt.
 * 4. Saetze, aber nur bei laengeren Texten. Ein einzelner Satz bleibt ein Schritt.
 *
 * Das Ergebnis ist ein *Vorschlag*. Wo er in der App auftaucht, laesst er sich vor dem
 * Speichern korrigieren - dieselbe Regel wie beim Zutaten-Parser: nie stillschweigend
 * uebernehmen.
 */
class InstructionSplitter @Inject constructor() {
    fun split(instructions: String): List<RecipeStep> {
        val text = instructions.trim()
        if (text.isEmpty()) return emptyList()

        val parts = splitNumbered(text)
            ?: splitOn(text, BLANK_LINE)
            ?: splitOn(text, NEWLINE)
            ?: splitSentences(text)
            ?: listOf(text)

        return parts
            .map { it.trim().removeLeadingMarker() }
            .filter { it.isNotBlank() }
            .mapIndexed { index, part ->
                RecipeStep(
                    position = index,
                    text = part,
                    timerMinutes = timerMinutesIn(part),
                )
            }
    }

    /**
     * Erkennt eine Wartezeit im Schritt.
     *
     * Nur Zeitangaben zaehlen - "bei 200 Grad" oder "3 EL" duerfen keinen Timer
     * ausloesen. Steht mehr als eine Zeit im Schritt, gewinnt die erste: "20 Minuten
     * backen, dann 5 Minuten ruhen lassen" soll den Backvorgang stellen.
     */
    fun timerMinutesIn(step: String): Int? {
        val match = DURATION.find(step) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val isHours = match.groupValues[2].lowercase().startsWith("st") ||
            match.groupValues[2].lowercase() == "h"

        val minutes = if (isHours) amount * MINUTES_PER_HOUR else amount
        return minutes.toInt().takeIf { it in 1..MAX_TIMER_MINUTES }
    }

    /** Schritte, die mit einer Nummer beginnen - daran laesst sich sicher trennen. */
    private fun splitNumbered(text: String): List<String>? {
        val matches = NUMBERED_START.findAll(text).toList()
        if (matches.size < 2) return null

        return matches.mapIndexed { index, match ->
            val end = matches.getOrNull(index + 1)?.range?.first ?: text.length
            text.substring(match.range.first, end)
        }
    }

    private fun splitOn(text: String, pattern: Regex): List<String>? {
        val parts = text.split(pattern).map { it.trim() }.filter { it.isNotBlank() }
        return parts.takeIf { it.size >= 2 }
    }

    /**
     * Trennt nach Saetzen - aber nur, wenn der Text lang genug ist, dass eine
     * Aufteilung ueberhaupt hilft. Sonst entstehen Karten mit drei Woertern.
     */
    private fun splitSentences(text: String): List<String>? {
        if (text.length < MIN_LENGTH_FOR_SENTENCES) return null
        val parts = SENTENCE_END
            .split(text)
            .map { it.trim() }
            .filter { it.length >= MIN_SENTENCE_LENGTH }
        return parts.takeIf { it.size >= 2 }
    }

    private fun String.removeLeadingMarker(): String =
        replaceFirst(LEADING_MARKER, "").trim()

    private companion object {
        val BLANK_LINE = Regex("\\n\\s*\\n")
        val NEWLINE = Regex("\\n")

        /** "1. ", "2) ", "Schritt 3:" - jeweils am Zeilenanfang. */
        val NUMBERED_START = Regex(
            "(?m)^\\s*(?:schritt\\s*)?\\d{1,2}\\s*[.):]\\s+",
            RegexOption.IGNORE_CASE,
        )
        val LEADING_MARKER = Regex(
            "^\\s*(?:schritt\\s*)?\\d{1,2}\\s*[.):]\\s*|^\\s*[-•*]\\s*",
            RegexOption.IGNORE_CASE,
        )

        /** Satzende, aber nicht bei Abkuerzungen wie "ca." oder "z.B.". */
        val SENTENCE_END = Regex("(?<=[a-zäöüßé])[.!?]\\s+(?=[A-ZÄÖÜ])")

        val DURATION = Regex(
            "(\\d+(?:[.,]\\d+)?)\\s*(minuten|minute|min\\.?|std\\.?|stunden|stunde|h)\\b",
            RegexOption.IGNORE_CASE,
        )

        const val MINUTES_PER_HOUR = 60
        const val MAX_TIMER_MINUTES = 24 * 60
        const val MIN_LENGTH_FOR_SENTENCES = 180
        const val MIN_SENTENCE_LENGTH = 12
    }
}
