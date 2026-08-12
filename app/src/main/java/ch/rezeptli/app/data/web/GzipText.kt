package ch.rezeptli.app.data.web

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Macht aus einer heruntergeladenen Antwort Text - auch wenn sie gepackt ankommt.
 *
 * Manche Quellen veroeffentlichen ihre Sitemap als `.xml.gz`. Der Server schickt so
 * eine Datei als Inhalt, nicht als gepackte Uebertragung: `Content-Encoding` fehlt,
 * also packt der HTTP-Client sie auch nicht aus. Ohne diesen Schritt landet
 * Binaermuell im Parser, und die Quelle liefert scheinbar einfach nichts.
 *
 * Erkannt wird am Inhalt, nicht am Dateinamen: Die Kennung 0x1f 0x8b steht am Anfang
 * jedes Gzip-Stroms. Das greift auch dort, wo die Adresse nicht auf `.gz` endet.
 */
object GzipText {
    private const val MAGIC_FIRST = 0x1f.toByte()
    private const val MAGIC_SECOND = 0x8b.toByte()

    fun decode(bytes: ByteArray): String =
        if (isGzip(bytes)) {
            runCatching {
                GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().decodeToString() }
            }.getOrElse { bytes.decodeToString() }
        } else {
            bytes.decodeToString()
        }

    fun isGzip(bytes: ByteArray): Boolean =
        bytes.size >= 2 && bytes[0] == MAGIC_FIRST && bytes[1] == MAGIC_SECOND
}
