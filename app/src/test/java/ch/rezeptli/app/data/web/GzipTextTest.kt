package ch.rezeptli.app.data.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class GzipTextTest {
    private fun gepackt(text: String): ByteArray {
        val ziel = ByteArrayOutputStream()
        GZIPOutputStream(ziel).use { it.write(text.encodeToByteArray()) }
        return ziel.toByteArray()
    }

    @Test
    fun `packt eine gepackte Sitemap aus`() {
        val xml = """<urlset><url><loc>https://www.gutekueche.ch/rezept-1</loc></url></urlset>"""

        assertEquals(xml, GzipText.decode(gepackt(xml)))
    }

    @Test
    fun `laesst ungepackten Text unveraendert`() {
        val xml = "<urlset><url><loc>https://example.ch/a</loc></url></urlset>"

        assertEquals(xml, GzipText.decode(xml.encodeToByteArray()))
    }

    @Test
    fun `erkennt Gzip an der Kennung und nicht am Dateinamen`() {
        assertTrue(GzipText.isGzip(gepackt("egal")))
        assertFalse(GzipText.isGzip("<urlset/>".encodeToByteArray()))
        assertFalse(GzipText.isGzip(ByteArray(1) { 0x1f }))
        assertFalse(GzipText.isGzip(ByteArray(0)))
    }

    @Test
    fun `gibt bei kaputtem Gzip die Rohbytes zurueck statt zu werfen`() {
        // Richtige Kennung, danach Unsinn - so etwas darf die Suche nicht abbrechen.
        val kaputt = byteArrayOf(0x1f, 0x8b.toByte(), 0x08, 0x00, 0x42, 0x42)

        // Kein Wurf, irgendein Ergebnis genuegt.
        GzipText.decode(kaputt)
    }

    @Test
    fun `umlaute ueberleben das Auspacken`() {
        val xml = "<loc>https://www.gutekueche.ch/kaesespaetzli-rezept-6</loc><titel>Käsespätzli</titel>"

        assertEquals(xml, GzipText.decode(gepackt(xml)))
    }
}
