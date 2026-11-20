package ch.rezeptli.app.data.local

import android.content.Context
import android.net.Uri
import ch.rezeptli.app.di.IoDispatcher
import ch.rezeptli.app.domain.archive.PhotoFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legt Rezeptfotos im privaten Speicher der App ab.
 *
 * Das ausgewaehlte Bild wird bewusst kopiert statt nur referenziert: Eine URI aus dem
 * Fotopicker gilt nur fuer die laufende Sitzung, und ein geloeschtes Original wuerde das
 * Rezept sonst ohne Bild zuruecklassen. Die Kopie liegt ausserdem im App-Sandbox-Speicher
 * und verlaesst das Geraet nicht.
 */
@Singleton
class PhotoStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PhotoFiles {
    private val photoDirectory: File
        get() = File(context.filesDir, PHOTO_DIRECTORY).apply { mkdirs() }

    /**
     * Kopiert das Bild hinter [source] in den App-Speicher und liefert die URI der Kopie,
     * oder `null`, wenn das Bild nicht gelesen werden konnte.
     */
    suspend fun savePhoto(source: Uri): String? = withContext(ioDispatcher) {
        val target = File(photoDirectory, "${UUID.randomUUID()}.jpg")
        runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            Uri.fromFile(target).toString()
        }.getOrElse {
            target.delete()
            null
        }
    }

    /** Loescht eine zuvor gespeicherte Kopie. Fremde URIs werden ignoriert. */
    suspend fun deletePhoto(photoUri: String?) = withContext(ioDispatcher) {
        if (photoUri.isNullOrBlank()) return@withContext
        delete(photoUri)
    }

    /** Die Datei hinter [photoUri] - aber nur, wenn sie wirklich im Fotoordner der App liegt. */
    private fun ownFile(photoUri: String): File? =
        runCatching { Uri.parse(photoUri).path?.let(::File) }
            .getOrNull()
            ?.takeIf { it.parentFile?.name == PHOTO_DIRECTORY && it.exists() }

    // Fuer den Export und Import der Sammlung. Diese drei rechnen nicht selbst mit einem
    // Hintergrund-Thread - wer sie ruft, liest oder schreibt ohnehin gerade eine Datei.

    override fun open(photoUri: String): InputStream? = runCatching { ownFile(photoUri)?.inputStream() }.getOrNull()

    override fun save(input: InputStream, maxBytes: Long): String? {
        val target = File(photoDirectory, "${UUID.randomUUID()}.jpg")
        return try {
            var written = 0L
            target.outputStream().use { output ->
                val buffer = ByteArray(COPY_BUFFER)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    written += read
                    // Zu gross: abbrechen, bevor der Speicher des Telefons volllaeuft.
                    if (written > maxBytes) break
                    output.write(buffer, 0, read)
                }
            }
            if (written == 0L || written > maxBytes) {
                target.delete()
                null
            } else {
                Uri.fromFile(target).toString()
            }
        } catch (_: IOException) {
            target.delete()
            null
        }
    }

    override fun delete(photoUri: String) {
        runCatching { ownFile(photoUri)?.delete() }
    }

    private companion object {
        const val PHOTO_DIRECTORY = "recipe-photos"
        const val COPY_BUFFER = 16 * 1024
    }
}
