package ch.rezeptli.app.domain.shopping

import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.shopping.UnitConverter.dimension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

private const val DELTA = 0.001

class UnitConverterTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "GRAMM, MASS",
        "KILOGRAMM, MASS",
        "MILLILITER, VOLUME",
        "DEZILITER, VOLUME",
        "LITER, VOLUME",
        "TEELOEFFEL, SPOON",
        "KAFFEELOEFFEL, SPOON",
        "ESSLOEFFEL, SPOON",
        "NONE, COUNT",
        "STUECK, COUNT",
        "BUND, DISCRETE",
        "DOSE, DISCRETE",
        "PRISE, DISCRETE",
    )
    fun `ordnet Einheiten der richtigen Groessenart zu`(unit: IngredientUnit, expected: UnitDimension) {
        assertEquals(expected, unit.dimension)
    }

    @ParameterizedTest(name = "{0} {1} -> {2} Basiseinheiten")
    @CsvSource(
        "2, KILOGRAMM, 2000",
        "500, GRAMM, 500",
        "2, DEZILITER, 200",
        "1, LITER, 1000",
        "2, ESSLOEFFEL, 6",
        "1, TEELOEFFEL, 1",
    )
    fun `rechnet in die Basiseinheit um`(amount: Double, unit: IngredientUnit, expected: Double) {
        assertEquals(expected, UnitConverter.toBase(amount, unit), DELTA)
    }

    @Test
    fun `waehlt Kilogramm ab tausend Gramm`() {
        assertEquals(
            1.5 to IngredientUnit.KILOGRAMM,
            UnitConverter.fromBase(1500.0, UnitDimension.MASS, IngredientUnit.GRAMM),
        )
        assertEquals(
            999.0 to IngredientUnit.GRAMM,
            UnitConverter.fromBase(999.0, UnitDimension.MASS, IngredientUnit.GRAMM),
        )
    }

    @Test
    fun `waehlt Deziliter als Schweizer Standard bei Fluessigkeiten`() {
        assertEquals(
            2.5 to IngredientUnit.DEZILITER,
            UnitConverter.fromBase(250.0, UnitDimension.VOLUME, IngredientUnit.MILLILITER),
        )
        assertEquals(
            1.2 to IngredientUnit.LITER,
            UnitConverter.fromBase(1200.0, UnitDimension.VOLUME, IngredientUnit.MILLILITER),
        )
        assertEquals(
            50.0 to IngredientUnit.MILLILITER,
            UnitConverter.fromBase(50.0, UnitDimension.VOLUME, IngredientUnit.MILLILITER),
        )
    }

    @Test
    fun `behaelt bei Stueckzahlen und Sondereinheiten die Ausgangseinheit`() {
        assertEquals(
            3.0 to IngredientUnit.BUND,
            UnitConverter.fromBase(3.0, UnitDimension.DISCRETE, IngredientUnit.BUND),
        )
        assertEquals(4.0 to IngredientUnit.NONE, UnitConverter.fromBase(4.0, UnitDimension.COUNT, IngredientUnit.NONE))
    }
}
