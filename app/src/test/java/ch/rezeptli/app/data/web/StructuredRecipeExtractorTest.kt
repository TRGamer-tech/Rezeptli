package ch.rezeptli.app.data.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Die Vorlagen bilden nach, wie die echten Quellen ihre Daten ausliefern - erhoben
 * ueber einen Analyse-Lauf gegen die Seiten selbst (siehe docs/adr/0005-web-import.md).
 */
class StructuredRecipeExtractorTest {
    private val extractor = StructuredRecipeExtractor()

    private fun page(vararg jsonLd: String, body: String = "") = buildString {
        append("<html><head><title>Testseite</title>")
        jsonLd.forEach { append("""<script type="application/ld+json">$it</script>""") }
        append("</head><body>").append(body).append("</body></html>")
    }

    @Nested
    @DisplayName("JSON-LD vom Typ Recipe")
    inner class RecipeJsonLd {
        private val gutekuecheStyle = page(
            """
            {
              "@context": "https://schema.org",
              "@type": "Recipe",
              "name": "Gebratener Kartoffelsalat",
              "image": "https://example.ch/bild.jpg",
              "totalTime": "PT60M",
              "prepTime": "PT15M",
              "recipeYield": 4,
              "keywords": "Salat, Vegetarisch",
              "recipeIngredient": ["50 ml Buttermilch", "1 Stk Knoblauchzehe", "200 g Mayonnaise"],
              "recipeInstructions": [
                "1. Sauerrahm mit der Mayonnaise verrühren.",
                "2. Knoblauchzehe schälen und dazupressen."
              ]
            }
            """,
        )

        @Test
        fun `liest Titel, Zutaten und Zubereitung`() {
            val recipe = extractor.extract(gutekuecheStyle, "https://example.ch/salat", "Gutekueche")

            requireNotNull(recipe)
            assertEquals("Gebratener Kartoffelsalat", recipe.title)
            assertEquals(
                listOf("50 ml Buttermilch", "1 Stk Knoblauchzehe", "200 g Mayonnaise"),
                recipe.ingredientLines,
            )
            assertTrue(recipe.instructions.contains("Sauerrahm"))
            assertEquals(2, recipe.instructions.lines().size)
            assertEquals("https://example.ch/bild.jpg", recipe.imageUrl)
            assertEquals(60, recipe.totalMinutes)
            assertEquals("4", recipe.servings)
            assertEquals(listOf("Salat", "Vegetarisch"), recipe.keywords)
            assertEquals("https://example.ch/salat", recipe.sourceUrl)
        }

        @Test
        fun `versteht Zubereitungsschritte als HowToStep-Objekte`() {
            val html = page(
                """
                {
                  "@type": "Recipe",
                  "name": "Znüni-Müesli",
                  "recipeIngredient": ["80 g Haferflocken", "2 dl Milch"],
                  "recipeInstructions": [
                    {"@type": "HowToStep", "name": "Schritt 1", "text": "Haferflocken mit Milch mischen."},
                    {"@type": "HowToStep", "text": "Zugedeckt ziehen lassen."}
                  ],
                  "totalTime": "PT2H20M"
                }
                """,
            )

            val recipe = extractor.extract(html, "https://example.ch/muesli", "Migusto")

            requireNotNull(recipe)
            assertEquals(
                "Haferflocken mit Milch mischen.\nZugedeckt ziehen lassen.",
                recipe.instructions,
            )
            assertEquals(140, recipe.totalMinutes, "PT2H20M sind 140 Minuten")
        }

        @Test
        fun `findet das Rezept auch verschachtelt in einem Graph`() {
            val html = page(
                """
                {
                  "@context": "https://schema.org",
                  "@graph": [
                    {"@type": "WebSite", "name": "Blog"},
                    {"@type": ["Recipe"], "name": "Griechischer Salat",
                     "recipeIngredient": ["4 Eier", "120 g Zucker"],
                     "recipeInstructions": "Alles verrühren."}
                  ]
                }
                """,
            )

            val recipe = extractor.extract(html, "https://example.ch/salat", "Bettys Küchenschätze")

            requireNotNull(recipe)
            assertEquals("Griechischer Salat", recipe.title)
            assertEquals(listOf("4 Eier", "120 g Zucker"), recipe.ingredientLines)
            assertEquals("Alles verrühren.", recipe.instructions)
        }

        @Test
        fun `addiert Vorbereitungs- und Kochzeit wenn keine Gesamtzeit angegeben ist`() {
            val html = page(
                """
                {"@type": "Recipe", "name": "Braten", "recipeIngredient": ["1 kg Fleisch"],
                 "prepTime": "PT45M", "cookTime": "PT45M"}
                """,
            )

            assertEquals(90, extractor.extract(html, "https://example.ch/x", "Betty Bossi")?.totalMinutes)
        }

        @Test
        fun `nimmt bei mehreren Bildern das erste und liest ImageObject`() {
            val html = page(
                """
                {"@type": "Recipe", "name": "A", "recipeIngredient": ["1 Ei"],
                 "image": [{"@type": "ImageObject", "url": "https://example.ch/a.jpg"}]}
                """,
            )

            assertEquals("https://example.ch/a.jpg", extractor.extract(html, "https://example.ch/x", "Q")?.imageUrl)
        }
    }

    @Nested
    @DisplayName("Swissmilk: HowTo mit Microdata-Zutaten")
    inner class HowToWithMicrodata {
        private val swissmilkStyle = page(
            """
            {
              "@context": "https://schema.org",
              "@type": "HowTo",
              "name": "Aprikosen-Blechkuchen ",
              "totalTime": 45,
              "step": [
                {"@type": "HowToStep", "position": 1,
                 "itemListElement": {"@type": "HowToDirection", "text": "Ofen auf 180°C vorheizen. "}},
                {"@type": "HowToStep", "position": 2,
                 "itemListElement": {"@type": "HowToDirection", "text": "Teig in das Blech geben. "}}
              ]
            }
            """,
            body = """
                <ul>
                  <li itemprop="recipeIngredient">200 g Mehl</li>
                  <li itemprop="recipeIngredient">1 &nbsp;Prise Salz</li>
                  <li itemprop="recipeIngredient">2 dl Rahm</li>
                </ul>
            """,
        )

        @Test
        fun `kombiniert Anleitung aus HowTo mit Zutaten aus Microdata`() {
            val recipe = extractor.extract(swissmilkStyle, "https://www.swissmilk.ch/x", "Swissmilk")

            requireNotNull(recipe)
            assertEquals("Aprikosen-Blechkuchen", recipe.title, "Nachlaufende Leerzeichen fallen weg")
            assertEquals(listOf("200 g Mehl", "1 Prise Salz", "2 dl Rahm"), recipe.ingredientLines)
            assertEquals(
                "Ofen auf 180°C vorheizen.\nTeig in das Blech geben.",
                recipe.instructions,
            )
            assertEquals(45, recipe.totalMinutes, "Swissmilk liefert die Minuten als blosse Zahl")
        }
    }

    @Nested
    @DisplayName("Rueckfall und Fehlerfaelle")
    inner class Fallbacks {
        @Test
        fun `liest reine Microdata ohne JSON-LD`() {
            val html = page(
                body = """
                    <h1 itemprop="name">Rösti</h1>
                    <li itemprop="recipeIngredient">800 g Kartoffeln</li>
                    <div itemprop="recipeInstructions">Raffeln und braten.</div>
                """,
            )

            val recipe = extractor.extract(html, "https://example.ch/roesti", "Quelle")

            requireNotNull(recipe)
            assertEquals("Rösti", recipe.title)
            assertEquals(listOf("800 g Kartoffeln"), recipe.ingredientLines)
            assertEquals("Raffeln und braten.", recipe.instructions)
        }

        @Test
        fun `liefert null wenn die Seite kein Rezept enthaelt`() {
            assertNull(extractor.extract(page(), "https://example.ch/", "Quelle"))
        }

        @Test
        fun `liefert null bei einem Rezept ohne Titel und Inhalt`() {
            val html = page("""{"@type": "Recipe", "name": ""}""")

            assertNull(extractor.extract(html, "https://example.ch/", "Quelle"))
        }

        @Test
        fun `ueberspringt kaputtes JSON und nutzt den naechsten Block`() {
            val html = page(
                "{ das ist kein JSON",
                """{"@type": "Recipe", "name": "Rettung", "recipeIngredient": ["1 Ei"]}""",
            )

            assertEquals("Rettung", extractor.extract(html, "https://example.ch/", "Q")?.title)
        }

        @Test
        fun `entfernt HTML aus Zubereitungstexten`() {
            val html = page(
                """
                {"@type": "Recipe", "name": "A", "recipeIngredient": ["1 Ei"],
                 "recipeInstructions": "<p>Erst r&uuml;hren,</p><p>dann backen.</p>"}
                """,
            )

            val instructions = extractor.extract(html, "https://example.ch/", "Q")?.instructions
            assertTrue(instructions!!.contains("rühren"), "Entities werden aufgeloest: $instructions")
            assertTrue(!instructions.contains("<p>"), "HTML-Reste bleiben nicht stehen")
        }
    }
}
