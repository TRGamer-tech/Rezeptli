package ch.rezeptli.app.data.web

import javax.inject.Inject

/** Ein Eintrag aus dem Verzeichnis einer Quelle. */
data class SitemapEntry(
    val url: String,
    val title: String,
)

/**
 * Liest die von den Quellen veroeffentlichten Sitemaps.
 *
 * Bewusst ohne XML-Bibliothek: Sitemaps sind flache Listen von `<loc>`-Elementen, und
 * ein regulaerer Ausdruck kommt hier mit hunderttausend Eintraegen deutlich schneller
 * durch als ein vollstaendiger XML-Parser.
 */
class SitemapParser @Inject constructor() {
    /** `true`, wenn das Dokument auf weitere Sitemaps verweist statt auf Seiten. */
    fun isIndex(xml: String): Boolean = xml.contains("<sitemapindex", ignoreCase = true)

    fun locations(xml: String): List<String> =
        LOC
            .findAll(xml)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()

    /**
     * Die Rezept-Links einer Quelle samt aus dem Link abgeleitetem Titel.
     *
     * Der Titel steht in Sitemaps nicht drin; er wird aus dem sprechenden Teil der
     * Adresse gewonnen ("aprikosen-blechkuchen" wird zu "Aprikosen Blechkuchen").
     * Das reicht fuer eine Suche und spart es, hunderte Seiten zu laden.
     */
    fun recipeEntries(xml: String, source: RecipeSource): List<SitemapEntry> =
        locations(xml)
            .filter { source.matches(it) }
            .map { SitemapEntry(url = it, title = titleFromUrl(it)) }
            .distinctBy { it.url }

    fun titleFromUrl(url: String): String {
        val slug = url
            .trimEnd('/')
            .substringAfterLast('/')
            .substringBefore('?')
            .removeSuffix(".html")
            .replace(TRAILING_ID, "")
            .replace(TRAILING_NOISE, "")

        return slug
            .split('-', '_')
            .filter { it.isNotBlank() && !it.all { char -> char.isDigit() } }
            .joinToString(" ") { part ->
                part.replaceFirstChar { char -> char.uppercase() }
            }.ifBlank { url }
    }

    private companion object {
        val LOC = Regex("<loc>\\s*([^<\\s]+)\\s*</loc>", RegexOption.IGNORE_CASE)

        /** Gutekueche haengt "-rezept-6" an, Betty Bossi eine lange Nummer. */
        val TRAILING_ID = Regex("-\\d+$")
        val TRAILING_NOISE = Regex("-(rezept|recipe|recette|ricetta)$", RegexOption.IGNORE_CASE)
    }
}
