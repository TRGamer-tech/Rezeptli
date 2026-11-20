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
            // Erst klein schreiben, dann das "www." abnehmen - sonst bliebe ein "WWW." stehen.
            .lowercase()
            .removePrefix("www.")
            .trimEnd('/')
}

/**
 * Macht aus einem Titel einen Vergleichsschluessel.
 *
 * Ein selbst erfasstes Rezept hat keine Adresse - wiedererkennen laesst es sich nur an seinem
 * Titel. Gross- und Kleinschreibung und ueberzaehlige Leerzeichen spielen dabei keine Rolle:
 * "Omas  Gratin" und "omas gratin" sind dasselbe Rezept.
 */
object TitleKey {
    private val SPACES = Regex("\\s+")

    fun of(title: String): String = title.trim().lowercase().replace(SPACES, " ")
}
