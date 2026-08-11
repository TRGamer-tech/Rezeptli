package ch.rezeptli.app.data.web

import ch.rezeptli.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Laedt Seiten der Rezeptquellen.
 *
 * Die App meldet sich mit einem ehrlichen User-Agent samt Projektadresse - wer in
 * seinen Logs nachschaut, soll sehen koennen, wer da anklopft. Zwischen zwei Anfragen
 * an dieselbe Quelle wird das Crawl-delay ihrer robots.txt eingehalten.
 */
@Singleton
class RecipeWebClient @Inject constructor(
    private val client: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PageFetcher {
    private val lastRequestAt = mutableMapOf<String, Long>()
    private val mutex = Mutex()

    override suspend fun fetch(url: String, source: RecipeSource?): String = withContext(ioDispatcher) {
        source?.let { respectCrawlDelay(it) }

        val request = Request
            .Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "de-CH,de;q=0.9")
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (exception: IOException) {
            throw WebFetchException(WebFetchError.NoConnection, exception.message.orEmpty())
        }

        response.use {
            if (!it.isSuccessful) {
                val error = when (it.code) {
                    HTTP_FORBIDDEN, HTTP_TOO_MANY_REQUESTS -> WebFetchError.Rejected
                    HTTP_NOT_FOUND -> WebFetchError.NotFound
                    else -> WebFetchError.Unexpected(it.code)
                }
                throw WebFetchException(error, "HTTP ${it.code} für $url")
            }
            it.body?.string().orEmpty()
        }
    }

    private suspend fun respectCrawlDelay(source: RecipeSource) {
        if (source.minRequestIntervalMs <= 0L) return

        val waitFor = mutex.withLock {
            val now = System.currentTimeMillis()
            val earliest = (lastRequestAt[source.id] ?: 0L) + source.minRequestIntervalMs
            val wait = (earliest - now).coerceAtLeast(0L)
            lastRequestAt[source.id] = now + wait
            wait
        }
        if (waitFor > 0L) delay(waitFor)
    }

    private companion object {
        const val USER_AGENT =
            "Rezeptli/1.0 (Open-Source-Rezept-App; +https://github.com/TRGamer-tech/Rezeptli)"
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
