package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ServingsAndScalerTest {
    @Test
    fun `liest die Portionszahl aus Freitext`() {
        assertEquals(4, ServingsParser.parse("4"))
        assertEquals(4, ServingsParser.parse("Für 4 Personen"))
        assertEquals(6, ServingsParser.parse("6-8 Portionen"))
    }

    @Test
    fun `unbrauchbare Angaben ergeben nichts`() {
        assertNull(ServingsParser.parse(null))
        assertNull(ServingsParser.parse("nach Belieben"))
        assertNull(ServingsParser.parse("0"))
        assertNull(ServingsParser.parse("48 Kekse"))
    }

    @Test
    fun `was herauskommt, ist keine Angabe, fuer wie viele es reicht`() {
        // "1 Blech" fuer vier Personen ergaebe sonst die vierfache Menge.
        assertNull(ServingsParser.parse("1 Blech"))
        assertNull(ServingsParser.parse("12 Stück"))
        assertNull(ServingsParser.parse("ca. 20 Stuecke"))
        assertNull(ServingsParser.parse("4 Gläser à 250 ml"))
        assertNull(ServingsParser.parse("1 Springform (26 cm)"))
        assertNull(ServingsParser.parse("2 Brote"))
        // Ein Pfannkuchen pro Person ist dagegen eine Portion - "Kuchen" steckt nur im Wort.
        assertEquals(4, ServingsParser.parse("4 Pfannkuchen-Portionen"))
        assertEquals(2, ServingsParser.parse("2 Personen"))
        assertEquals(4, ServingsParser.parse("4 servings"))
    }

    @Test
    fun `ohne Angabe der Quelle wird von vier Portionen ausgegangen`() {
        assertEquals(0.5, IngredientScaler.factor(target = 2, original = null))
        assertEquals(2.0, IngredientScaler.factor(target = 4, original = 2))
    }

    @Test
    fun `skaliert Mengen und rundet so, wie man abmisst`() {
        val zutaten = listOf(
            Ingredient(name = "Mehl", amount = 250.0, unit = IngredientUnit.GRAMM),
            Ingredient(name = "Zwiebel", amount = 1.0, unit = IngredientUnit.STUECK),
            Ingredient(name = "Salz", amount = 1.0, unit = IngredientUnit.TEELOEFFEL),
            Ingredient(name = "Pfeffer"),
        )

        val ergebnis = IngredientScaler.scale(zutaten, factor = 0.5)

        assertEquals(125.0, ergebnis[0].amount)
        assertEquals(0.5, ergebnis[1].amount)
        assertEquals(0.5, ergebnis[2].amount)
        assertNull(ergebnis[3].amount)
    }

    @Test
    fun `eine Menge wird nie zu null`() {
        assertEquals(0.25, IngredientScaler.nice(0.01, IngredientUnit.PRISE))
    }
}
