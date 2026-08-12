package ch.rezeptli.app.data.web

import javax.inject.Inject

/** Ein Eintrag aus dem Verzeichnis einer Quelle. */
data class SitemapEntry(
    val url: String,
    val title: String,
    /**
     * Bild aus dem Verzeichnis, falls die Quelle eines nennt.
     *
     * Viele Sitemaps fuehren zu jedem Eintrag ein `<image:loc>`. Wo das so ist, hat die
     * Wischkarte sofort ein Foto, ohne dass die App die Rezeptseite laden muss.
     */
    val imageUrl: String? = null,
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
    fun recipeEntries(xml: String, source: RecipeSource): List<SitemapEntry> {
        // Blockweise lesen, damit Adresse und Bild zusammenbleiben. Ohne <url>-Bloecke
        // - die gibt es - faellt es auf die reine Adressliste zurueck.
        val ausBloecken = URL_BLOCK
            .findAll(xml)
            .mapNotNull { block ->
                val inhalt = block.groupValues[1]
                val adresse = ersterTreffer(LOC, inhalt) ?: return@mapNotNull null
                if (!source.matches(adresse)) return@mapNotNull null

                SitemapEntry(
                    url = adresse,
                    title = titleFromUrl(adresse),
                    imageUrl = ersterTreffer(IMAGE_LOC, inhalt)?.let { WebUrl.absolute(it, adresse) },
                )
            }.distinctBy { it.url }
            .toList()

        if (ausBloecken.isNotEmpty()) return ausBloecken

        return locations(xml)
            .filter { source.matches(it) }
            .map { SitemapEntry(url = it, title = titleFromUrl(it)) }
            .distinctBy { it.url }
    }

    /** Der getrimmte erste Klammerausdruck des ersten Treffers, sonst null. */
    private fun ersterTreffer(regex: Regex, text: String): String? {
        val treffer = regex.find(text) ?: return null
        return treffer.groupValues[1].trim().takeIf { it.isNotEmpty() }
    }

    fun titleFromUrl(url: String): String {
        val slug = url
            .trimEnd('/')
            .substringAfterLast('/')
            .substringBefore('?')
            .replace(PAGE_SUFFIX, "")
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
        val URL_BLOCK = Regex("<url>(.*?)</url>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val IMAGE_LOC = Regex("<image:loc>\\s*([^<\\s]+)\\s*</image:loc>", RegexOption.IGNORE_CASE)

        /** Cookaround endet auf .html, CuisineAZ auf .aspx. */
        val PAGE_SUFFIX = Regex("\\.(html?|aspx)$", RegexOption.IGNORE_CASE)

        /** Gutekueche haengt "-rezept-6" an, Betty Bossi eine lange Nummer. */
        val TRAILING_ID = Regex("-\\d+$")

        /** Ptitchef schreibt "-fid" vor die Nummer. */
        val TRAILING_NOISE = Regex("-(rezept|recipe|recette|ricetta|fid)$", RegexOption.IGNORE_CASE)
    }
}
