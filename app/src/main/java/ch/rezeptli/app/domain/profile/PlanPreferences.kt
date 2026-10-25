package ch.rezeptli.app.domain.profile

/**
 * Die Laeden, bei denen die App einen Einkauf schaetzt.
 *
 * [priceFactor] ist ein grober Aufschlag auf die Richtpreise der App (1.0 = Migros/Coop).
 * Es sind Schaetzwerte, keine Live-Preise - die App zeigt sie deshalb immer als "ca.".
 */
enum class StoreChain(val label: String, val priceFactor: Double) {
    ALDI("Aldi", 0.82),
    LIDL("Lidl", 0.84),
    DENNER("Denner", 0.9),
    MIGROS("Migros", 1.0),
    COOP("Coop", 1.03),
    VOLG("Volg", 1.08),
    SPAR("Spar", 1.05),
    ;

    companion object {
        fun fromName(value: String?): StoreChain? = entries.firstOrNull { it.name == value }
    }
}

enum class Weekday(val short: String, val label: String) {
    MONTAG("Mo", "Montag"),
    DIENSTAG("Di", "Dienstag"),
    MITTWOCH("Mi", "Mittwoch"),
    DONNERSTAG("Do", "Donnerstag"),
    FREITAG("Fr", "Freitag"),
    SAMSTAG("Sa", "Samstag"),
    SONNTAG("So", "Sonntag"),
    ;

    companion object {
        fun fromName(value: String): Weekday? = entries.firstOrNull { it.name == value }
    }
}

/** Die Stimmungen aus der Planung - wirken als Punktzahl, nie als harter Ausschluss. */
enum class Mood(val label: String) {
    SCHNELL("Schnell gekocht"),
    LEICHT("Kalorienarm"),
    FAMILIE("Familienliebling"),
    COMFORT("Gesunde Hausmannskost"),
    DARMFREUNDLICH("Darmfreundlich"),
    FAKEAWAY("Fakeaway"),
    PROTEIN("Proteinreich"),
    ;

    companion object {
        fun fromName(value: String): Mood? = entries.firstOrNull { it.name == value }
    }
}

enum class Appliance(val label: String) {
    BACKOFEN("Backofen"),
    HERD("Herd"),
    MIKROWELLE("Mikrowelle"),
    HEISSLUFT("Heissluftfritteuse"),
    SLOWCOOKER("Slow Cooker"),
    REISKOCHER("Reiskocher"),
    ;

    companion object {
        fun fromName(value: String): Appliance? = entries.firstOrNull { it.name == value }
    }
}
