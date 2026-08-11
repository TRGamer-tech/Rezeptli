package ch.rezeptli.app.fake

import ch.rezeptli.app.domain.model.WebRecipe
import ch.rezeptli.app.domain.model.WebSearchResult
import ch.rezeptli.app.domain.repository.WebImportError
import ch.rezeptli.app.domain.repository.WebRecipeRepository
import ch.rezeptli.app.domain.repository.WebRecipeResult

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

    override suspend fun search(query: String, sourceIds: Set<String>): List<WebSearchResult> {
        lastQuery = query
        lastSourceIds = sourceIds
        return results
    }

    override suspend fun loadRecipe(url: String): WebRecipeResult {
        error?.let { return WebRecipeResult.Failed(it) }
        val recipe = recipeByUrl[url] ?: return WebRecipeResult.Failed(WebImportError.NO_RECIPE_FOUND)
        return WebRecipeResult.Loaded(recipe)
    }
}
