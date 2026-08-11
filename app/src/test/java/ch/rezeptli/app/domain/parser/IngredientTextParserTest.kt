package ch.rezeptli.app.domain.parser

import ch.rezeptli.app.domain.model.IngredientUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

private const val DELTA = 0.0001

class IngredientTextParserTest {
    private val parser = IngredientTextParser()

    @Nested
    @DisplayName("Menge, Einheit und Name")
    inner class BasicPatterns {
        @Test
        fun `erkennt Menge Einheit Zutat`() {
            val result = parser.parseLine("200 g Mehl")

            requireNotNull(result)
            assertEquals(200.0, result.amount!!, DELTA)
            assertEquals(IngredientUnit.GRAMM, result.unit)
            assertEquals("Mehl", result.name)
            assertEquals(ParseConfidence.HIGH, result.confidence)
        }

        @Test
        fun `erkennt Menge ohne Leerzeichen vor der Einheit`() {
            val result = parser.parseLine("200g Mehl")

            requireNotNull(result)
            assertEquals(200.0, result.amount!!, DELTA)
            assertEquals(IngredientUnit.GRAMM, result.unit)
            assertEquals("Mehl", result.name)
        }

        @Test
        fun `erkennt Zutat ohne Einheit`() {
            val result = parser.parseLine("2 Eier")

            requireNotNull(result)
            assertEquals(2.0, result.amount!!, DELTA)
            assertEquals(IngredientUnit.NONE, result.unit)
            assertEquals("Eier", result.name)
            assertEquals(ParseConfidence.HIGH, result.confidence)
        }

        @Test
        fun `erkennt Zutat ganz ohne Menge`() {
            val result = parser.parseLine("Salz")

            requireNotNull(result)
            assertNull(result.amount)
            assertEquals(IngredientUnit.NONE, result.unit)
            assertEquals("Salz", result.name)
            assertEquals(ParseConfidence.MEDIUM, result.confidence)
        }

        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource(
            "'1 dl Rahm', DEZILITER",
            "'2 EL Öl', ESSLOEFFEL",
            "'1 TL Salz', TEELOEFFEL",
            "'1 KL Zucker', KAFFEELOEFFEL",
            "'5 dl Milch', DEZILITER",
            "'1 kg Kartoffeln', KILOGRAMM",
            "'250 ml Wasser', MILLILITER",
            "'1 l Bouillon', LITER",
            "'1 Prise Muskat', PRISE",
            "'2 Zehen Knoblauch', ZEHE",
            "'1 Bund Peterli', BUND",
            "'1 Pck. Backpulver', PACKUNG",
            "'1 Msp. Zimt', MESSERSPITZE",
            "'2 Dosen Pelati', DOSE",
            "'3 Scheiben Brot', SCHEIBE",
        )
        fun `erkennt gebraeuchliche Einheiten`(line: String, expected: IngredientUnit) {
            val result = parser.parseLine(line)

            requireNotNull(result)
            assertEquals(expected, result.unit, "Einheit aus \"$line\"")
        }

        @Test
        @DisplayName("dl wird als Schweizer Standardeinheit erkannt")
        fun `erkennt Deziliter`() {
            val result = parser.parseLine("2.5 dl Vollrahm")

            requireNotNull(result)
            assertEquals(2.5, result.amount!!, DELTA)
            assertEquals(IngredientUnit.DEZILITER, result.unit)
            assertEquals("Vollrahm", result.name)
        }
    }

    @Nested
    @DisplayName("Zahlenformate")
    inner class NumberFormats {
        @ParameterizedTest(name = "\"{0}\" -> {1}")
        @CsvSource(
            "'1,5 dl Milch', 1.5",
            "'1.5 dl Milch', 1.5",
            "'1/2 TL Salz', 0.5",
            "'½ TL Salz', 0.5",
            "'2 1/2 dl Milch', 2.5",
            "'1½ dl Rahm', 1.5",
            "'¼ TL Pfeffer', 0.25",
            "'3/4 dl Wasser', 0.75",
            "'12 Stk. Cherrytomaten', 12.0",
        )
        fun `erkennt Zahlenschreibweisen`(line: String, expected: Double) {
            val result = parser.parseLine(line)

            requireNotNull(result)
            assertEquals(expected, result.amount!!, DELTA, "Menge aus \"$line\"")
        }

        @Test
        fun `nimmt bei einem Bereich die untere Grenze und senkt die Konfidenz`() {
            val result = parser.parseLine("2-3 EL Olivenöl")

            requireNotNull(result)
            assertEquals(2.0, result.amount!!, DELTA)
            assertEquals(IngredientUnit.ESSLOEFFEL, result.unit)
            assertEquals("Olivenöl", result.name)
            assertEquals(ParseConfidence.MEDIUM, result.confidence)
        }

        @Test
        fun `versteht auch bis als Bereichstrenner`() {
            val result = parser.parseLine("2 bis 3 EL Olivenöl")

            requireNotNull(result)
            assertEquals(2.0, result.amount!!, DELTA)
            assertEquals(IngredientUnit.ESSLOEFFEL, result.unit)
        }

        @Test
        fun `ignoriert ein vorangestelltes ca`() {
            val result = parser.parseLine("ca. 200 g Mehl")

            requireNotNull(result)
            assertEquals(200.0, result.amount!!, DELTA)
            assertEquals(IngredientUnit.GRAMM, result.unit)
            assertEquals("Mehl", result.name)
        }
    }

    @Nested
    @DisplayName("Aufzaehlungszeichen und Notizen")
    inner class BulletsAndNotes {
        @ParameterizedTest
        @ValueSource(strings = ["- 200 g Mehl", "• 200 g Mehl", "* 200 g Mehl", "  –  200 g Mehl"])
        fun `entfernt Aufzaehlungszeichen`(line: String) {
            val result = parser.parseLine(line)

            requireNotNull(result)
            assertEquals("Mehl", result.name)
            assertEquals(200.0, result.amount!!, DELTA)
        }

        @Test
        fun `trennt eine Zubereitungsnotiz ab`() {
            val result = parser.parseLine("1 Zwiebel, fein gehackt")

            requireNotNull(result)
            assertEquals("Zwiebel", result.name)
            assertEquals("fein gehackt", result.note)
            assertEquals(1.0, result.amount!!, DELTA)
        }

        @Test
        fun `behaelt einen Komma-Zusatz im Namen wenn er keine Zubereitung beschreibt`() {
            val result = parser.parseLine("200 g Mehl, Zucker")

            requireNotNull(result)
            assertEquals("Mehl, Zucker", result.name)
            assertNull(result.note)
        }

        @Test
        fun `erkennt nach Belieben als Notiz ohne Menge`() {
            val result = parser.parseLine("Salz nach Belieben")

            requireNotNull(result)
            assertEquals("Salz", result.name)
            assertNull(result.amount)
            assertEquals("nach Belieben", result.note)
        }

        @Test
        fun `erkennt etwas als Mengenangabe ohne Zahl`() {
            val result = parser.parseLine("etwas Öl zum Braten")

            requireNotNull(result)
            assertEquals("Öl", result.name)
            assertNull(result.amount)
            assertTrue(result.note!!.contains("etwas"), "Notiz war: ${result.note}")
        }
    }

    @Nested
    @DisplayName("Nicht verwertbare Zeilen")
    inner class RejectedLines {
        @ParameterizedTest
        @ValueSource(strings = ["", "   ", "Zutaten:", "Für den Teig:", "200 g", "2 dl"])
        fun `liefert null fuer Zeilen ohne Zutat`(line: String) {
            assertNull(parser.parseLine(line), "Zeile: \"$line\"")
        }

        @Test
        fun `markiert Fliesstext mit tiefer Konfidenz`() {
            val result = parser.parseLine("Die Zwiebeln in etwas Öl andünsten, dann den Reis dazugeben.")

            requireNotNull(result)
            assertEquals(ParseConfidence.LOW, result.confidence)
        }

        @Test
        fun `erkennt Fliesstext nicht als Zutatenzeile`() {
            assertTrue(!parser.looksLikeIngredientLine("Den Ofen auf 200 Grad vorheizen und alles 30 Minuten backen."))
        }

        @Test
        fun `erkennt eine normale Zutatenzeile als solche`() {
            assertTrue(parser.looksLikeIngredientLine("200 g Mehl"))
        }
    }

    @Nested
    @DisplayName("Synonyme")
    inner class Synonyms {
        @Test
        fun `haelt die hochdeutsche Normalform eines Schweizer Begriffs fest`() {
            val result = parser.parseLine("300 g Rüebli")

            requireNotNull(result)
            assertEquals("Rüebli", result.name)
            assertEquals("Karotte", result.canonicalName)
        }

        @Test
        fun `loest auch zusammengesetzte Angaben auf`() {
            val result = parser.parseLine("2 dl Vollrahm")

            requireNotNull(result)
            assertEquals("Sahne", result.canonicalName)
        }

        @Test
        fun `laesst unbekannte Zutaten ohne Normalform`() {
            val result = parser.parseLine("100 g Sbrinz")

            requireNotNull(result)
            assertNull(result.canonicalName)
        }
    }

    @Nested
    @DisplayName("Mehrzeiliger Block")
    inner class MultiLine {
        @Test
        fun `parst einen kompletten Zutatenblock`() {
            val block =
                """
                Zutaten:
                - 200 g Mehl
                - 1 ½ dl Milch
                - 2 Eier

                1 Prise Salz
                """.trimIndent()

            val result = parser.parse(block)

            assertEquals(4, result.size)
            assertEquals(listOf("Mehl", "Milch", "Eier", "Salz"), result.map { it.name })
            assertEquals(1.5, result[1].amount!!, DELTA)
            assertEquals(IngredientUnit.PRISE, result[3].unit)
        }
    }
}
