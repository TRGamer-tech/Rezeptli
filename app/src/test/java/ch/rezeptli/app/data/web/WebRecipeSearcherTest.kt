package ch.rezeptli.app.data.web

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val SITEMAP = """<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url><loc>https://www.swissmilk.ch/de/rezepte-kochideen/rezepte/SM01/aprikosen-blechkuchen/</loc></url>
  <url><loc>https://www.swissmilk.ch/de/rezepte-kochideen/rezepte/SM02/aprikosen-galette/</loc></url>
  <url><loc>https://www.swissmilk.ch/de/rezepte-kochideen/rezepte/SM03/kuerbis-risotto/</loc></url>
  <url><loc>https://www.swissmilk.ch/de/rezepte-kochideen/saisonkalender/kohl/</loc></url>
  <url><loc>https://www.swissmilk.ch/de/ueber-uns/</loc></url>
</urlset>"""

private const val SITEMAP_INDEX = """<?xml version="1.0" encoding="UTF-8"?>
<sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <sitemap><loc>https://www.swissmilk.ch/de/sitemap-1.xml</loc></sitemap>
</sitemapindex>"""

private class FakeFetcher(private val responses: Map<String, String>) : PageFetcher {
    var calls: Int = 0
        private set

    override suspend fun fetch(url: String, source: RecipeSource?): String {
        calls++
        return responses[url] ?: error("Unerwarteter Abruf: $url")
    }
}

/**
 * Ein Verzeichnisdienst, der standardmaessig nichts liefert.
 *
 * Damit laufen die bestehenden Tests wie bisher ueber die Sitemaps - und ein Test
 * kann gezielt Eintraege setzen, um den schnellen Weg zu pruefen.
 */
private class FakePrebuiltIndex(var entries: List<SitemapEntry>? = null) : PrebuiltIndex {
    var abgefragt = 0
        private set

    override suspend fun entriesFor(sourceId: String): List<SitemapEntry>? {
        abgefragt += 1
        return entries
    }
}

private class FakeStore : WebIndexStore {
    private val entries = mutableMapOf<String, List<SitemapEntry>>()
    var writes: Int = 0
        private set

    override suspend fun read(sourceId: String): List<SitemapEntry>? = entries[sourceId]

    override suspend fun write(sourceId: String, entries: List<SitemapEntry>) {
        writes++
        this.entries[sourceId] = entries
    }
}

class WebRecipeSearcherTest {
    private val source = RecipeSourceCatalog.SWISSMILK
    private val sitemapUrl = source.sitemapUrls.single()

    private fun searcher(
        fetcher: PageFetcher,
        store: WebIndexStore = FakeStore(),
        prebuilt: PrebuiltIndex = FakePrebuiltIndex(),
    ) = WebRecipeSearcher(fetcher, SitemapParser(), store, prebuilt)

    @Test
    fun `findet Rezepte ueber den sprechenden Teil der Adresse`() = runTest {
        val fetcher = FakeFetcher(mapOf(sitemapUrl to SITEMAP))

        val results = searcher(fetcher).search(source, "Aprikosen")

        assertEquals(2, results.size)
        assertEquals(listOf("Aprikosen Blechkuchen", "Aprikosen Galette"), results.map { it.title }.sorted())
        assertTrue(results.all { it.sourceId == "swissmilk" })
    }

    @Test
    fun `nimmt nur Adressen die zum Rezeptmuster der Quelle passen`() = runTest {
        val fetcher = FakeFetcher(mapOf(sitemapUrl to SITEMAP))

        val entries = searcher(fetcher).index(source)

        assertEquals(3, entries.size, "Saisonkalender und Über-uns sind keine Rezepte")
        assertTrue(entries.none { it.url.contains("saisonkalender") })
    }

    @Test
    fun `verlangt dass alle Suchwoerter vorkommen`() = runTest {
        val fetcher = FakeFetcher(mapOf(sitemapUrl to SITEMAP))

        val results = searcher(fetcher).search(source, "Aprikosen Risotto")

        assertTrue(results.isEmpty(), "Kein Rezept enthaelt beide Woerter")
    }

    @Test
    fun `laedt das Verzeichnis nur einmal und nutzt danach den Zwischenspeicher`() = runTest {
        val fetcher = FakeFetcher(mapOf(sitemapUrl to SITEMAP))
        val store = FakeStore()
        val searcher = searcher(fetcher, store)

        searcher.search(source, "Aprikosen")
        searcher.search(source, "Risotto")

        assertEquals(1, fetcher.calls, "Beim zweiten Suchen darf nichts nachgeladen werden")
        assertEquals(1, store.writes)
    }

    @Test
    fun `folgt einem Sitemap-Index zu den Unterverzeichnissen`() = runTest {
        val fetcher = FakeFetcher(
            mapOf(
                sitemapUrl to SITEMAP_INDEX,
                "https://www.swissmilk.ch/de/sitemap-1.xml" to SITEMAP,
            ),
        )

        val results = searcher(fetcher).search(source, "Kürbis")

        assertEquals(listOf("Kuerbis Risotto"), results.map { it.title })
    }

    @Test
    fun `findet auch mit Umlauten und abweichender Schreibweise`() = runTest {
        val fetcher = FakeFetcher(mapOf(sitemapUrl to SITEMAP))

        assertEquals(1, searcher(fetcher).search(source, "kuerbis").size)
    }

    @Test
    fun `liefert bei leerer Suche nichts`() = runTest {
        val fetcher = FakeFetcher(mapOf(sitemapUrl to SITEMAP))

        assertTrue(searcher(fetcher).search(source, "  ").isEmpty())
        assertEquals(0, fetcher.calls, "Ohne Suchbegriff wird gar nichts geladen")
    }

    @Test
    fun `bleibt still wenn eine Quelle nicht erreichbar ist`() = runTest {
        val fetcher = object : PageFetcher {
            override suspend fun fetch(url: String, source: RecipeSource?): String =
                throw WebFetchException(WebFetchError.NoConnection, "offline")
        }

        assertTrue(searcher(fetcher).search(source, "Aprikosen").isEmpty())
    }

    @Test
    fun `nutzt das fertige Verzeichnis und laesst die Sitemaps in Ruhe`() = runTest {
        val fetcher = FakeFetcher(mapOf(sitemapUrl to SITEMAP))
        val prebuilt = FakePrebuiltIndex(
            listOf(
                SitemapEntry("https://www.swissmilk.ch/de/rezepte-kochideen/rezepte/x/", "Kuerbis Risotto"),
            ),
        )

        val results = searcher(fetcher, prebuilt = prebuilt).search(source, "Risotto")

        assertEquals(listOf("Kuerbis Risotto"), results.map { it.title })
        assertEquals(0, fetcher.calls, "Mit fertigem Verzeichnis wird keine Sitemap geladen")
    }

    @Test
    fun `faellt auf die Sitemaps zurueck, wenn das fertige Verzeichnis fehlt`() = runTest {
        val fetcher = FakeFetcher(mapOf(sitemapUrl to SITEMAP))
        val prebuilt = FakePrebuiltIndex(entries = null)

        val results = searcher(fetcher, prebuilt = prebuilt).search(source, "Aprikosen")

        assertEquals(1, prebuilt.abgefragt, "Der schnelle Weg wird zuerst versucht")
        assertEquals(1, fetcher.calls, "Danach die Sitemap")
        assertTrue(results.isNotEmpty())
    }
}
