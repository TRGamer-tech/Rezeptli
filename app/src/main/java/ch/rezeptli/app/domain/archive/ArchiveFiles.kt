package ch.rezeptli.app.domain.archive

import java.io.InputStream
import java.io.OutputStream

/** Die Fotos der Rezepte - ohne dass der Rest wissen muss, wo auf dem Telefon sie liegen. */
interface PhotoFiles {
    /** Oeffnet ein Foto, das die App selbst abgelegt hat. `null`, wenn es keines ist oder die Datei fehlt. */
    fun open(photoUri: String): InputStream?

    /** Legt ein Foto ab und liefert, wo es jetzt liegt - `null`, wenn es groesser als [maxBytes] ist. */
    fun save(input: InputStream, maxBytes: Long): String?

    /** Raeumt ein Foto weg, das doch nicht gebraucht wird. */
    fun delete(photoUri: String)
}

/** Dateien, die die Person ueber den Dateidialog ihres Telefons gewaehlt hat. */
interface DocumentAccess {
    fun openForWriting(uri: String): OutputStream?

    fun openForReading(uri: String): InputStream?

    /** Raeumt eine angefangene Datei wieder weg, wenn das Schreiben gescheitert ist. */
    fun discard(uri: String)
}
