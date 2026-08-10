package ch.rezeptli.app.domain.model

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/**
 * Formatiert Mengenangaben so, wie sie in Kochbuechern stehen: "2", "1½", "2,5".
 *
 * Reine Kotlin-Logik ohne Locale-Abhaengigkeit, damit sie deterministisch testbar ist.
 * Als Dezimaltrennzeichen wird das im deutschsprachigen Raum uebliche Komma verwendet.
 */
object AmountFormatter {
    private const val TOLERANCE = 0.005

    private val FRACTIONS: List<Pair<Double, String>> =
        listOf(
            0.125 to "⅛",
            0.25 to "¼",
            1.0 / 3.0 to "⅓",
            0.5 to "½",
            2.0 / 3.0 to "⅔",
            0.75 to "¾",
        )

    /** Formatiert [amount]; `null` ergibt einen leeren String. */
    fun format(amount: Double?): String {
        if (amount == null) return ""
        if (amount < 0) return "-" + format(-amount)

        val whole = floor(amount)
        val fraction = amount - whole

        if (fraction < TOLERANCE) return whole.toLong().toString()

        val glyph = FRACTIONS.firstOrNull { abs(fraction - it.first) < 0.01 }?.second
        if (glyph != null) {
            return if (whole >= 1.0) "${whole.toLong()}$glyph" else glyph
        }

        return decimal(amount)
    }

    /**
     * Formatiert einen Wert fuer Eingabefelder - dort sind Brueche unpraktisch,
     * deshalb immer die Dezimalschreibweise.
     */
    fun formatForInput(amount: Double?): String {
        if (amount == null) return ""
        return decimal(amount)
    }

    private fun decimal(amount: Double): String {
        val rounded = round(amount * 100.0) / 100.0
        if (abs(rounded - round(rounded)) < TOLERANCE) return round(rounded).toLong().toString()
        return rounded
            .toString()
            .trimEnd('0')
            .trimEnd('.')
            .replace('.', ',')
    }
}
