package ch.rezeptli.app.data.web

/**
 * Das fertig gebaute Rezeptverzeichnis.
 *
 * Ein taeglicher Auftrag holt die Sitemaps der Anbieter einmal zentral und legt das
 * Ergebnis als eine Datei je Quelle ab. Die App laedt daraus einen einzigen Abruf,
 * statt sich selbst durch Verzeichnis und Unterverzeichnisse zu arbeiten - das war
 * die Ursache der langen ersten Suche, und es schont nebenbei die Server der Anbieter:
 * ein Abruf taeglich fuer alle zusammen statt einer je Installation.
 *
 * Faellt der Index aus, geht die Suche den alten Weg ueber die Sitemaps. Die App
 * bleibt also unabhaengig von ihm.
 */
interface PrebuiltIndex {
    /**
     * Das Verzeichnis einer Quelle, oder `null`, wenn es nicht erreichbar ist.
     *
     * `null` ist ausdruecklich kein Fehler, sondern der Hinweis, den anderen Weg zu
     * nehmen.
     */
    suspend fun entriesFor(sourceId: String): List<SitemapEntry>?
}
