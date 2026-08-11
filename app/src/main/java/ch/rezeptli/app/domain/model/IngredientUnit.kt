package ch.rezeptli.app.domain.model

/**
 * Masssystem einer Einheit. Wird gebraucht, um metrische und angelsaechsische
 * Einheiten getrennt behandeln zu koennen (z. B. fuer eine spaetere Umschaltoption).
 */
enum class UnitSystem {
    METRIC,
    IMPERIAL,
    COUNT,
}

/**
 * Alle Einheiten, die Rezeptli kennt.
 *
 * Der Fokus liegt auf dem deutschsprachigen Raum: [DEZILITER] und [KAFFEELOEFFEL] sind
 * in der Schweiz Standard und deshalb erstklassig unterstuetzt. Angelsaechsische
 * Einheiten werden erkannt, sind aber bewusst nicht der Default.
 *
 * [abbreviation] ist die Kurzform fuer die Anzeige. Fuer ausgeschriebene Einheiten
 * ("Prise", "Bund") liefert die Presentation-Schicht eine lokalisierte Bezeichnung;
 * die Domain-Schicht bleibt frei von Android-Abhaengigkeiten.
 */
enum class IngredientUnit(
    val abbreviation: String,
    val system: UnitSystem,
    val aliases: Set<String>,
) {
    /** Keine Einheit, z. B. "2 Eier". */
    NONE("", UnitSystem.COUNT, emptySet()),

    GRAMM("g", UnitSystem.METRIC, setOf("g", "gr", "gramm")),
    KILOGRAMM("kg", UnitSystem.METRIC, setOf("kg", "kilo", "kilogramm")),
    MILLILITER("ml", UnitSystem.METRIC, setOf("ml", "milliliter")),
    DEZILITER("dl", UnitSystem.METRIC, setOf("dl", "deziliter")),
    LITER("l", UnitSystem.METRIC, setOf("l", "lt", "ltr", "liter")),

    TEELOEFFEL("TL", UnitSystem.METRIC, setOf("tl", "teeloeffel", "teelöffel", "tsp")),
    KAFFEELOEFFEL("KL", UnitSystem.METRIC, setOf("kl", "kaffeeloeffel", "kaffeelöffel")),
    ESSLOEFFEL("EL", UnitSystem.METRIC, setOf("el", "essloeffel", "esslöffel", "tbsp")),
    MESSERSPITZE("Msp.", UnitSystem.METRIC, setOf("msp", "messerspitze", "messerspitzen")),

    PRISE("Prise", UnitSystem.COUNT, setOf("prise", "prisen")),
    STUECK("Stk.", UnitSystem.COUNT, setOf("stk", "stueck", "stück", "st", "x")),
    BUND("Bund", UnitSystem.COUNT, setOf("bund", "bd")),
    PACKUNG("Pck.", UnitSystem.COUNT, setOf("pck", "pkg", "packung", "packungen", "paeckchen", "päckchen")),
    DOSE("Dose", UnitSystem.COUNT, setOf("dose", "dosen")),
    ZEHE("Zehe", UnitSystem.COUNT, setOf("zehe", "zehen")),
    SCHEIBE("Scheibe", UnitSystem.COUNT, setOf("scheibe", "scheiben")),
    TASSE("Tasse", UnitSystem.METRIC, setOf("tasse", "tassen")),

    CUP("cup", UnitSystem.IMPERIAL, setOf("cup", "cups")),
    OUNCE("oz", UnitSystem.IMPERIAL, setOf("oz", "ounce", "ounces", "unze", "unzen")),
    FLUID_OUNCE("fl oz", UnitSystem.IMPERIAL, setOf("floz", "fl oz")),
    POUND("lb", UnitSystem.IMPERIAL, setOf("lb", "lbs", "pound", "pounds")),
    ;

    companion object {
        private val BY_ALIAS: Map<String, IngredientUnit> =
            buildMap {
                IngredientUnit.entries.forEach { unit ->
                    if (unit != NONE) {
                        put(unit.abbreviation.lowercase().replace(".", ""), unit)
                    }
                }
                IngredientUnit.entries.forEach { unit ->
                    unit.aliases.forEach { alias -> put(alias, unit) }
                }
            }

        /**
         * Loest einen Einheiten-Text auf. Punkte, Gross-/Kleinschreibung und
         * Mehrfach-Leerzeichen werden ignoriert. Gibt `null` zurueck, wenn der Text
         * keine bekannte Einheit ist - der Aufrufer behandelt ihn dann als Teil des
         * Zutatennamens.
         */
        fun fromText(text: String): IngredientUnit? {
            val normalized =
                text
                    .trim()
                    .removeSuffix(".")
                    .replace(".", "")
                    .replace(Regex("\\s+"), " ")
                    .lowercase()
            if (normalized.isEmpty()) return null
            return BY_ALIAS[normalized] ?: BY_ALIAS[normalized.removeSuffix(".")]
        }
    }
}
