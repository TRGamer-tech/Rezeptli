package ch.rezeptli.app.domain.profile

/**
 * Was die App ueber die Person weiss, die sie benutzt.
 *
 * Alles ist freiwillig: Ein leeres Profil ist ein gueltiges Profil. Was hier steht, wirkt
 * an genau den Stellen, die die Einstellung nennt - eine Angabe ohne Folge gibt es nicht.
 *
 * Die Angaben bleiben auf dem Geraet. Einzige Ausnahme ist die gemeinsame Runde: Dort geht,
 * was nicht auf den Tisch soll, an den Pairing-Dienst, damit es fuer beide gilt.
 */
data class UserProfile(
    val firstName: String = "",
    val country: Country? = null,
    val cuisines: Set<Cuisine> = emptySet(),
    val diets: Set<Diet> = emptySet(),
    val intolerances: Set<Intolerance> = emptySet(),
    /** Was jemand nicht mag - wird ausgeschlossen wie eine Unvertraeglichkeit, nur ohne Gesundheitsrisiko. */
    val dislikes: Set<Dislike> = emptySet(),
    /** Eigene Stichworte fuer alles, was in der Liste fehlt, etwa "Rosinen". */
    val customDislikes: List<String> = emptyList(),
    val householdSize: Int? = null,
    /**
     * Die bevorzugten Laeden, hoechstens [MAX_STORES]. Der erste ist der Hauptladen: Mit
     * ihm rechnet die App. Die anderen stehen zum Vergleich daneben.
     */
    val stores: List<StoreChain> = emptyList(),
    val cookDays: Set<Weekday> = emptySet(),
    /** Welche Mahlzeiten geplant werden. Leer heisst: Mittag- und Abendessen. */
    val mealSlots: Set<MealSlot> = emptySet(),
    /** Wochenbudget fuer alle Einkaeufe des Plans, in der Waehrung des Landes. */
    val weeklyBudget: Int? = null,
    val moods: Set<Mood> = emptySet(),
    val appliances: Set<Appliance> = emptySet(),
    /** Wurde das Onboarding gesehen - unabhaengig davon, ob etwas ausgefuellt wurde. */
    val onboardingCompleted: Boolean = false,
) {
    /** Der Laden, mit dem gerechnet wird. */
    val store: StoreChain? get() = stores.firstOrNull()

    /** Wo eingekauft wird - bestimmt Laeden, Waehrung und Preisniveau. */
    val market: Market get() = Market.of(country)

    /** Die Kochtage in Wochenreihenfolge; ohne Auswahl Montag bis Freitag. */
    val days: List<Weekday>
        get() = cookDays.sortedBy { it.ordinal }.ifEmpty { Weekday.entries.take(DEFAULT_COOK_DAYS) }

    /** Die geplanten Mahlzeiten eines Tages, in Tagesreihenfolge. */
    val slots: List<MealSlot>
        get() = mealSlots.sortedBy { it.ordinal }.ifEmpty { MealSlot.entries }

    /** Anzahl Mahlzeiten pro Woche: Kochtage mal Mahlzeiten pro Tag. */
    val mealsPerWeek: Int get() = days.size * slots.size

    val persons: Int get() = householdSize ?: 2

    val budget: Int get() = weeklyBudget ?: market.defaultBudget

    /**
     * Der Faktor auf die Schweizer Richtpreise der App: Preisniveau des Landes mal Niveau
     * des Hauptladens. Ohne gewaehlten Laden gilt der uebliche Supermarkt des Landes.
     */
    val priceFactor: Double get() = store?.priceMultiplier ?: market.priceLevel

    companion object {
        val EMPTY = UserProfile()

        /** Mehr als das kocht kaum jemand auf einmal - schuetzt vor Tippfehlern. */
        const val MAX_HOUSEHOLD_SIZE = 12

        const val DEFAULT_COOK_DAYS = 5
        const val MAX_MOODS = 3
        const val MAX_STORES = 3
        const val MIN_BUDGET = 20

        /** Zwei Mahlzeiten an sieben Tagen fuer eine grosse Familie brauchen Luft nach oben. */
        const val MAX_BUDGET = 600

        const val MAX_CUSTOM_DISLIKES = 12
        const val MAX_DISLIKE_LENGTH = 24
    }
}

/**
 * Das Wohnland. Bestimmt, welche Quellen zuerst vorgeschlagen werden, welche Laeden zur
 * Auswahl stehen und in welcher Waehrung gerechnet wird.
 *
 * Bewusst eine kurze Liste: Die App kennt nur Quellen aus diesen Laendern, und ein
 * Auswahlfeld mit 200 Eintraegen waere eine Zumutung. "Anderswo" faengt alle uebrigen ab.
 */
enum class Country(val code: String, val region: LanguageRegion) {
    SCHWEIZ("CH", LanguageRegion.DEUTSCH),
    DEUTSCHLAND("DE", LanguageRegion.DEUTSCH),
    OESTERREICH("AT", LanguageRegion.DEUTSCH),
    FRANKREICH("FR", LanguageRegion.FRANZOESISCH),
    ITALIEN("IT", LanguageRegion.ITALIENISCH),
    ANDERSWO("XX", LanguageRegion.DEUTSCH),
    ;

    companion object {
        fun fromCode(code: String?): Country? = entries.firstOrNull { it.code == code }
    }
}

/** Sprachraum einer Quelle - Grundlage dafuer, was als "gleiche Sprache" gilt. */
enum class LanguageRegion {
    DEUTSCH,
    FRANZOESISCH,
    ITALIENISCH,
}

/**
 * Kuechen im Sinne von Geschmacksrichtung.
 *
 * Eine Vorliebe, kein Filter: Gewaehlte Kuechen kommen beim Wischen und im Wochenplan
 * haeufiger vor, alles andere taucht weiterhin ab und zu auf.
 *
 * Bewusst getrennt von [Diet]: "italienisch" und "vegan" schliessen sich nicht aus.
 *
 * [region] verbindet eine Kueche mit einem Sprachraum. Wer italienisch mag, bekommt
 * zusaetzlich mehr Karten aus italienischen Quellen - auch ohne in Italien zu wohnen.
 */
enum class Cuisine(val region: LanguageRegion? = null) {
    SCHWEIZER_KLASSIKER(LanguageRegion.DEUTSCH),
    DEUTSCH_OESTERREICHISCH(LanguageRegion.DEUTSCH),
    ITALIENISCH(LanguageRegion.ITALIENISCH),
    FRANZOESISCH(LanguageRegion.FRANZOESISCH),
    MEDITERRAN,
    ASIATISCH,
    ORIENTALISCH,
    AMERIKANISCH,
    LATEINAMERIKANISCH,
    ;

    companion object {
        fun fromName(value: String): Cuisine? = entries.firstOrNull { it.name == value }
    }
}

/** Ernaehrungsform - eine Entscheidung, keine Unvertraeglichkeit. */
enum class Diet {
    VEGETARISCH,
    VEGAN,
    PESCETARISCH,
    KOHLENHYDRATARM,
    ;

    companion object {
        fun fromName(value: String): Diet? = entries.firstOrNull { it.name == value }
    }
}

/**
 * Unvertraeglichkeiten und Allergien.
 *
 * Getrennt von [Diet] und [Dislike], weil hier etwas anderes auf dem Spiel steht: Eine
 * Allergie ist ein Gesundheitsrisiko. Die App darf daraus niemals ableiten, ein Rezept sei
 * "sicher" - sie kennt weder Spuren noch die Zutatenlisten von Fertigprodukten.
 */
enum class Intolerance {
    GLUTEN,
    LAKTOSE,
    NUESSE,
    EIER,
    SOJA,
    MEERESFRUECHTE,
    ;

    companion object {
        fun fromName(value: String): Intolerance? = entries.firstOrNull { it.name == value }
    }
}

/**
 * Was jemand schlicht nicht mag.
 *
 * Wirkt wie ein Ausschluss: Ein Rezept, das etwas davon enthaelt, wird weder beim Wischen
 * gezeigt noch in den Plan genommen. In der gemeinsamen Runde zaehlen die Abneigungen
 * beider.
 *
 * [VEGAN] meint, was als vegan ausgeschrieben ist oder auf Fleischersatz baut - nicht
 * jeden Tomatensalat, der zufaellig ohne Tierisches auskommt.
 */
enum class Dislike {
    FISCH,
    MEERESFRUECHTE,
    POULET,
    RIND,
    SCHWEIN,
    LAMM,
    INNEREIEN,
    PILZE,
    ZWIEBELN,
    KNOBLAUCH,
    KORIANDER,
    OLIVEN,
    AUBERGINE,
    KOHL,
    SCHARF,
    KRAEFTIGER_KAESE,
    TOFU,
    VEGAN,
    ;

    companion object {
        fun fromName(value: String): Dislike? = entries.firstOrNull { it.name == value }
    }
}
