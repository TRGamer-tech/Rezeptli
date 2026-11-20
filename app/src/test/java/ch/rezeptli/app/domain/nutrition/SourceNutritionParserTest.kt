package ch.rezeptli.app.domain.nutrition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Die Naehrwertangaben der Rezeptseiten - so verschieden, wie sie geschrieben werden.
 *
 * Geglaubt wird nur, was plausibel ist: Sonst zeigte die App eine falsche Zahl als "laut
 * Quelle", und das waere schlimmer als eine ehrliche Schaetzung.
 */
class SourceNutritionParserTest {
    private fun kcal(text: String?) = SourceNutritionParser.parse(calories = text, protein = null)?.kcal

    private fun eiweiss(text: String?) = SourceNutritionParser
        .parse(
            calories = "500 kcal",
            protein = text,
        )?.proteinGrams

    @Test
    fun `Kalorien stehen mit und ohne Einheit da`() {
        assertEquals(512, kcal("512 kcal"))
        assertEquals(512, kcal("512kcal"))
        assertEquals(512, kcal("512 calories"))
        assertEquals(512, kcal("512 Kalorien"))
        assertEquals(512, kcal("512 cal"))
        assertEquals(512, kcal("512"))
        assertEquals(512, kcal("512.0"))
        assertEquals(513, kcal("512,6 kcal"))
        assertEquals(512, kcal("ca. 512 kcal pro Portion"))
    }

    @Test
    fun `Kilojoule werden umgerechnet - und neben Kalorien zaehlen die Kalorien`() {
        assertEquals(512, kcal("2143 kJ"))
        assertEquals(512, kcal("2143 kJ / 512 kcal"))
        assertEquals(512, kcal("2143 kJ (512 kcal)"))
    }

    @Test
    fun `Tausenderzeichen sind keine Nachkommastellen`() {
        assertEquals(1234, kcal("1'234 kcal"))
        assertEquals(1234, kcal("1.234 kcal"))
        assertEquals(1234, kcal("1,234 kcal"))
        assertEquals(1235, kcal("1.234,6 kcal"))
    }

    @Test
    fun `Eiweiss in Gramm, mit Komma oder Punkt, auch in Milligramm`() {
        assertEquals(23.0, eiweiss("23 g"))
        assertEquals(23.5, eiweiss("23,5 g"))
        assertEquals(23.5, eiweiss("23.5g"))
        assertEquals(23.0, eiweiss("23"))
        assertEquals(23.0, eiweiss("23 grams"))
        assertEquals(0.9, eiweiss("900 mg"))
        // Ein Drink hat wirklich keines.
        assertEquals(0.0, eiweiss("0 g"))
    }

    @Test
    fun `was nicht plausibel ist, gilt als nicht genannt`() {
        assertNull(kcal("0 kcal"))
        assertNull(kcal("0"))
        assertNull(kcal(""))
        assertNull(kcal("k. A."))
        assertNull(kcal(null))
        // Mehr hat keine Portion.
        assertNull(kcal("25000 kcal"))
        assertNull(eiweiss("900 g"))
        assertNull(eiweiss("viel"))
    }

    @Test
    fun `ohne Kalorien ist null Gramm Eiweiss ein leeres Feld, keine Angabe`() {
        assertNull(SourceNutritionParser.parse(calories = null, protein = "0 g"))
        assertNull(SourceNutritionParser.parse(calories = "", protein = ""))
        assertNull(SourceNutritionParser.parse(calories = null, protein = null))
        // Eiweiss allein gilt, wenn es etwas sagt.
        assertEquals(SourceNutrition(kcal = null, proteinGrams = 18.0), SourceNutritionParser.parse(null, "18 g"))
    }

    @Test
    fun `Angaben pro 100 Gramm sagen nichts ueber die Portion`() {
        assertNull(SourceNutritionParser.parse("180 kcal", "7 g", servingSize = "100 g"))
        assertNull(SourceNutritionParser.parse("180 kcal", "7 g", servingSize = "pro 100g"))
        assertNull(SourceNutritionParser.parse("45 kcal", "1 g", servingSize = "100 ml"))
        // Eine Portion von 350 g ist eine Portion.
        assertEquals(
            SourceNutrition(kcal = 630, proteinGrams = 24.0),
            SourceNutritionParser.parse("630 kcal", "24 g", servingSize = "1 Portion (350 g)"),
        )
        assertEquals(SourceNutrition(630, 24.0), SourceNutritionParser.parse("630 kcal", "24 g", servingSize = "1"))
    }
}
