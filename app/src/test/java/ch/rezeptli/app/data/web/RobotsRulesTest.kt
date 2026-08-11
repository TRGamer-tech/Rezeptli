package ch.rezeptli.app.data.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Die Vorlage ist die echte robots.txt von Chefkoch - der anspruchsvollste Fall unter
 * den unterstuetzten Quellen, mit Platzhaltern, Anker am Zeilenende und einer langen
 * Liste vollstaendig ausgesperrter Abrufer.
 */
class RobotsRulesTest {
    private val parser = RobotsParser()

    private val chefkoch: String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("chefkoch-robots.txt"))
            .bufferedReader()
            .readText()

    private fun rulesFor(agent: String) = parser.parse(chefkoch, agent)

    @Nested
    @DisplayName("Rezeptli faellt unter die allgemeinen Regeln")
    inner class GeneralRules {
        private val rules = rulesFor("Rezeptli")

        @ParameterizedTest
        @ValueSource(
            strings = [
                "/rezepte/1234567890/Zuercher-Geschnetzeltes.html",
                "/rezepte/drucken/1234567890/",
                "/rs/s0o6/Rezepte.html",
                "/",
            ],
        )
        fun `erlaubt Rezeptseiten`(path: String) {
            assertTrue(rules.isAllowed(path), "Sollte erlaubt sein: $path")
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "/rezepte/kommentare/1234/",
                "/rezepte/wertungen/1234/",
                "/kochbuch/meins",
                "/user/rezepte/12",
                "/cms/irgendwas",
                "/mein-kochbuch/rezept-import/auto",
            ],
        )
        fun `verbietet was die Seite ausgenommen hat`(path: String) {
            assertFalse(rules.isAllowed(path), "Sollte verboten sein: $path")
        }

        @Test
        fun `verbietet Rezeptadressen mit Abfrageparametern`() {
            assertFalse(
                rules.isAllowed("/rezepte/1234/titel.html?portionen=4"),
                "Disallow: /rezepte/*?* schliesst Adressen mit Parametern aus",
            )
            assertTrue(rules.isAllowed("/rezepte/1234/titel.html"))
        }

        @Test
        fun `beachtet den Anker am Zeilenende`() {
            assertFalse(rules.isAllowed("/rezepte/1234/rewe/rewe.html"))
            assertTrue(rules.isAllowed("/rezepte/1234/rewe/rewe.html.txt"))
        }

        @Test
        fun `beachtet Platzhalter mitten im Muster`() {
            assertFalse(rules.isAllowed("/rs/s0tirgendwas"))
        }
    }

    @Nested
    @DisplayName("Eigene Gruppen fuer bestimmte Abrufer")
    inner class SpecificAgents {
        @ParameterizedTest
        @ValueSource(strings = ["ClaudeBot", "anthropic-ai", "GPTBot", "scrapy"])
        fun `sperrt die ausdruecklich genannten Crawler komplett aus`(agent: String) {
            assertFalse(rulesFor(agent).isAllowed("/rezepte/1234/titel.html"))
        }

        @Test
        fun `nimmt fuer unbekannte Abrufer die allgemeine Gruppe`() {
            assertTrue(rulesFor("Rezeptli/1.0").isAllowed("/rezepte/1234/titel.html"))
        }

        @Test
        fun `beachtet eine ausdrueckliche Erlaubnis`() {
            assertTrue(rulesFor("facebookexternalhit").isAllowed("/kochbuch/meins"))
        }
    }

    @Nested
    @DisplayName("Weitere Faelle")
    inner class Misc {
        @Test
        fun `liest ein Crawl-delay in Millisekunden`() {
            val rules = parser.parse("User-agent: *\nCrawl-delay: 10\nDisallow: /x", "Rezeptli")

            assertEquals(10_000L, rules.crawlDelayMs)
        }

        @Test
        fun `bei laengerer Regel gewinnt die genauere`() {
            val rules = parser.parse(
                "User-agent: *\nDisallow: /rezepte/\nAllow: /rezepte/gut/",
                "Rezeptli",
            )

            assertFalse(rules.isAllowed("/rezepte/anderes"))
            assertTrue(rules.isAllowed("/rezepte/gut/eins"))
        }

        @Test
        fun `ignoriert Kommentare und leere Eintraege`() {
            val rules = parser.parse(
                "# Kommentar\nUser-agent: *\nDisallow:\nDisallow: /geheim # nicht oeffentlich",
                "Rezeptli",
            )

            assertTrue(rules.isAllowed("/"), "Ein leeres Disallow erlaubt alles")
            assertFalse(rules.isAllowed("/geheim"))
        }

        @Test
        fun `fasst mehrere User-agent-Zeilen zu einer Gruppe zusammen`() {
            val rules = parser.parse("User-agent: a\nUser-agent: b\nDisallow: /x", "b")

            assertFalse(rules.isAllowed("/x"))
        }

        @Test
        fun `erlaubt alles wenn keine Regeln vorliegen`() {
            assertTrue(RobotsRules.PERMISSIVE.isAllowed("/beliebig"))
        }
    }
}
