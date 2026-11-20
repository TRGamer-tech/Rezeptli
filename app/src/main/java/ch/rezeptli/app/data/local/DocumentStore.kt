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
    // "wt": von vorn schreiben und kuerzen - sonst bliebe vom alten Inhalt einer ersetzten Datei ein Rest stehen.
    override fun openForWriting(uri: String): OutputStream? =
        runCatching { context.contentResolver.openOutputStream(Uri.parse(uri), "wt") }.getOrNull()

    override fun openForReading(uri: String): InputStream? =
        runCatching { context.contentResolver.openInputStream(Uri.parse(uri)) }.getOrNull()

    override fun discard(uri: String) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uri)) }
    }
}
