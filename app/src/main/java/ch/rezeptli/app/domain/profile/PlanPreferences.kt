package ch.rezeptli.app.domain.profile

/** In welcher Waehrung ein Land rechnet. */
enum class Currency(val symbol: String) {
    CHF("CHF"),
    EUR("€"),
}

/**
 * Wo eingekauft wird: Waehrung, Preisniveau und Laeden eines Landes.
 *
 * Die Richtpreise der App sind Schweizer Preise in Franken. [priceLevel] rechnet sie in
 * das Land um - Preisniveau und Wechselkurs in einer Zahl. Das sind grobe Schaetzwerte
 * (Lebensmittel kosten in den Nachbarlaendern rund ein Drittel weniger als in der Schweiz),
 * keine amtlichen Zahlen; die Oberflaeche zeigt alle Summen deshalb mit "ca.".
 *
 * Ohne Landesangabe gilt die Schweiz - dort ist die App zu Hause.
 */
enum class Market(
    val country: Country,
    val currency: Currency,
    val priceLevel: Double,
    val defaultBudget: Int,
) {
    SCHWEIZ(Country.SCHWEIZ, Currency.CHF, 1.0, 120),
    DEUTSCHLAND(Country.DEUTSCHLAND, Currency.EUR, 0.7, 85),
    OESTERREICH(Country.OESTERREICH, Currency.EUR, 0.7, 85),
    FRANKREICH(Country.FRANKREICH, Currency.EUR, 0.75, 90),
    ITALIEN(Country.ITALIEN, Currency.EUR, 0.7, 85),
    ANDERSWO(Country.ANDERSWO, Currency.EUR, 0.65, 80),
    ;

    /** Die Laeden dieses Landes, in der Reihenfolge, in der sie zur Auswahl stehen. */
    val stores: List<StoreChain> get() = StoreChain.entries.filter { it.country == country }

    companion object {
        fun of(country: Country?): Market = entries.firstOrNull { it.country == country } ?: SCHWEIZ
    }
}

/**
 * Die Laeden, bei denen die App einen Einkauf schaetzt.
 *
 * [priceFactor] ist ein grober Auf- oder Abschlag auf das Preisniveau des Landes (1.0 =
 * der uebliche Supermarkt dort). Es sind Schaetzwerte, keine Live-Preise.
 *
 * Die Schweizer Laeden tragen ihren Namen ohne Laenderkuerzel, weil sie so schon
 * gespeichert sind - ein Umbenennen haette bestehende Angaben geloescht.
 */
enum class StoreChain(val label: String, val country: Country, val priceFactor: Double) {
    MIGROS("Migros", Country.SCHWEIZ, 1.0),
    COOP("Coop", Country.SCHWEIZ, 1.03),
    DENNER("Denner", Country.SCHWEIZ, 0.9),
    ALDI("Aldi", Country.SCHWEIZ, 0.82),
    LIDL("Lidl", Country.SCHWEIZ, 0.84),
    VOLG("Volg", Country.SCHWEIZ, 1.08),
    SPAR("Spar", Country.SCHWEIZ, 1.05),

    DE_REWE("Rewe", Country.DEUTSCHLAND, 1.0),
    DE_EDEKA("Edeka", Country.DEUTSCHLAND, 1.02),
    DE_ALDI("Aldi", Country.DEUTSCHLAND, 0.88),
    DE_LIDL("Lidl", Country.DEUTSCHLAND, 0.88),
    DE_KAUFLAND("Kaufland", Country.DEUTSCHLAND, 0.93),
    DE_PENNY("Penny", Country.DEUTSCHLAND, 0.9),
    DE_NETTO("Netto", Country.DEUTSCHLAND, 0.9),

    AT_BILLA("Billa", Country.OESTERREICH, 1.0),
    AT_SPAR("Spar", Country.OESTERREICH, 1.0),
    AT_HOFER("Hofer", Country.OESTERREICH, 0.88),
    AT_LIDL("Lidl", Country.OESTERREICH, 0.88),
    AT_PENNY("Penny", Country.OESTERREICH, 0.9),
    AT_MPREIS("MPreis", Country.OESTERREICH, 1.03),

    FR_CARREFOUR("Carrefour", Country.FRANKREICH, 1.0),
    FR_LECLERC("E.Leclerc", Country.FRANKREICH, 0.93),
    FR_INTERMARCHE("Intermarché", Country.FRANKREICH, 0.97),
    FR_AUCHAN("Auchan", Country.FRANKREICH, 0.98),
    FR_SUPER_U("Super U", Country.FRANKREICH, 0.97),
    FR_LIDL("Lidl", Country.FRANKREICH, 0.88),
    FR_MONOPRIX("Monoprix", Country.FRANKREICH, 1.15),

    IT_COOP("Coop", Country.ITALIEN, 1.02),
    IT_CONAD("Conad", Country.ITALIEN, 1.0),
    IT_ESSELUNGA("Esselunga", Country.ITALIEN, 1.0),
    IT_CARREFOUR("Carrefour", Country.ITALIEN, 1.03),
    IT_LIDL("Lidl", Country.ITALIEN, 0.88),
    IT_EUROSPIN("Eurospin", Country.ITALIEN, 0.85),
    IT_MD("MD", Country.ITALIEN, 0.86),

    XX_SUPERMARKT("Supermarkt", Country.ANDERSWO, 1.0),
    XX_DISCOUNTER("Discounter", Country.ANDERSWO, 0.88),
    XX_BIO("Bio- oder Feinkostladen", Country.ANDERSWO, 1.25),
    ;

    /** Der Faktor auf die Schweizer Richtpreise: Preisniveau des Landes mal Niveau des Ladens. */
    val priceMultiplier: Double get() = Market.of(country).priceLevel * priceFactor

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

/** Die Mahlzeiten eines Tages, die der Plan fuellt. */
enum class MealSlot(val label: String) {
    MITTAG("Mittagessen"),
    ABEND("Abendessen"),
    ;

    companion object {
        fun fromName(value: String?): MealSlot? = entries.firstOrNull { it.name == value }
    }
}

/** Die Stimmungen aus der Planung - wirken als Punktzahl im Wochenplan, nie als harter Ausschluss. */
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

/**
 * Was in der Kueche steht.
 *
 * Ein Rezept, das ein Geraet verlangt, das fehlt, wird nicht vorgeschlagen. Besondere
 * Geraete ([isSpecial]) bringen dafuer Rezepte nach vorn, die genau dafuer gedacht sind -
 * wer einen Dampfgarer hat, soll ihn auch benutzen koennen.
 */
enum class Appliance(val label: String, val isSpecial: Boolean = true) {
    BACKOFEN("Backofen", isSpecial = false),
    HERD("Herd", isSpecial = false),
    MIKROWELLE("Mikrowelle"),
    HEISSLUFT("Heissluftfritteuse"),
    DAMPFGARER("Dampfgarer (Steamer)"),
    SLOWCOOKER("Slow Cooker"),
    REISKOCHER("Reiskocher"),
    ;

    companion object {
        fun fromName(value: String): Appliance? = entries.firstOrNull { it.name == value }
    }
}
