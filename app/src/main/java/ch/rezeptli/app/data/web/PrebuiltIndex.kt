package ch.rezeptli.app.data.web

/** Ein Eintrag der Stapeldatei - wie ein Verzeichniseintrag, aber mit Quelle. */
data class SampleEntry(
    val url: String,
    val title: String,
    val imageUrl: String?,
    val sourceId: String,
)

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
     * Die kleine Auswahl fuer den Wischstapel, quer ueber alle Quellen.
     *
     * Der Stapel zeigt ein paar Dutzend Karten; die Verzeichnisse aller Quellen
     * zusammen haben ueber 300'000 Eintraege. Die vorher zu laden hiess: zweistellige
     * Megabytes und minutenlanges Warten vor der ersten Karte. Diese Datei ist ein
     * Bruchteil davon und wird taeglich mitgebaut.
     *
     * `null`, wenn sie nicht erreichbar ist - dann bleibt der lange Weg.
     */
    suspend fun deckSample(): List<SampleEntry>?

    /**
     * Das Verzeichnis einer Quelle, oder `null`, wenn es nicht erreichbar ist.
     *
     * `null` ist ausdruecklich kein Fehler, sondern der Hinweis, den anderen Weg zu
     * nehmen.
     */
    suspend fun entriesFor(sourceId: String): List<SitemapEntry>?
}
