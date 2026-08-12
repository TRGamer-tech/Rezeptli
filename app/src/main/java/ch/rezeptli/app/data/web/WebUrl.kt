package ch.rezeptli.app.data.web

/**
 * Macht aus einer Adresse, wie sie auf einer Seite steht, eine vollstaendige.
 *
 * Seiten schreiben Bildadressen selten vollstaendig aus. Kochrezepte.at etwa liefert
 * `//img2.kochrezepte.at/…` - gueltig im Browser, der das Protokoll der Seite ergaenzt,
 * aber unbrauchbar fuer einen Bildlader, der eine echte Adresse erwartet. Genau daran
 * sind dort die Bilder gescheitert.
 */
internal object WebUrl {
    fun absolute(value: String?, pageUrl: String): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed

            // "//host/pfad" uebernimmt das Protokoll der Seite.
            trimmed.startsWith("//") -> schemeOf(pageUrl) + ":" + trimmed

            // "/pfad" haengt an der Wurzel der Seite.
            trimmed.startsWith("/") -> originOf(pageUrl)?.let { it + trimmed }

            // Alles andere ist relativ zum Verzeichnis der Seite.
            !trimmed.contains(":") -> directoryOf(pageUrl)?.let { it + trimmed }

            // Unbekanntes Schema (data:, javascript: …) - lieber nichts als etwas Falsches.
            else -> null
        }
    }

    private fun schemeOf(pageUrl: String): String =
        if (pageUrl.startsWith("http://")) "http" else "https"

    private fun originOf(pageUrl: String): String? {
        val schemeEnd = pageUrl.indexOf("://")
        if (schemeEnd < 0) return null
        val hostEnd = pageUrl.indexOf('/', schemeEnd + 3)
        return if (hostEnd < 0) pageUrl else pageUrl.substring(0, hostEnd)
    }

    private fun directoryOf(pageUrl: String): String? {
        val ohneAbfrage = pageUrl.substringBefore('?').substringBefore('#')
        val letzterSchraegstrich = ohneAbfrage.lastIndexOf('/')
        if (letzterSchraegstrich <= ohneAbfrage.indexOf("://") + 2) {
            return originOf(pageUrl)?.let { "$it/" }
        }
        return ohneAbfrage.substring(0, letzterSchraegstrich + 1)
    }
}
