package ch.rezeptli.app.data.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SitemapParserTest {
    private val parser = SitemapParser()

    @Test
    fun `erkennt ein Verzeichnis von Verzeichnissen`() {
        assertTrue(parser.isIndex("<sitemapindex><sitemap><loc>a</loc></sitemap></sitemapindex>"))
        assertFalse(parser.isIndex("<urlset><url><loc>a</loc></url></urlset>"))
    }

    @Test
    fun `liest alle Adressen`() {
        val xml = "<urlset><url><loc> https://a.ch/1 </loc></url><url><loc>https://a.ch/2</loc></url></urlset>"

        assertEquals(listOf("https://a.ch/1", "https://a.ch/2"), parser.locations(xml))
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "'https://www.swissmilk.ch/de/rezepte-kochideen/rezepte/SM01/aprikosen-blechkuchen/', 'Aprikosen Blechkuchen'",
        "'https://www.gutekueche.ch/gebratener-kartoffelsalat-rezept-6', 'Gebratener Kartoffelsalat'",
        "'https://migusto.migros.ch/de/rezepte/feta-pasta', 'Feta Pasta'",
        "'https://fooby.ch/de/rezepte/12345/riz-casimir.html', 'Riz Casimir'",
        "'https://www.bettybossi.ch/de/rezepte/rezept/zuppa-leggera-10002116/', 'Zuppa Leggera'",
    )
    fun `leitet aus der Adresse einen lesbaren Titel ab`(url: String, expected: String) {
        assertEquals(expected, parser.titleFromUrl(url))
    }
}
