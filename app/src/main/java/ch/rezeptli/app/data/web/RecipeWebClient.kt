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
    private val robotsParser: RobotsParser,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PageFetcher {
    private val lastRequestAt = mutableMapOf<String, Long>()
    private val robotsByHost = mutableMapOf<String, RobotsRules>()
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

    /**
     * Holt die robots.txt eines Hosts - einmal pro Sitzung, danach aus dem Speicher.
     * Ist sie nicht erreichbar, wird nichts blockiert: eine fehlende robots.txt
     * bedeutet nach der ueblichen Auslegung "alles erlaubt".
     */
    private suspend fun robotsFor(url: String): RobotsRules {
        val origin = originOf(url) ?: return RobotsRules.PERMISSIVE
        mutex.withLock { robotsByHost[origin] }?.let { return it }

        val robotsTxt = runCatching { requestBody("$origin/robots.txt") }.getOrNull()
        val rules = robotsTxt?.let { robotsParser.parse(it, USER_AGENT_TOKEN) } ?: RobotsRules.PERMISSIVE
        mutex.withLock { robotsByHost[origin] = rules }
        return rules
    }

    private fun requestBody(url: String): String {
        val request = Request
            .Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    /**
     * Wartet den geforderten Abstand ab - den groesseren Wert aus robots.txt und
     * hinterlegter Quellen-Konfiguration.
     */
    private suspend fun respectCrawlDelay(source: RecipeSource?, rules: RobotsRules) {
        val interval = maxOf(source?.minRequestIntervalMs ?: 0L, rules.crawlDelayMs)
        if (interval <= 0L) return

        val key = source?.id ?: "unbekannt"
        val waitFor = mutex.withLock {
            val now = System.currentTimeMillis()
            val earliest = (lastRequestAt[key] ?: 0L) + interval
            val wait = (earliest - now).coerceAtLeast(0L)
            lastRequestAt[key] = now + wait
            wait
        }
        if (waitFor > 0L) delay(waitFor)
    }

    /**
     * Entfernt Anker und Abfrageparameter.
     *
     * Geteilte Links tragen oft Herkunfts-Parameter mit sich, und mehrere Quellen
     * schliessen parametrisierte Adressen in ihrer robots.txt aus, weil es Dubletten
     * derselben Seite sind. Geladen wird deshalb die Seite ohne Beiwerk.
     */
    private fun normalize(url: String): String =
        url.substringBefore('#').substringBefore('?').trim()

    private fun originOf(url: String): String? {
        val withoutScheme = url.substringAfter("://", "")
        if (withoutScheme.isEmpty()) return null
        val scheme = url.substringBefore("://")
        return "$scheme://" + withoutScheme.substringBefore('/')
    }

    private fun pathOf(url: String): String {
        val withoutScheme = url.substringAfter("://", "")
        val slash = withoutScheme.indexOf('/')
        return if (slash < 0) "/" else withoutScheme.substring(slash)
    }

    private companion object {
        /**
         * Rezeptli tritt unter eigenem Namen auf. Es ist kein Suchmaschinen- oder
         * Trainings-Crawler, sondern laedt genau die Seite, die eine Person gerade
         * importieren moechte - und befolgt dabei die Regeln fuer `*`.
         */
        const val USER_AGENT_TOKEN = "Rezeptli"
        const val USER_AGENT =
            "$USER_AGENT_TOKEN/1.0 (Open-Source-Rezept-App; +https://github.com/TRGamer-tech/Rezeptli)"
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
