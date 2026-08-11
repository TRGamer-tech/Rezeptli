package ch.rezeptli.app.data.web

/**
 * Liest Zeitangaben aus strukturierten Daten.
 *
 * schema.org schreibt ISO-8601 vor ("PT2H20M"), aber nicht jede Seite haelt sich daran -
 * Swissmilk liefert schlicht die Zahl der Minuten. Beides wird akzeptiert.
 */
object IsoDuration {
    private val ISO = Regex(
        "^P(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?$",
        RegexOption.IGNORE_CASE,
    )

    /** Minuten oder `null`, wenn sich nichts Sinnvolles lesen laesst. */
    fun toMinutes(value: String?): Int? {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return null

        text.toIntOrNull()?.let { return it.takeIf { minutes -> minutes > 0 } }

        val match = ISO.matchEntire(text) ?: return null
        val days = match.groupValues[1].toIntOrNull() ?: 0
        val hours = match.groupValues[2].toIntOrNull() ?: 0
        val minutes = match.groupValues[3].toIntOrNull() ?: 0

        val total = days * MINUTES_PER_DAY + hours * MINUTES_PER_HOUR + minutes
        return total.takeIf { it > 0 }
    }

    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 24 * 60
}
