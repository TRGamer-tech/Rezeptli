package ch.rezeptli.app.fake

import ch.rezeptli.app.domain.model.WebRecipe
import ch.rezeptli.app.domain.model.WebSearchResult
import ch.rezeptli.app.domain.repository.DeckPool
import ch.rezeptli.app.domain.repository.WebImportError
import ch.rezeptli.app.domain.repository.WebRecipeRepository
import ch.rezeptli.app.domain.repository.WebRecipeResult
import ch.rezeptli.app.domain.repository.WebSearchUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Liefert vorgegebene Antworten, damit der Import ohne Netz getestet werden kann. */
class FakeWebRecipeRepository(
    private val results: List<WebSearchResult> = emptyList(),
    private var recipeByUrl: Map<String, WebRecipe> = emptyMap(),
    private var error: WebImportError? = null,
    private val pool: DeckPool = DeckPool(),
) : WebRecipeRepository {
    /** Wie oft der Vorrat geholt wurde - der Stapel soll ihn nicht doppelt anfragen. */
    var deckPoolCalls: Int = 0
        private set

    override suspend fun deckPool(): DeckPool {
        deckPoolCalls += 1
        return pool
    }

    var lastQuery: String? = null
        private set
    var lastSourceIds: Set<String> = emptySet()
        private set

    var warmedUpSources: Set<String> = emptySet()
        private set

    /**
     * Haelt die Suche an, bis der Test sie freigibt.
     *
     * Damit laesst sich pruefen, was auf dem Bildschirm steht, waehrend noch gesucht
     * wird - und nicht nur das Ergebnis danach.
     */
    var gate: CompletableDeferred<Unit>? = null

    /** Meldet die Treffer in zwei Schueben, so wie es zwei Quellen tun wuerden. */
    override fun search(query: String, sourceIds: Set<String>): Flow<WebSearchUpdate> = flow {
        lastQuery = query
        lastSourceIds = sourceIds

        emit(WebSearchUpdate(totalSources = 2))
        gate?.await()

        val half = results.size / 2
        emit(WebSearchUpdate(results.take(half), finishedSources = 1, totalSources = 2))
        emit(WebSearchUpdate(results, finishedSources = 2, totalSources = 2))
    }

    override suspend fun warmUp(sourceIds: Set<String>) {
        warmedUpSources = sourceIds
    }

    override suspend fun loadRecipe(url: String): WebRecipeResult {
        error?.let { return WebRecipeResult.Failed(it) }
        val recipe = recipeByUrl[url] ?: return WebRecipeResult.Failed(WebImportError.NO_RECIPE_FOUND)
        return WebRecipeResult.Loaded(recipe)
    }

    /** Was cardImage() liefert - je Adresse, oder ueberhaupt, oder gar nichts. */
    var cardImagesByUrl: Map<String, String> = emptyMap()
    var cardImageCalls: MutableList<String> = mutableListOf()
        private set

    override suspend fun cardImage(url: String): String? {
        cardImageCalls += url
        return cardImagesByUrl[url]
    }
}
