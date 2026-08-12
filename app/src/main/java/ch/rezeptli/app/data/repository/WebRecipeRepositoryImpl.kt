package ch.rezeptli.app.data.repository

import ch.rezeptli.app.data.web.PageFetcher
import ch.rezeptli.app.data.web.PrebuiltIndex
import ch.rezeptli.app.data.web.RecipeSourceCatalog
import ch.rezeptli.app.data.web.StructuredRecipeExtractor
import ch.rezeptli.app.data.web.WebFetchError
import ch.rezeptli.app.data.web.WebFetchException
import ch.rezeptli.app.data.web.WebRecipeSearcher
import ch.rezeptli.app.di.IoDispatcher
import ch.rezeptli.app.domain.deck.DeckEntry
import ch.rezeptli.app.domain.model.WebSearchResult
import ch.rezeptli.app.domain.repository.DeckPool
import ch.rezeptli.app.domain.repository.WebImportError
import ch.rezeptli.app.domain.repository.WebRecipeRepository
import ch.rezeptli.app.domain.repository.WebRecipeResult
import ch.rezeptli.app.domain.repository.WebSearchUpdate
import ch.rezeptli.app.domain.translate.SourceLanguage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRecipeRepositoryImpl @Inject constructor(
    private val searcher: WebRecipeSearcher,
    private val prebuiltIndex: PrebuiltIndex,
    private val fetcher: PageFetcher,
    private val extractor: StructuredRecipeExtractor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : WebRecipeRepository {
    /**
     * Fragt alle gewaehlten Quellen gleichzeitig und meldet jeden Zwischenstand.
     *
     * Vorher lief das nacheinander: Drei Quellen bedeuteten drei Wartezeiten
     * hintereinander, und angezeigt wurde erst, wenn die letzte fertig war.
     */
    override fun search(query: String, sourceIds: Set<String>): Flow<WebSearchUpdate> = channelFlow {
        val sources = RecipeSourceCatalog.SEARCHABLE.filter { it.id in sourceIds }
        if (sources.isEmpty()) {
            send(WebSearchUpdate())
            return@channelFlow
        }

        send(WebSearchUpdate(totalSources = sources.size))

        val collected = mutableListOf<WebSearchResult>()
        val mutex = Mutex()
        var finished = 0

        coroutineScope {
            sources.forEach { source ->
                launch(ioDispatcher) {
                    // Eine nicht erreichbare Quelle darf die Suche in den anderen nicht kippen.
                    val hits = runCatching { searcher.search(source, query) }.getOrDefault(emptyList())

                    mutex.withLock {
                        collected += hits
                        finished += 1
                        send(
                            WebSearchUpdate(
                                results = collected.sortedBy { it.title.length },
                                finishedSources = finished,
                                totalSources = sources.size,
                            ),
                        )
                    }
                }
            }
        }
    }

    override suspend fun warmUp(sourceIds: Set<String>) {
        coroutineScope {
            RecipeSourceCatalog.SEARCHABLE
                .filter { it.id in sourceIds }
                .forEach { source ->
                    launch(ioDispatcher) {
                        // Fehler sind hier bedeutungslos: Klappt es nicht, laedt die
                        // Suche das Verzeichnis spaeter eben doch selbst.
                        runCatching { searcher.index(source) }
                    }
                }
        }
    }

    /**
     * Holt die Verzeichnisse aller durchsuchbaren Quellen fuer den Wischstapel.
     *
     * Die Quellen werden gleichzeitig gefragt, und eine ausgefallene Quelle laesst die
     * anderen stehen: Ein Stapel aus zehn Quellen statt elf faellt niemandem auf, ein
     * leerer Bildschirm schon.
     */
    override suspend fun deckPool(): DeckPool = coroutineScope {
        val sources = RecipeSourceCatalog.SEARCHABLE
        val herkunft = sources.associate { it.id to it.origin }

        // Erst die kleine Stapeldatei: ein Abruf von ein paar hundert Kilobyte statt
        // der Verzeichnisse aller Quellen. Vorher lud der Stapel ueber 300'000
        // Adressen, bevor die erste Karte erschien - das dauerte am Handy Minuten.
        prebuiltIndex.deckSample()?.let { auswahl ->
            val namen = sources.associate { it.id to it.name }
            val gruppiert = auswahl
                .filter { it.sourceId in namen }
                .groupBy { it.sourceId }
                .mapValues { (quelle, eintraege) ->
                    eintraege.map { eintrag ->
                        DeckEntry(
                            url = eintrag.url,
                            title = eintrag.title,
                            imageUrl = eintrag.imageUrl,
                            sourceId = quelle,
                            sourceName = namen.getValue(quelle),
                        )
                    }
                }

            if (gruppiert.isNotEmpty()) {
                return@coroutineScope DeckPool(entriesBySource = gruppiert, origins = herkunft)
            }
        }

        val mutex = Mutex()
        val eintraege = mutableMapOf<String, List<DeckEntry>>()

        val auftraege = sources.map { source ->
            launch(ioDispatcher) {
                val gefunden = runCatching { searcher.index(source) }
                    .getOrDefault(emptyList())
                if (gefunden.isEmpty()) return@launch

                val umgewandelt = gefunden.map { eintrag ->
                    DeckEntry(
                        url = eintrag.url,
                        title = eintrag.title,
                        imageUrl = eintrag.imageUrl,
                        sourceId = source.id,
                        sourceName = source.name,
                    )
                }
                mutex.withLock { eintraege[source.id] = umgewandelt }
            }
        }

        auftraege.joinAll()

        DeckPool(entriesBySource = eintraege.toMap(), origins = herkunft)
    }

    override suspend fun cardImage(url: String): String? = withContext(ioDispatcher) {
        val source = RecipeSourceCatalog.forUrl(url)
        val html = runCatching { fetcher.fetch(url, source) }.getOrNull() ?: return@withContext null
        extractor.imageOf(html, url)
    }

    override suspend fun loadRecipe(url: String): WebRecipeResult {
        val source = RecipeSourceCatalog.forUrl(url)
        val sourceName = source?.name ?: url.substringAfter("//").substringBefore('/')

        val html = try {
            fetcher.fetch(url, source)
        } catch (exception: WebFetchException) {
            return WebRecipeResult.Failed(exception.error.toDomain())
        }

        val recipe = extractor.extract(html, url, sourceName)
            ?: return WebRecipeResult.Failed(WebImportError.NO_RECIPE_FOUND)

        // Die Sprache steht am Land der Quelle, nicht im Text: Eine Erkennung liegt
        // bei kurzen Zutatenzeilen oft daneben.
        return WebRecipeResult.Loaded(
            recipe.copy(sourceLanguage = SourceLanguage.forCountry(source?.country)),
        )
    }

    private fun WebFetchError.toDomain(): WebImportError = when (this) {
        WebFetchError.NoConnection -> WebImportError.NO_CONNECTION
        WebFetchError.Rejected -> WebImportError.REJECTED
        WebFetchError.Disallowed -> WebImportError.DISALLOWED
        WebFetchError.NotFound -> WebImportError.NOT_FOUND
        is WebFetchError.Unexpected -> WebImportError.UNKNOWN
    }
}
