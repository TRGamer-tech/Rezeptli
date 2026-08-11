package ch.rezeptli.app.domain.profile

/**
 * Was die App ueber die Person weiss, die sie benutzt.
 *
 * Alles ist freiwillig: Ein leeres Profil ist ein gueltiges Profil. Die Angaben
 * beeinflussen die Reihenfolge der Vorschlaege, nie deren Verfuegbarkeit - wer nichts
 * angibt, sieht dieselben Rezepte, nur in anderer Reihenfolge.
 *
 * Die Angaben bleiben auf dem Geraet.
 */
data class UserProfile(
    val firstName: String = "",
    val country: Country? = null,
    val cuisines: Set<Cuisine> = emptySet(),
    val diets: Set<Diet> = emptySet(),
    val intolerances: Set<Intolerance> = emptySet(),
    val householdSize: Int? = null,
    /** Wurde das Onboarding gesehen - unabhaengig davon, ob etwas ausgefuellt wurde. */
    val onboardingCompleted: Boolean = false,
) {
    val hasName: Boolean get() = firstName.isNotBlank()

    /** Sprachregionen, aus denen Quellen bevorzugt werden. */
    val preferredRegions: List<LanguageRegion>
        get() = country?.let { listOf(it.region) } ?: emptyList()

    companion object {
        val EMPTY = UserProfile()

        /** Mehr als das kocht kaum jemand auf einmal - schuetzt vor Tippfehlern. */
        const val MAX_HOUSEHOLD_SIZE = 12
    }
}

/**
 * Das Wohnland. Bestimmt, welche Quellen zuerst vorgeschlagen werden.
 *
 * Bewusst eine kurze Liste: Die App kennt nur Quellen aus diesen Laendern, und ein
 * Auswahlfeld mit 200 Eintraegen waere eine Zumutung fuer eine Frage, deren Antwort
 * nur die Reihenfolge beeinflusst. "Anderswo" faengt alle uebrigen ab.
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
 * Bewusst getrennt von [Diet]: "italienisch" und "vegan" schliessen sich nicht aus,
 * in einer gemeinsamen Liste liessen sie sich aber nicht zusammen ausdruecken.
 *
 * [region] verbindet eine Kueche mit einem Sprachraum. Wer italienisch mag, bekommt
 * italienische Quellen weiter oben - auch ohne in Italien zu wohnen.
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
 * Getrennt von [Diet], weil hier etwas anderes auf dem Spiel steht: Eine Ernaehrungsform
 * ist eine Vorliebe, eine Allergie ist ein Gesundheitsrisiko. Die App darf daraus
 * niemals ableiten, ein Rezept sei "sicher" - sie kennt weder Spuren noch Zutatenlisten
 * von Fertigprodukten. Die Angabe dient nur dazu, passende Rezepte nach oben zu holen.
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
