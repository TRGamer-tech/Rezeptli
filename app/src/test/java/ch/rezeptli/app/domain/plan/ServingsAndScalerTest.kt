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
