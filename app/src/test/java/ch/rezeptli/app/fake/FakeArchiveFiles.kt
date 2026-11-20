package ch.rezeptli.app.fake

import ch.rezeptli.app.domain.archive.DocumentAccess
import ch.rezeptli.app.domain.archive.PhotoFiles
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Die Fotos der App - im Speicher statt auf dem Telefon. */
class FakePhotoFiles(
    initial: Map<String, ByteArray> = emptyMap(),
) : PhotoFiles {
    val files = initial.toMutableMap()
    private var next = 1

    override fun open(photoUri: String): InputStream? = files[photoUri]?.let(::ByteArrayInputStream)

    override fun save(input: InputStream, maxBytes: Long): String? {
        val bytes = input.readNBytes((maxBytes + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        if (bytes.isEmpty() || bytes.size > maxBytes) return null
        return "file:///fotos/neu-${next++}.jpg".also { files[it] = bytes }
    }

    override fun delete(photoUri: String) {
        files.remove(photoUri)
    }
}

/** Die Dateien aus dem Dateidialog - im Speicher. */
class FakeDocuments : DocumentAccess {
    val files = mutableMapOf<String, ByteArray>()
    val discarded = mutableListOf<String>()

    /** Adressen, die sich nicht oeffnen lassen - wie eine Datei auf einer entfernten Speicherkarte. */
    val locked = mutableSetOf<String>()

    /** Bricht das Schreiben nach so vielen Bytes ab - wie ein voller Speicher. */
    var failAfterBytes: Int? = null

    override fun openForWriting(uri: String): OutputStream? {
        if (uri in locked) return null
        val limit = failAfterBytes
        return object : ByteArrayOutputStream() {
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                if (limit != null && size() + length > limit) throw IOException("Speicher voll")
                super.write(buffer, offset, length)
            }

            override fun close() {
                files[uri] = toByteArray()
            }
        }
    }

    override fun openForReading(uri: String): InputStream? =
        files[uri]?.takeUnless { uri in locked }?.let(::ByteArrayInputStream)

    override fun discard(uri: String) {
        discarded += uri
        files.remove(uri)
    }
}
