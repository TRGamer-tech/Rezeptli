package ch.rezeptli.app.data.web

import android.content.Context
import ch.rezeptli.app.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legt das Rezeptverzeichnis einer Quelle im Cache-Verzeichnis der App ab.
 *
 * Ein Verzeichnis umfasst je nach Quelle mehrere zehntausend Eintraege. Es einmal pro
 * Woche zu laden statt bei jeder Suche schont Datenvolumen und die Server der Anbieter.
 * Der Ablageort ist bewusst der Cache: Android darf ihn jederzeit leeren, und dann wird
 * er einfach neu geholt.
 */
@Singleton
class WebIndexCache @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : WebIndexStore {
    private val directory: File
        get() = File(context.cacheDir, DIRECTORY).apply { mkdirs() }

    private fun fileFor(sourceId: String) = File(directory, "$sourceId.tsv")

    /** Gibt das gespeicherte Verzeichnis zurueck oder `null`, wenn es fehlt oder alt ist. */
    override suspend fun read(sourceId: String): List<SitemapEntry>? =
        withContext(ioDispatcher) {
            val file = fileFor(sourceId)
            if (!file.exists()) return@withContext null
            if (System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS) return@withContext null

            runCatching {
                file.readLines().mapNotNull { line ->
                    val parts = line.split('\t')
                    // Zwei Spalten sind das alte Format ohne Bild - es bleibt lesbar.
                    if (parts.size >= 2) {
                        SitemapEntry(
                            url = parts[0],
                            title = parts[1],
                            imageUrl = parts.getOrNull(2)?.takeIf { it.isNotBlank() },
                        )
                    } else {
                        null
                    }
                }
            }.getOrNull()
        }

    override suspend fun write(sourceId: String, entries: List<SitemapEntry>) = withContext(ioDispatcher) {
        runCatching {
            fileFor(sourceId).writeText(
                entries.joinToString("\n") { "${it.url}\t${it.title}\t${it.imageUrl.orEmpty()}" },
            )
        }
        Unit
    }

    suspend fun clear() = withContext(ioDispatcher) {
        runCatching { directory.deleteRecursively() }
        Unit
    }

    private companion object {
        const val DIRECTORY = "web-index"
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
