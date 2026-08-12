package ch.rezeptli.app.data.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WebUrlTest {
    private val seite = "https://www.kochrezepte.at/rindsbraten-rezept-4456"

    @Test
    fun `laesst eine vollstaendige Adresse in Ruhe`() {
        assertEquals(
            "https://bild.example/foto.jpg",
            WebUrl.absolute("https://bild.example/foto.jpg", seite),
        )
    }

    @Test
    fun `ergaenzt das Protokoll bei einer schemalosen Adresse`() {
        // Genau die Form, an der die Bilder von kochrezepte.at gescheitert sind.
        assertEquals(
            "https://img2.kochrezepte.at/use/5/braten_5446.jpg",
            WebUrl.absolute("//img2.kochrezepte.at/use/5/braten_5446.jpg", seite),
        )
    }

    @Test
    fun `uebernimmt dabei das Protokoll der Seite`() {
        assertEquals(
            "http://bild.example/foto.jpg",
            WebUrl.absolute("//bild.example/foto.jpg", "http://alte.example/rezept"),
        )
    }

    @Test
    fun `haengt eine wurzelrelative Adresse an den Host`() {
        assertEquals(
            "https://www.kochrezepte.at/bilder/foto.jpg",
            WebUrl.absolute("/bilder/foto.jpg", seite),
        )
    }

    @Test
    fun `loest eine verzeichnisrelative Adresse auf`() {
        assertEquals(
            "https://example.ch/rezepte/foto.jpg",
            WebUrl.absolute("foto.jpg", "https://example.ch/rezepte/braten"),
        )
    }

    @Test
    fun `ignoriert Abfrage und Anker beim Aufloesen`() {
        assertEquals(
            "https://example.ch/rezepte/foto.jpg",
            WebUrl.absolute("foto.jpg", "https://example.ch/rezepte/braten?a=1#oben"),
        )
    }

    @Test
    fun `liefert nichts bei leerer Angabe`() {
        assertNull(WebUrl.absolute(null, seite))
        assertNull(WebUrl.absolute("", seite))
        assertNull(WebUrl.absolute("   ", seite))
    }

    @Test
    fun `weist fremde Schemata ab, statt sie zu verbiegen`() {
        // Lieber kein Bild als eine Adresse, die der Bildlader nicht laden kann.
        assertNull(WebUrl.absolute("data:image/png;base64,AAAA", seite))
        assertNull(WebUrl.absolute("javascript:void(0)", seite))
    }
}
