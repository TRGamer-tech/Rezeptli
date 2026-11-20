package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.profile.Appliance
import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.Mood

/** Wie streng ein Rezept ist - von offen (Fleisch) bis streng (vegan). */
enum class DietClass {
    FLEISCH,
    FISCH,
    VEGETARISCH,
    VEGAN,
}

/**
 * Was die App einem Rezept ansieht.
 *
 * Es ist eine Einschaetzung aus Titel, Zutaten und Zubereitung, keine Zutatenpruefung.
 * [allergens] kann deshalb unvollstaendig sein - die App sagt das auch so.
 */
data class RecipeTraits(
    val diet: DietClass,
    val allergens: Set<Intolerance>,
    val moods: Set<Mood>,
    val requiredAppliances: Set<Appliance>,
    val lowCarb: Boolean,
    /** Was das Rezept enthaelt, das jemand nicht moegen koennte. */
    val dislikes: Set<Dislike> = emptySet(),
    val course: Course = Course.HAUPTGERICHT,
    /** Die Kuechen, zu denen das Rezept erkennbar gehoert - meistens keine. */
    val cuisines: Set<Cuisine> = emptySet(),
    /** Die Woerter aus Titel und Zutaten - damit lassen sich eigene Stichworte pruefen. */
    val words: Set<String> = emptySet(),
) {
    /** Der Hauptbegriff fuer den farbigen Chip auf der Karte. */
    val label: MealLabel
        get() = when {
            Mood.PROTEIN in moods && diet != DietClass.VEGAN && diet != DietClass.VEGETARISCH -> MealLabel.PROTEIN
            Mood.SCHNELL in moods -> MealLabel.SCHNELL
            Mood.FAKEAWAY in moods -> MealLabel.FAKEAWAY
            Mood.FAMILIE in moods -> MealLabel.FAMILIE
            Mood.COMFORT in moods -> MealLabel.COMFORT
            Mood.LEICHT in moods -> MealLabel.LEICHT
            Mood.DARMFREUNDLICH in moods -> MealLabel.DARM
            diet == DietClass.VEGAN -> MealLabel.VEGAN
            diet == DietClass.VEGETARISCH -> MealLabel.VEGETARISCH
            else -> MealLabel.ALLTAG
        }

    /** Verlangt das Rezept ein besonderes Geraet, das in dieser Kueche fehlt? */
    fun missesSpecialAppliance(kitchen: Set<Appliance>): Boolean =
        kitchen.isNotEmpty() && requiredAppliances.any { it.isSpecial && it !in kitchen }
}

/** Die Chips auf den Mahlzeitenkarten. */
enum class MealLabel(val text: String) {
    PROTEIN("Proteinreich"),
    SCHNELL("Schnell"),
    FAKEAWAY("Fakeaway"),
    FAMILIE("Familienliebling"),
    COMFORT("Comfort"),
    LEICHT("Leicht"),
    DARM("Darmfreundlich"),
    VEGAN("Vegan"),
    VEGETARISCH("Vegetarisch"),
    ALLTAG("Alltagsküche"),
}
