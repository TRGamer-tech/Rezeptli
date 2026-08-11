package ch.rezeptli.app.data.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class IsoDurationTest {
    @ParameterizedTest(name = "\"{0}\" -> {1} Minuten")
    @CsvSource(
        "PT60M, 60",
        "PT2H20M, 140",
        "PT1H, 60",
        "PT165M, 165",
        "P1DT2H, 1560",
        "pt30m, 30",
        "45, 45",
        "'  PT15M  ', 15",
    )
    fun `liest Zeitangaben in beiden Schreibweisen`(value: String, expected: Int) {
        assertEquals(expected, IsoDuration.toMinutes(value))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "irgendwas", "PT0M", "0", "P"])
    fun `liefert null wenn nichts Sinnvolles drinsteht`(value: String) {
        assertNull(IsoDuration.toMinutes(value))
    }

    @Test
    fun `liefert null fuer null`() {
        assertNull(IsoDuration.toMinutes(null))
    }
}
