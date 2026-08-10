package ch.rezeptli.app.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AmountFormatterTest {
    @ParameterizedTest(name = "{0} -> \"{1}\"")
    @CsvSource(
        "2.0, '2'",
        "0.5, '½'",
        "1.5, '1½'",
        "0.25, '¼'",
        "2.75, '2¾'",
        "0.3, '0,3'",
        "1.2, '1,2'",
        "200.0, '200'",
        "0.0, '0'",
    )
    fun `formatiert Mengen kochbuchtauglich`(amount: Double, expected: String) {
        assertEquals(expected, AmountFormatter.format(amount))
    }

    @Test
    fun `formatiert null als leeren String`() {
        assertEquals("", AmountFormatter.format(null))
    }

    @ParameterizedTest(name = "{0} -> \"{1}\"")
    @CsvSource(
        "2.0, '2'",
        "1.5, '1,5'",
        "0.25, '0,25'",
    )
    fun `nutzt fuer Eingabefelder die Dezimalschreibweise`(amount: Double, expected: String) {
        assertEquals(expected, AmountFormatter.formatForInput(amount))
    }
}
