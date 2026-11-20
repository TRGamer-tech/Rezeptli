package ch.rezeptli.app.data.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import ch.rezeptli.app.domain.archive.DocumentAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Oeffnet Dateien, die die Person im Dateidialog des Telefons gewaehlt hat.
 *
 * Die App braucht dafuer keine Speicher-Berechtigung: Android gibt ihr genau die eine Datei
 * frei, die gewaehlt wurde - zum Exportieren die neu angelegte, zum Importieren die geoeffnete.
 */
@Singleton
class DocumentStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : DocumentAccess {
    /**
     * Zuerst "wt" - von vorn schreiben und kuerzen: Sonst bliebe vom alten Inhalt einer ersetzten
     * Datei ein Rest stehen. Manche Ablagen (etwa in der Cloud) kennen nur das schlichte "w";
     * fuer die frisch angelegte, leere Datei kommt das aufs selbe hinaus.
     */
    override fun openForWriting(uri: String): OutputStream? {
        val target = Uri.parse(uri)
        return WRITE_MODES.firstNotNullOfOrNull { mode ->
            runCatching { context.contentResolver.openOutputStream(target, mode) }.getOrNull()
        }
    }

    override fun openForReading(uri: String): InputStream? =
        runCatching { context.contentResolver.openInputStream(Uri.parse(uri)) }.getOrNull()

    override fun discard(uri: String) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uri)) }
    }

    private companion object {
        val WRITE_MODES = listOf("wt", "w")
    }
}
