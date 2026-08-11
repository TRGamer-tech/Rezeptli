package ch.rezeptli.app.data.web

import ch.rezeptli.app.BuildConfig
import ch.rezeptli.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Laedt das fertig gebaute Verzeichnis einer Quelle.
 *
 * Die Datei ist gepackt und enthaelt eine Zeile je Rezept: Adresse, Tabulator, Titel.
 * Ein Format ohne Schnoerkel, weil es zehntausende Zeilen sind und jedes Byte auf dem
 * Mobilfunk bezahlt wird.
 */
@Singleton
class PrebuiltIndexClient @Inject constructor(
    private val client: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PrebuiltIndex {
    override suspend fun entriesFor(sourceId: String): List<SitemapEntry>? = withContext(ioDispatcher) {
        val request = Request
            .Builder()
            .url("$BASE_URL/$sourceId.tsv.gz")
            .header("Accept-Encoding", "identity")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null

                GZIPInputStream(body.byteStream()).bufferedReader().useLines { lines ->
                    lines
                        .mapNotNull { line ->
                            val separator = line.indexOf('\t')
                            if (separator <= 0) {
                                null
                            } else {
                                SitemapEntry(
                                    url = line.substring(0, separator),
                                    title = line.substring(separator + 1),
                                )
                            }
                        }.toList()
                        .takeIf { it.isNotEmpty() }
                }
            }
        } catch (exception: IOException) {
            // Kein Netz, kein Index, oder die Datei ist beschaedigt - alles drei
            // bedeutet dasselbe: den anderen Weg nehmen.
            null
        }
    }

    private companion object {
        val BASE_URL = BuildConfig.INDEX_URL.trimEnd('/')
    }
}
