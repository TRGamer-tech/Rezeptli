package ch.rezeptli.app.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class IngredientUnitTest {
    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(
        "g, GRAMM",
        "G, GRAMM",
        "gr, GRAMM",
        "Gramm, GRAMM",
        "kg, KILOGRAMM",
        "ml, MILLILITER",
        "dl, DEZILITER",
        "DL, DEZILITER",
        "Deziliter, DEZILITER",
        "l, LITER",
        "TL, TEELOEFFEL",
        "KL, KAFFEELOEFFEL",
        "EL, ESSLOEFFEL",
        "Esslöffel, ESSLOEFFEL",
        "Msp., MESSERSPITZE",
        "Prise, PRISE",
        "Stk., STUECK",
        "Stück, STUECK",
        "Bund, BUND",
        "Zehen, ZEHE",
        "cup, CUP",
        "oz, OUNCE",
    )
    fun `erkennt Einheiten unabhaengig von Schreibweise`(text: String, expected: IngredientUnit) {
        assertEquals(expected, IngredientUnit.fromText(text))
    }

    @ParameterizedTest
    @ValueSource(strings = ["Mehl", "Zwiebel", "", "   ", "xyz"])
    fun `liefert null fuer unbekannte Einheiten`(text: String) {
        assertNull(IngredientUnit.fromText(text))
    }

    @Test
    fun `dl ist eine metrische Einheit und in der App enthalten`() {
        assertEquals("dl", IngredientUnit.DEZILITER.abbreviation)
        assertEquals(UnitSystem.METRIC, IngredientUnit.DEZILITER.system)
    }

    @Test
    fun `angelsaechsische Einheiten sind als eigenes System markiert`() {
        assertEquals(UnitSystem.IMPERIAL, IngredientUnit.CUP.system)
        assertEquals(UnitSystem.IMPERIAL, IngredientUnit.OUNCE.system)
    }
}
