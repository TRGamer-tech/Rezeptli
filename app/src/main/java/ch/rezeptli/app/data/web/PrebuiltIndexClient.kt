package ch.rezeptli.app.data.web

import ch.rezeptli.app.BuildConfig
import ch.rezeptli.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
                            val spalten = line.split('\t')
                            if (spalten.size < 2 || spalten[0].isBlank()) {
                                null
                            } else {
                                SitemapEntry(
                                    url = spalten[0],
                                    title = spalten[1],
                                    imageUrl = spalten.getOrNull(2)?.takeIf { it.isNotBlank() },
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

    /** Einmal geladen, gilt sie fuer den Rest des Prozesses - sie aendert sich nur einmal taeglich. */
    private var stapelCache: List<SampleEntry>? = null
    private val stapelMutex = Mutex()

    /**
     * Die Stapeldatei ist der einzige Weg, mit dem der Wischstapel nicht auf die
     * Verzeichnisse aller Quellen zurueckfaellt (siehe [entriesFor]s Dokumentation). Ohne
     * Zwischenspeicher hier hiesse "der Stapel legt nach" oder "die gemeinsame Runde
     * zieht einen eigenen Vorschlag": dieselben paar hundert Kilobyte noch einmal vom
     * Netz holen und neu einlesen - und das mehrmals in derselben Sitzung.
     */
    override suspend fun deckSample(): List<SampleEntry>? {
        stapelCache?.let { return it }

        return stapelMutex.withLock {
            stapelCache?.let { return@withLock it }

            val geladen = deckSampleVomNetz() ?: deckSampleVomNetz()
            geladen?.also { stapelCache = it }
        }
    }

    /**
     * Ein einzelner Versuch, die Stapeldatei zu laden.
     *
     * [deckSample] ruft das bei einem Fehlschlag ein zweites Mal auf: Ein einzelner
     * verlorener Abruf soll nicht gleich den langen Weg ueber alle Quellen ausloesen.
     */
    private suspend fun deckSampleVomNetz(): List<SampleEntry>? = withContext(ioDispatcher) {
        lade("stapel.tsv.gz") { spalten ->
            val quelle = spalten.getOrNull(3)?.takeIf { it.isNotBlank() } ?: return@lade null
            SampleEntry(
                url = spalten[0],
                title = spalten[1],
                imageUrl = spalten.getOrNull(2)?.takeIf { it.isNotBlank() },
                sourceId = quelle,
            )
        }
    }

    /** Holt eine Datei des Verzeichnisses und liest sie Zeile fuer Zeile. */
    private fun <T> lade(datei: String, zeileLesen: (List<String>) -> T?): List<T>? {
        val request = Request
            .Builder()
            .url("$BASE_URL/$datei")
            .header("Accept-Encoding", "identity")
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null

                GZIPInputStream(body.byteStream()).bufferedReader().useLines { lines ->
                    lines
                        .mapNotNull { line ->
                            val spalten = line.split('\t')
                            if (spalten.size < 2 || spalten[0].isBlank()) null else zeileLesen(spalten)
                        }.toList()
                        .takeIf { it.isNotEmpty() }
                }
            }
        } catch (exception: IOException) {
            null
        }
    }

    private companion object {
        val BASE_URL = BuildConfig.INDEX_URL.trimEnd('/')
    }
}
