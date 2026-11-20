package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.profile.Appliance
import ch.rezeptli.app.domain.profile.UserProfile

/**
 * Was eine Karte erfuellen muss, um gezeigt zu werden.
 *
 * Dieselben Regeln gelten zweimal: zuerst am Titel - mehr kennt die App von einer Karte
 * aus dem Verzeichnis nicht - und, wenn die Rezeptseite geladen ist, an Zutaten und
 * Zubereitung. Der Titel kann nur aussortieren ("Lachsfilet" ohne Fisch, "Erdbeer-Smoothie"
 * bei "nur Essen"); zusagen kann erst die Seite.
 */
data class CardRules(
    val course: CourseChoice = CourseChoice.BEIDES,
    val filter: DietaryFilter = DietaryFilter(),
    /** Die Geraete der Kueche; leer heisst: nichts angegeben, also keine Einschraenkung. */
    val kitchen: Set<Appliance> = emptySet(),
    /** Nur Hauptgerichte - fuer den Wochenplan. */
    val mainsOnly: Boolean = false,
) {
    /**
     * Muss fuer eine Zusage die Rezeptseite gelesen werden?
     *
     * Ernaehrung, Unvertraeglichkeiten und Abneigungen stehen in den Zutaten, nicht im
     * Titel. Ohne solche Angaben genuegt der Titel.
     */
    val needsPage: Boolean get() = filter.isActive

    fun allows(traits: RecipeTraits): Boolean =
        course.allows(traits.course) &&
            (!mainsOnly || traits.course == Course.HAUPTGERICHT) &&
            filter.allows(traits) &&
            !traits.missesSpecialAppliance(kitchen)

    companion object {
        fun of(profile: UserProfile, course: CourseChoice = CourseChoice.BEIDES) = CardRules(
            course = course,
            filter = DietaryFilter.of(profile),
            kitchen = profile.appliances,
        )
    }
}
