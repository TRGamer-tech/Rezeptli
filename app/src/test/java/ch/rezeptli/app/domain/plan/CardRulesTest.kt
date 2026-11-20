package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.profile.Appliance
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.UserProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Was eine Wischkarte erfuellen muss - am Titel und, wenn sie geladen ist, an der Rezeptseite. */
class CardRulesTest {
    private val classifier = RecipeClassifier()

    private fun titel(text: String) = classifier.classifyTitle(text)

    private fun seite(titel: String, vararg zutaten: String, text: String = "In der Pfanne anbraten.") =
        classifier.classify(
            Recipe(title = titel, instructions = text, ingredients = zutaten.map { Ingredient(name = it) }),
        )

    @Test
    fun `ohne Angaben braucht es keine Rezeptseite - der Titel genuegt`() {
        val regeln = CardRules.of(UserProfile())

        assertFalse(regeln.needsPage)
        assertTrue(regeln.allows(titel("Lachsfilet mit Dill")))
        assertTrue(regeln.allows(titel("Erdbeer-Smoothie")))
    }

    @Test
    fun `Ernaehrung, Unvertraeglichkeiten und Abneigungen verlangen die Rezeptseite`() {
        assertTrue(CardRules.of(UserProfile(diets = setOf(Diet.VEGETARISCH))).needsPage)
        assertTrue(CardRules.of(UserProfile(intolerances = setOf(Intolerance.NUESSE))).needsPage)
        assertTrue(CardRules.of(UserProfile(dislikes = setOf(Dislike.FISCH))).needsPage)
        assertTrue(CardRules.of(UserProfile(customDislikes = listOf("Rosinen"))).needsPage)
        // Die Auswahl Essen oder Getraenke allein nicht: Die steht im Titel.
        assertFalse(CardRules.of(UserProfile(), CourseChoice.GETRAENKE).needsPage)
    }

    @Test
    fun `nur Essen laesst Getraenke weg, nur Getraenke alles andere`() {
        val essen = CardRules.of(UserProfile(), CourseChoice.ESSEN)
        val getraenke = CardRules.of(UserProfile(), CourseChoice.GETRAENKE)

        assertTrue(essen.allows(titel("Kartoffelgratin")))
        assertTrue(essen.allows(titel("Schokoladenkuchen")))
        assertFalse(essen.allows(titel("Erdbeer-Smoothie")))

        assertTrue(getraenke.allows(titel("Erdbeer-Smoothie")))
        assertFalse(getraenke.allows(titel("Kartoffelgratin")))
    }

    @Test
    fun `der Titel sortiert das Offensichtliche schon aus`() {
        val keinFisch = CardRules.of(UserProfile(dislikes = setOf(Dislike.FISCH)))

        assertFalse(keinFisch.allows(titel("Lachsfilet mit Dill")))
        assertTrue(keinFisch.allows(titel("Sommerliche Gemüsepfanne")))
    }

    @Test
    fun `was der Titel verschweigt, findet die Rezeptseite`() {
        val keinFisch = CardRules.of(UserProfile(dislikes = setOf(Dislike.FISCH)))

        assertTrue(keinFisch.allows(titel("Sommerliche Gemüsepfanne")))
        assertFalse(keinFisch.allows(seite("Sommerliche Gemüsepfanne", "Zucchetti", "Sardellen")))
        assertTrue(keinFisch.allows(seite("Sommerliche Gemüsepfanne", "Zucchetti", "Peperoni")))
    }

    @Test
    fun `fuer den Wochenplan zaehlen nur Mahlzeiten`() {
        val plan = CardRules.of(UserProfile()).copy(mainsOnly = true)

        assertTrue(plan.allows(titel("Kartoffelgratin")))
        assertFalse(plan.allows(titel("Schokoladenkuchen")))
        assertFalse(plan.allows(titel("Erdbeer-Smoothie")))
        assertFalse(plan.allows(titel("Tomatensauce")))
    }

    @Test
    fun `ein Rezept fuer ein Geraet, das fehlt, wird nicht gezeigt`() {
        val ohneFritteuse = CardRules.of(UserProfile(appliances = setOf(Appliance.HERD, Appliance.BACKOFEN)))
        val mitFritteuse = CardRules.of(UserProfile(appliances = setOf(Appliance.HERD, Appliance.HEISSLUFT)))

        assertFalse(ohneFritteuse.allows(titel("Pommes aus dem Airfryer")))
        assertTrue(mitFritteuse.allows(titel("Pommes aus dem Airfryer")))
        // Wer nichts zur Kueche angegeben hat, bekommt alles.
        assertTrue(CardRules.of(UserProfile()).allows(titel("Pommes aus dem Airfryer")))
    }

    @Test
    fun `die Regeln eines Profils nehmen alles auf, was dort steht`() {
        val profil = UserProfile(
            diets = setOf(Diet.VEGETARISCH),
            intolerances = setOf(Intolerance.GLUTEN),
            dislikes = setOf(Dislike.PILZE),
            customDislikes = listOf("Rosinen"),
            appliances = setOf(Appliance.HERD),
        )

        val regeln = CardRules.of(profil, CourseChoice.ESSEN)

        assertEquals(DietaryFilter.of(profil), regeln.filter)
        assertEquals(setOf(Appliance.HERD), regeln.kitchen)
        assertEquals(CourseChoice.ESSEN, regeln.course)
    }
}
