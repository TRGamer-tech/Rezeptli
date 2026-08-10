package ch.rezeptli.app.domain.parser

import ch.rezeptli.app.domain.model.SuggestedTags
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecipeTextParserTest {
    private val parser = RecipeTextParser(IngredientTextParser())

    @Test
    fun `zerlegt ein typisches aus Google Docs kopiertes Rezept`() {
        val text =
            """
            Älplermagronen

            Zubereitungszeit: 40 Minuten
            Portionen: 4

            Zutaten
            400 g Hörnli
            4 mittelgrosse Kartoffeln
            2 dl Vollrahm
            2 dl Milch
            200 g Käse, gerieben
            2 Zwiebeln
            Salz nach Belieben

            Zubereitung
            Kartoffeln schälen und in Würfel schneiden.
            Hörnli und Kartoffeln zusammen im Salzwasser kochen.
            Rahm und Milch aufkochen, Käse darin schmelzen lassen.
            Zwiebeln in Ringe schneiden und goldbraun braten.
            """.trimIndent()

        val result = parser.parse(text)

        assertEquals("Älplermagronen", result.title)
        assertEquals(40, result.prepTimeMinutes)
        assertEquals(7, result.ingredients.size)
        assertEquals(
            listOf("Hörnli", "mittelgrosse Kartoffeln", "Vollrahm", "Milch", "Käse", "Zwiebeln", "Salz"),
            result.ingredients.map { it.name },
        )
        assertEquals("gerieben", result.ingredients[4].note)
        assertTrue(result.instructions.startsWith("Kartoffeln schälen"))
        assertTrue(result.instructions.lines().size == 4, "Zubereitung: ${result.instructions}")
    }

    @Test
    fun `erkennt Zutaten und Zubereitung auch ohne Ueberschriften`() {
        val text =
            """
            Tomatensugo
            2 Dosen Pelati
            1 Zwiebel
            2 EL Olivenöl
            Zwiebel im Olivenöl andünsten, Pelati dazugeben und 20 Minuten köcheln lassen.
            """.trimIndent()

        val result = parser.parse(text)

        assertEquals("Tomatensugo", result.title)
        assertEquals(listOf("Pelati", "Zwiebel", "Olivenöl"), result.ingredients.map { it.name })
        assertTrue(result.instructions.contains("köcheln"))
    }

    @Test
    fun `erkennt Abschnittsueberschriften innerhalb der Zutaten`() {
        val text =
            """
            Wähe

            Zutaten
            Für den Teig:
            250 g Mehl
            125 g Butter

            Für den Guss:
            2 dl Rahm
            2 Eier

            Zubereitung
            Teig auswallen und in eine Form legen.
            """.trimIndent()

        val result = parser.parse(text)

        assertEquals("Wähe", result.title)
        assertEquals(listOf("Mehl", "Butter", "Rahm", "Eier"), result.ingredients.map { it.name })
        assertEquals("Teig auswallen und in eine Form legen.", result.instructions)
    }

    @Test
    fun `rechnet Stunden in Minuten um`() {
        val text =
            """
            Schmorbraten
            Zubereitungszeit: 2 Stunden 30 Minuten
            Zutaten
            1 kg Rindfleisch
            """.trimIndent()

        val result = parser.parse(text)

        assertEquals(150, result.prepTimeMinutes)
    }

    @Test
    fun `schlaegt Tags aus dem Text vor`() {
        val text =
            """
            Gemüsecurry
            Ein schnelles vegetarisches Gericht.
            Zutaten
            200 g Reis
            """.trimIndent()

        val result = parser.parse(text)

        assertTrue(SuggestedTags.VEGETARISCH in result.tags, "Tags: ${result.tags}")
    }

    @Test
    fun `markiert kurze Rezepte automatisch als schnell`() {
        val text =
            """
            Rührei
            Zubereitungszeit: 10 Minuten
            Zutaten
            4 Eier
            """.trimIndent()

        val result = parser.parse(text)

        assertTrue(SuggestedTags.SCHNELL in result.tags, "Tags: ${result.tags}")
    }

    @Test
    fun `liefert ein leeres Ergebnis fuer leeren Text`() {
        val result = parser.parse("   \n  \n")

        assertTrue(result.isEmpty)
        assertEquals("", result.title)
        assertNull(result.prepTimeMinutes)
    }

    @Test
    fun `verliert keine Zeilen die nicht als Zutat erkannt werden`() {
        val text =
            """
            Suppe
            Zutaten
            1 l Bouillon
            Diese Suppe schmeckt am nächsten Tag noch besser.
            """.trimIndent()

        val result = parser.parse(text)

        assertEquals(listOf("Bouillon"), result.ingredients.map { it.name })
        assertTrue(
            result.instructions.contains("nächsten Tag"),
            "Nicht erkannte Zeilen muessen in der Zubereitung landen: ${result.instructions}",
        )
    }
}
