package ch.rezeptli.app.data.web

/**
 * Eine Rezeptquelle im Web.
 *
 * [sitemapUrls] sind die Verzeichnisse, die die Seiten selbst fuer Suchmaschinen
 * veroeffentlichen. Die Suche in Rezeptli arbeitet ausschliesslich damit - nicht mit
 * den Suchseiten der Anbieter. Das ist der Weg, den die Betreiber fuer maschinelle
 * Zugriffe vorgesehen haben, und bei Fooby untersagt die robots.txt das Abfragen der
 * eigenen Suche sogar ausdruecklich.
 *
 * [minRequestIntervalMs] setzt das Crawl-delay der jeweiligen robots.txt um.
 */
data class RecipeSource(
    val id: String,
    val name: String,
    val homeUrl: String,
    val urlPattern: Regex,
    val sitemapUrls: List<String> = emptyList(),
    val minRequestIntervalMs: Long = 0L,
    val note: String? = null,
) {
    /** Ohne Verzeichnis laesst sich nur ein konkreter Link importieren, aber nicht suchen. */
    val supportsSearch: Boolean get() = sitemapUrls.isNotEmpty()

    fun matches(url: String): Boolean = urlPattern.containsMatchIn(url)
}

/**
 * Die unterstuetzten Quellen.
 *
 * Die Angaben stammen aus einer Analyse der Seiten (robots.txt, Sitemaps und
 * strukturierte Daten), nicht aus Vermutungen - siehe docs/adr/0005-web-import.md.
 */
object RecipeSourceCatalog {
    val BETTY_BOSSI = RecipeSource(
        id = "bettybossi",
        name = "Betty Bossi",
        homeUrl = "https://www.bettybossi.ch",
        urlPattern = Regex("bettybossi\\.ch/de/rezepte/rezept/", RegexOption.IGNORE_CASE),
        sitemapUrls = listOf("https://www.bettybossi.ch/sitemap.xml"),
    )

    val SWISSMILK = RecipeSource(
        id = "swissmilk",
        name = "Swissmilk",
        homeUrl = "https://www.swissmilk.ch",
        urlPattern = Regex("swissmilk\\.ch/de/rezepte-kochideen/rezepte/", RegexOption.IGNORE_CASE),
        sitemapUrls = listOf("https://www.swissmilk.ch/de/sitemap.xml"),
    )

    val GUTEKUECHE = RecipeSource(
        id = "gutekueche",
        name = "Gutekueche",
        homeUrl = "https://www.gutekueche.ch",
        urlPattern = Regex("gutekueche\\.ch/[^/]+-rezept-\\d+", RegexOption.IGNORE_CASE),
        sitemapUrls = listOf("https://www.gutekueche.ch/sitemap.xml.gz"),
    )

    val MIGUSTO = RecipeSource(
        id = "migusto",
        name = "Migusto",
        homeUrl = "https://migusto.migros.ch",
        urlPattern = Regex("migusto\\.migros\\.ch/de/rezepte/", RegexOption.IGNORE_CASE),
        sitemapUrls = listOf("https://migusto.migros.ch/.rest/sitemap/migusto/de.xml"),
    )

    val BETTYS_KUECHENSCHAETZE = RecipeSource(
        id = "bettyskuechenschaetze",
        name = "Bettys Küchenschätze",
        homeUrl = "https://bettyskuechenschaetze.ch",
        urlPattern = Regex("bettyskuechenschaetze\\.ch/[^/]+/?$", RegexOption.IGNORE_CASE),
        sitemapUrls = listOf("https://bettyskuechenschaetze.ch/sitemap_index.xml"),
    )

    val LE_MENU = RecipeSource(
        id = "lemenu",
        name = "Le Menu",
        homeUrl = "https://lemenu.ch",
        urlPattern = Regex("lemenu\\.ch/de/", RegexOption.IGNORE_CASE),
        sitemapUrls = listOf("https://lemenu.ch/post-sitemap.xml"),
    )

    /**
     * Fooby untersagt in der robots.txt das Abfragen der eigenen Suche und verlangt
     * 10 Sekunden Abstand zwischen Anfragen. Ein Verzeichnis mit Rezeptlinks liess sich
     * nicht finden, deshalb hier nur Import ueber einen konkreten Link.
     */
    val FOOBY = RecipeSource(
        id = "fooby",
        name = "Fooby",
        homeUrl = "https://fooby.ch",
        urlPattern = Regex("fooby\\.ch/de/rezepte/", RegexOption.IGNORE_CASE),
        minRequestIntervalMs = 10_000L,
        note = "nur ueber einen Link",
    )

    /**
     * Chefkochs robots.txt erlaubt Rezeptseiten ausdruecklich; ausgenommen sind unter
     * anderem Kommentare, Bewertungen und Adressen mit Abfrageparametern. Letztere
     * entfernt der Client ohnehin, bevor er laedt.
     *
     * Aus Rechenzentren beantwortet Chefkoch Anfragen teilweise mit 403. Vom Handy aus
     * klappt der Import in der Regel; wenn nicht, meldet die App das sauber.
     */
    val CHEFKOCH = RecipeSource(
        id = "chefkoch",
        name = "Chefkoch",
        homeUrl = "https://www.chefkoch.de",
        urlPattern = Regex("chefkoch\\.de/rezepte/\\d+", RegexOption.IGNORE_CASE),
        note = "nur ueber einen Link",
    )

    val ALL: List<RecipeSource> = listOf(
        BETTY_BOSSI,
        SWISSMILK,
        GUTEKUECHE,
        MIGUSTO,
        BETTYS_KUECHENSCHAETZE,
        LE_MENU,
        FOOBY,
        CHEFKOCH,
    )

    val SEARCHABLE: List<RecipeSource> = ALL.filter { it.supportsSearch }

    fun byId(id: String): RecipeSource? = ALL.firstOrNull { it.id == id }

    /** Findet die Quelle zu einem Link - fuer den Import ueber "Teilen". */
    fun forUrl(url: String): RecipeSource? = ALL.firstOrNull { it.matches(url) }
}
