package ch.rezeptli.app.data.local

import android.content.Context
import android.net.Uri
import ch.rezeptli.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
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
) {
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
        runCatching {
            val file = Uri.parse(photoUri).path?.let(::File) ?: return@runCatching
            if (file.parentFile?.name == PHOTO_DIRECTORY && file.exists()) {
                file.delete()
            }
        }
        Unit
    }

    private companion object {
        const val PHOTO_DIRECTORY = "recipe-photos"
    }
}
