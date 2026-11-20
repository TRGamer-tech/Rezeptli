package ch.rezeptli.app.data.local

/**
 * Wie die gekochten Gerichte in der Einstellungsdatei stehen: je Gericht eine Zeile
 * "Zeitpunkt|Erkennungszeichen".
 *
 * Der Zeitpunkt steht vorn, weil das Zeichen eine Adresse sein kann - und eine Adresse darf
 * jedes Trennzeichen enthalten.
 */
internal object CookedEntries {
    private const val SEPARATOR = "|"

    /** Laenger zurueck schaut der Plan nicht - siehe RecentlyCooked.FADE_DAYS. */
    const val KEEP_MS = 60L * 24 * 60 * 60 * 1_000

    fun decode(lines: Set<String>?): Map<String, Long> =
        lines
            .orEmpty()
            .mapNotNull { line ->
                val cookedAt = line.substringBefore(SEPARATOR).toLongOrNull() ?: return@mapNotNull null
                line.substringAfter(SEPARATOR, "").takeIf { it.isNotEmpty() }?.let { it to cookedAt }
            }.toMap()

    fun encode(entries: Map<String, Long>): Set<String> =
        entries.mapTo(mutableSetOf()) { (key, cookedAt) -> "$cookedAt$SEPARATOR$key" }

    /**
     * Die Zeilen nach einem neuen Vermerk: [keys] gelten als um [at] gekocht (`null` nimmt den
     * Vermerk zurueck). Ein neuer Vermerk ersetzt den alten, und was lange her ist, faellt weg.
     */
    fun updated(lines: Set<String>?, keys: Collection<String>, at: Long?, now: Long): Set<String> {
        val kept = decode(lines).filter { (key, cookedAt) -> key !in keys && cookedAt >= now - KEEP_MS }
        val added = if (at == null) emptyMap() else keys.associateWith { at }
        return encode(kept + added)
    }
}
