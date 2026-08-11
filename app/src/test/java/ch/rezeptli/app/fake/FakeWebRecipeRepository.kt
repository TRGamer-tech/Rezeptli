package ch.rezeptli.app.fake

import ch.rezeptli.app.domain.model.WebRecipe
import ch.rezeptli.app.domain.model.WebSearchResult
import ch.rezeptli.app.domain.repository.WebImportError
import ch.rezeptli.app.domain.repository.WebRecipeRepository
import ch.rezeptli.app.domain.repository.WebRecipeResult
import ch.rezeptli.app.domain.repository.WebSearchUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Liefert vorgegebene Antworten, damit der Import ohne Netz getestet werden kann. */
class FakeWebRecipeRepository(
    private val results: List<WebSearchResult> = emptyList(),
    private var recipeByUrl: Map<String, WebRecipe> = emptyMap(),
    private var error: WebImportError? = null,
) : WebRecipeRepository {
    var lastQuery: String? = null
        private set
    var lastSourceIds: Set<String> = emptySet()
        private set

    var warmedUpSources: Set<String> = emptySet()
        private set

    /** Meldet die Treffer in zwei Schueben, so wie es zwei Quellen tun wuerden. */
    override fun search(query: String, sourceIds: Set<String>): Flow<WebSearchUpdate> = flow {
        lastQuery = query
        lastSourceIds = sourceIds

        val half = results.size / 2
        emit(WebSearchUpdate(totalSources = 2))
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
}
