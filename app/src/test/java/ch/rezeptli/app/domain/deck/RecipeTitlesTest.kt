package ch.rezeptli.app.domain.deck

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RecipeTitlesTest {
    @Test
    fun `eine rohe Adresse als Titel wird zum lesbaren Namen`() {
        val url = "https://migusto.migros.ch/de/rezepte/feta-pasta"

        assertEquals("Feta Pasta", RecipeTitles.clean(url, url, fallback = "Migusto"))
    }

    @Test
    fun `endet die Adresse auf einer Nummer, zaehlt der Abschnitt davor`() {
        assertEquals("Feta Pasta", RecipeTitles.fromUrl("https://migusto.migros.ch/de/rezepte/feta-pasta/12345"))
    }

    @Test
    fun `bleibt nur eine Rubrik, gibt es keinen Titel aus der Adresse`() {
        assertNull(RecipeTitles.fromUrl("https://migusto.migros.ch/de/rezepte/98765"))
        assertEquals("Migusto", RecipeTitles.clean("", "https://migusto.migros.ch/de/rezepte/98765", "Migusto"))
    }

    @Test
    fun `kodierte Umlaute werden lesbar`() {
        assertEquals("Älplermagronen", RecipeTitles.fromUrl("https://beispiel.ch/rezepte/%C3%A4lplermagronen"))
    }

    @Test
    fun `ein echter Titel bleibt, wie er ist`() {
        assertEquals("Rösti mit Spiegelei", RecipeTitles.clean("Rösti mit Spiegelei", "https://x.ch/a", "X"))
    }

    @Test
    fun `sehr lange Titel werden gekuerzt`() {
        val lang = "Schnelle Einfache Leckere Pasta Mit Tomaten Und Basilikum Fuer Die Ganze Familie"

        val gekuerzt = RecipeTitles.clean(lang, "https://x.ch/a", "X")

        assertEquals(RecipeTitles.MAX_WORDS + 1, gekuerzt.split(" ").size)
        assertEquals(true, gekuerzt.endsWith("…"))
    }
}
