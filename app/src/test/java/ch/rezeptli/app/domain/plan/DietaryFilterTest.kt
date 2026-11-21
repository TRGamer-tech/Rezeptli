package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.UserProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DietaryFilterTest {
    private fun traits(
        diet: DietClass,
        vararg allergens: Intolerance,
        dislikes: Set<Dislike> = emptySet(),
        words: Set<String> = emptySet(),
        lowCarb: Boolean = false,
    ) = RecipeTraits(
        diet = diet,
        allergens = allergens.toSet(),
        moods = emptySet(),
        requiredAppliances = emptySet(),
        lowCarb = lowCarb,
        dislikes = dislikes,
        words = words,
    )

    @Test
    fun `ohne Angaben ist der Filter aus und laesst alles durch`() {
        val filter = DietaryFilter()

        assertFalse(filter.isActive)
        assertTrue(filter.allows(traits(DietClass.FLEISCH, Intolerance.NUESSE)))
    }

    @Test
    fun `Allergien schliessen aus`() {
        val filter = DietaryFilter(intolerances = setOf(Intolerance.NUESSE))

        assertFalse(filter.allows(traits(DietClass.VEGAN, Intolerance.NUESSE)))
        assertTrue(filter.allows(traits(DietClass.VEGAN, Intolerance.GLUTEN)))
    }

    @Test
    fun `vegetarisch laesst vegan durch, aber keinen Fisch`() {
        val filter = DietaryFilter(diets = setOf(Diet.VEGETARISCH))

        assertTrue(filter.allows(traits(DietClass.VEGAN)))
        assertFalse(filter.allows(traits(DietClass.FISCH)))
    }

    @Test
    fun `pescetarisch laesst Fisch durch, aber kein Fleisch`() {
        val filter = DietaryFilter(diets = setOf(Diet.PESCETARISCH))

        assertTrue(filter.allows(traits(DietClass.FISCH)))
        assertTrue(filter.allows(traits(DietClass.VEGETARISCH)))
        assertFalse(filter.allows(traits(DietClass.FLEISCH)))
    }

    @Test
    fun `kohlenhydratarm gilt zusaetzlich zur Ernaehrungsform`() {
        val filter = DietaryFilter(diets = setOf(Diet.VEGETARISCH, Diet.KOHLENHYDRATARM))

        assertTrue(filter.allows(traits(DietClass.VEGAN, lowCarb = true)))
        assertFalse(filter.allows(traits(DietClass.VEGAN, lowCarb = false)))
        assertFalse(filter.allows(traits(DietClass.FLEISCH, lowCarb = true)))
    }

    @Test
    fun `eine Abneigung schliesst aus wie eine Unvertraeglichkeit`() {
        val filter = DietaryFilter(dislikes = setOf(Dislike.FISCH))

        assertTrue(filter.isActive)
        assertFalse(filter.allows(traits(DietClass.FISCH, dislikes = setOf(Dislike.FISCH))))
        assertTrue(filter.allows(traits(DietClass.FLEISCH, dislikes = setOf(Dislike.RIND))))
    }

    @Test
    fun `vegan als Ernaehrung und vegan als Abneigung sind Gegenteile`() {
        // Daran ist die gemeinsame Runde gescheitert: "Vegan" stand unter "Was darf nicht auf
        // den Tisch?", bedeutete aber "nur vegan".
        val veganesGericht = traits(DietClass.VEGAN, dislikes = setOf(Dislike.VEGAN))
        val fleischgericht = traits(DietClass.FLEISCH)

        val nurVegan = DietaryFilter(diets = setOf(Diet.VEGAN))
        assertTrue(nurVegan.allows(veganesGericht))
        assertFalse(nurVegan.allows(fleischgericht))

        val keinVegan = DietaryFilter(dislikes = setOf(Dislike.VEGAN))
        assertFalse(keinVegan.allows(veganesGericht))
        assertTrue(keinVegan.allows(fleischgericht))
    }

    @Test
    fun `ein eigenes Stichwort trifft auch zusammengesetzte Woerter`() {
        val filter = DietaryFilter(customDislikes = listOf("Rosinen"))

        assertTrue(filter.isActive)
        assertFalse(filter.allows(traits(DietClass.VEGETARISCH, words = setOf("rosinenbroetchen", "mehl"))))
        assertFalse(filter.allows(traits(DietClass.VEGETARISCH, words = setOf("rosinen"))))
        assertTrue(filter.allows(traits(DietClass.VEGETARISCH, words = setOf("rosen", "mehl"))))
    }

    @Test
    fun `ein Stichwort aus mehreren Woertern braucht alle davon`() {
        val filter = DietaryFilter(customDislikes = listOf("Rote Bete"))

        assertFalse(filter.allows(traits(DietClass.VEGAN, words = setOf("rote", "bete", "salat"))))
        assertTrue(filter.allows(traits(DietClass.VEGAN, words = setOf("rote", "linsen"))))
    }

    @Test
    fun `Einzahl und Mehrzahl sind beim Stichwort dasselbe`() {
        val rosinen = DietaryFilter(customDislikes = listOf("Rosinen"))
        val nuesse = DietaryFilter(customDislikes = listOf("Nüsse"))
        val aepfel = DietaryFilter(customDislikes = listOf("Äpfel"))

        assertFalse(rosinen.allows(traits(DietClass.VEGAN, words = setOf("rosine", "hafer"))))
        assertFalse(nuesse.allows(traits(DietClass.VEGAN, words = setOf("haselnuss"))))
        assertFalse(nuesse.allows(traits(DietClass.VEGAN, words = setOf("baumnuesse"))))
        assertFalse(aepfel.allows(traits(DietClass.VEGAN, words = setOf("apfelmus"))))
        // Ein kurzes Wort bleibt, wie es ist - "Reis" soll nicht jedes "Rei..." treffen.
        assertTrue(
            DietaryFilter(customDislikes = listOf("Reis")).allows(traits(DietClass.VEGAN, words = setOf("reibkaese"))),
        )
    }

    @Test
    fun `Schweizer und hochdeutscher Name meinen dasselbe`() {
        val rueebli = DietaryFilter(customDislikes = listOf("Rüebli"))
        val karotten = DietaryFilter(customDislikes = listOf("Karotten"))
        val classifier = RecipeClassifier()

        fun rezept(zutat: String) = classifier.classify(
            ch.rezeptli.app.domain.model.Recipe(
                title = "Suppe",
                ingredients = listOf(
                    ch.rezeptli.app.domain.parser
                        .IngredientTextParser()
                        .parseLine(zutat)!!
                        .toIngredient(),
                ),
            ),
        )

        assertFalse(rueebli.allows(rezept("300 g Karotten")))
        assertFalse(rueebli.allows(rezept("300 g Rüebli")))
        assertFalse(karotten.allows(rezept("300 g Rüebli")))
        assertTrue(karotten.allows(rezept("300 g Lauch")))
    }

    @Test
    fun `wer eine ganze Gruppe eintippt, meint die Gruppe`() {
        val pilze = DietaryFilter(customDislikes = listOf("Pilze"))
        val fisch = DietaryFilter(customDislikes = listOf("fisch"))

        // "Champignons" enthaelt das Wort "Pilze" nicht - gemeint sind sie trotzdem.
        assertFalse(
            pilze.allows(traits(DietClass.VEGAN, dislikes = setOf(Dislike.PILZE), words = setOf("champignons"))),
        )
        assertFalse(fisch.allows(traits(DietClass.FISCH, dislikes = setOf(Dislike.FISCH), words = setOf("thon"))))
        assertTrue(pilze.allows(traits(DietClass.VEGAN, words = setOf("zucchetti"))))
        // Ein einzelner Pilz ist keine Gruppe: Wer "Champignons" schreibt, meint Champignons.
        val champignons = DietaryFilter(customDislikes = listOf("Champignons"))
        assertTrue(
            champignons.allows(traits(DietClass.VEGAN, dislikes = setOf(Dislike.PILZE), words = setOf("steinpilze"))),
        )
        assertFalse(
            champignons.allows(traits(DietClass.VEGAN, dislikes = setOf(Dislike.PILZE), words = setOf("champignon"))),
        )
    }

    @Test
    fun `Umlaute und Grossschreibung spielen beim Stichwort keine Rolle`() {
        val filter = DietaryFilter(customDislikes = listOf("KÜRBIS"))

        assertFalse(filter.allows(traits(DietClass.VEGAN, words = setOf("kuerbissuppe"))))
    }

    @Test
    fun `zu zweit gilt, was eine von beiden nicht will, fuer beide`() {
        val ich = DietaryFilter(dislikes = setOf(Dislike.FISCH), customDislikes = listOf("Rosinen"))
        val gegenueber = DietaryFilter(
            diets = setOf(Diet.VEGETARISCH),
            intolerances = setOf(Intolerance.NUESSE),
            dislikes = setOf(Dislike.VEGAN),
            customDislikes = listOf("rosinen", "Kapern"),
        )

        val zusammen = ich + gegenueber

        assertEquals(setOf(Diet.VEGETARISCH), zusammen.diets)
        assertEquals(setOf(Intolerance.NUESSE), zusammen.intolerances)
        assertEquals(setOf(Dislike.FISCH, Dislike.VEGAN), zusammen.dislikes)
        // Dasselbe Stichwort zaehlt nur einmal, auch anders geschrieben.
        assertEquals(listOf("Rosinen", "Kapern"), zusammen.customDislikes)

        // Ich habe "vegan" nicht ausgeschlossen - mein Gegenueber schon. Also kommt es nicht vor.
        assertTrue(ich.allows(traits(DietClass.VEGAN, dislikes = setOf(Dislike.VEGAN))))
        assertFalse(zusammen.allows(traits(DietClass.VEGAN, dislikes = setOf(Dislike.VEGAN))))
    }

    @Test
    fun `zu zweit gewinnt bei der Ernaehrung die strengere Form`() {
        val zusammen = DietaryFilter(diets = setOf(Diet.VEGETARISCH)) + DietaryFilter(diets = setOf(Diet.VEGAN))

        assertTrue(zusammen.allows(traits(DietClass.VEGAN)))
        assertFalse(zusammen.allows(traits(DietClass.VEGETARISCH)))
    }

    @Test
    fun `der Filter eines Profils enthaelt alles, was dort ausgeschlossen ist`() {
        val profil = UserProfile(
            diets = setOf(Diet.PESCETARISCH),
            intolerances = setOf(Intolerance.LAKTOSE),
            dislikes = setOf(Dislike.KORIANDER),
            customDislikes = listOf("Rosinen"),
        )

        assertEquals(
            DietaryFilter(
                diets = setOf(Diet.PESCETARISCH),
                intolerances = setOf(Intolerance.LAKTOSE),
                dislikes = setOf(Dislike.KORIANDER),
                customDislikes = listOf("Rosinen"),
            ),
            DietaryFilter.of(profil),
        )
        assertFalse(DietaryFilter.of(UserProfile()).isActive)
    }
}
