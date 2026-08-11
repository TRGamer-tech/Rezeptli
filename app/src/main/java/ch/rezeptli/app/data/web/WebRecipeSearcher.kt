package ch.rezeptli.app.data.web

import ch.rezeptli.app.domain.model.IngredientNameNormalizer
import ch.rezeptli.app.domain.model.WebSearchResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sucht Rezepte in den Verzeichnissen der Quellen.
 *
 * Gesucht wird im sprechenden Teil der Adresse - der enthaelt bei allen unterstuetzten
 * Quellen den Rezepttitel. Das ist keine Volltextsuche ueber die Zutaten, findet aber
 * zuverlaessig, was man ueblicherweise sucht ("Aprikosen", "Risotto"), ohne fuer jede
 * Anfrage hunderte Seiten der Anbieter zu laden.
 */
@Singleton
class WebRecipeSearcher @Inject constructor(
    private val fetcher: PageFetcher,
    private val sitemapParser: SitemapParser,
    private val store: WebIndexStore,
    private val prebuiltIndex: PrebuiltIndex,
) {
    /**
     * Sucht in [source] nach [query].
     *
     * Beim ersten Mal wird das Verzeichnis der Quelle geladen und zwischengespeichert;
     * danach laeuft die Suche ohne Netzzugriff.
     */
    suspend fun search(source: RecipeSource, query: String, limit: Int = DEFAULT_LIMIT): List<WebSearchResult> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()

        val entries = index(source)

        return entries
            .mapNotNull { entry ->
                val haystack = IngredientNameNormalizer.normalize(entry.title)
                val score = score(haystack, tokens) ?: return@mapNotNull null
                score to entry
            }.sortedWith(compareByDescending<Pair<Int, SitemapEntry>> { it.first }.thenBy { it.second.title.length })
            .take(limit)
            .map { (_, entry) ->
                WebSearchResult(
                    title = entry.title,
                    url = entry.url,
                    sourceId = source.id,
                    sourceName = source.name,
                    country = source.country,
                )
            }
    }

    /**
     * Laedt das Verzeichnis einer Quelle. Drei Wege, in dieser Reihenfolge:
     *
     * 1. Der eigene Zwischenspeicher - kein Netzzugriff.
     * 2. Das taeglich gebaute Verzeichnis - ein einziger Abruf.
     * 3. Die Sitemaps der Quelle - bis zu hundert Abrufe, deshalb zuletzt.
     */
    suspend fun index(source: RecipeSource): List<SitemapEntry> {
        store.read(source.id)?.let { return it }

        prebuiltIndex.entriesFor(source.id)?.let { prebuilt ->
            store.write(source.id, prebuilt)
            return prebuilt
        }

        val entries = mutableListOf<SitemapEntry>()
        source.sitemapUrls.forEach { sitemapUrl ->
            entries += loadSitemap(sitemapUrl, source, depth = 0)
        }

        val distinct = entries.distinctBy { it.url }
        if (distinct.isNotEmpty()) store.write(source.id, distinct)
        return distinct
    }

    private suspend fun loadSitemap(url: String, source: RecipeSource, depth: Int): List<SitemapEntry> {
        if (depth > MAX_INDEX_DEPTH) return emptyList()

        val xml = runCatching { fetcher.fetch(url, source) }.getOrElse { return emptyList() }

        if (!sitemapParser.isIndex(xml)) {
            return sitemapParser.recipeEntries(xml, source)
        }

        // Ein Sitemap-Index verweist auf weitere Sitemaps. Es werden nur so viele
        // Unterverzeichnisse geladen, wie noetig - nicht das halbe Archiv der Seite.
        return sitemapParser
            .locations(xml)
            .take(MAX_CHILD_SITEMAPS)
            .flatMap { child -> loadSitemap(child, source, depth + 1) }
    }

    /**
     * Bewertet einen Treffer: vollstaendige Wortuebereinstimmung zaehlt mehr als ein
     * Wortanfang. Fehlt ein Suchwort ganz, ist es kein Treffer.
     */
    private fun score(haystack: String, tokens: List<String>): Int? {
        var total = 0
        val words = haystack.split(' ').filter { it.isNotBlank() }

        tokens.forEach { token ->
            val exact = words.any { it == token }
            val prefix = words.any { it.startsWith(token) }
            val contained = haystack.contains(token)
            total += when {
                exact -> EXACT_WORD_SCORE
                prefix -> PREFIX_SCORE
                contained -> CONTAINED_SCORE
                else -> return null
            }
        }
        return total
    }

    private fun tokenize(query: String): List<String> =
        IngredientNameNormalizer
            .normalize(query)
            .split(' ')
            .filter { it.length >= MIN_TOKEN_LENGTH }

    private companion object {
        const val DEFAULT_LIMIT = 40
        const val MAX_INDEX_DEPTH = 2
        const val MAX_CHILD_SITEMAPS = 12
        const val MIN_TOKEN_LENGTH = 2
        const val EXACT_WORD_SCORE = 10
        const val PREFIX_SCORE = 5
        const val CONTAINED_SCORE = 2
    }
}
