package ch.rezeptli.app.domain.parser

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit

/**
 * Wie sicher der Parser bei einer Zeile ist.
 *
 * Die UI nutzt das, um unsichere Zeilen sichtbar zu markieren. Der Parser uebernimmt
 * nie stillschweigend Daten - jede Zeile bleibt vor dem Speichern editierbar.
 */
enum class ParseConfidence {
    /** Menge, Einheit und Name klar erkannt. */
    HIGH,

    /** Plausibel erkannt, aber mehrdeutig (z. B. Mengenbereich oder fehlende Menge). */
    MEDIUM,

    /** Sieht eher nach Fliesstext als nach einer Zutat aus. */
    LOW,
}

/**
 * Ergebnis des Parsens einer einzelnen Zutatenzeile.
 *
 * [rawLine] bleibt erhalten, damit die UI dem Nutzer immer zeigen kann, woraus ein
 * Eintrag entstanden ist.
 */
data class ParsedIngredient(
    val rawLine: String,
    val name: String,
    val amount: Double? = null,
    val unit: IngredientUnit = IngredientUnit.NONE,
    val note: String? = null,
    val canonicalName: String? = null,
    val confidence: ParseConfidence = ParseConfidence.HIGH,
) {
    fun toIngredient(position: Int = 0): Ingredient =
        Ingredient(
            name = name,
            amount = amount,
            unit = unit,
            note = note,
            canonicalName = canonicalName,
            position = position,
        )
}
