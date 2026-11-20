package ch.rezeptli.app.domain.deck

/**
 * Macht aus einer Adresse einen Vergleichsschluessel.
 *
 * Dasselbe Rezept kann unter leicht verschiedenen Adressen bekannt sein - mit oder ohne
 * "www.", mit "http" statt "https", mit einem Schraegstrich am Ende oder mit angehaengten
 * Abfrageparametern, je nachdem, ob es aus dem Verzeichnis, aus der Suche oder ueber
 * "Teilen" kam. Fuer die Frage "habe ich das schon?" ist das alles dasselbe Rezept.
 */
object UrlKey {
    fun of(url: String): String =
        url
            .trim()
            .substringBefore('#')
            .substringBefore('?')
            .substringAfter("://")
            .removePrefix("www.")
            .trimEnd('/')
            .lowercase()
}
