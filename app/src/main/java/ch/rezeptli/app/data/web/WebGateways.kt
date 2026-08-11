package ch.rezeptli.app.data.web

import java.io.IOException

/**
 * Laedt eine Seite. Als Schnittstelle formuliert, damit die Suchlogik ohne Netzwerk
 * und ohne Android getestet werden kann.
 */
interface PageFetcher {
    suspend fun fetch(url: String, source: RecipeSource? = null): String
}

/** Ablage fuer das Rezeptverzeichnis einer Quelle. */
interface WebIndexStore {
    suspend fun read(sourceId: String): List<SitemapEntry>?

    suspend fun write(sourceId: String, entries: List<SitemapEntry>)
}

/** Was beim Laden einer Seite schiefgehen kann - in Worten, die die UI zeigen kann. */
sealed interface WebFetchError {
    data object NoConnection : WebFetchError

    /** Die Seite weist den Zugriff ab (etwa mit 403). */
    data object Rejected : WebFetchError

    /** Die robots.txt der Seite untersagt den Abruf dieser Adresse. */
    data object Disallowed : WebFetchError

    data object NotFound : WebFetchError

    data class Unexpected(val statusCode: Int) : WebFetchError
}

class WebFetchException(val error: WebFetchError, message: String) : IOException(message)
