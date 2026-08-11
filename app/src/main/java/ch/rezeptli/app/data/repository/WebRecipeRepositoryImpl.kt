package ch.rezeptli.app.data.repository

import ch.rezeptli.app.data.web.PageFetcher
import ch.rezeptli.app.data.web.RecipeSourceCatalog
import ch.rezeptli.app.data.web.StructuredRecipeExtractor
import ch.rezeptli.app.data.web.WebFetchError
import ch.rezeptli.app.data.web.WebFetchException
import ch.rezeptli.app.data.web.WebRecipeSearcher
import ch.rezeptli.app.domain.model.WebSearchResult
import ch.rezeptli.app.domain.repository.WebImportError
import ch.rezeptli.app.domain.repository.WebRecipeRepository
import ch.rezeptli.app.domain.repository.WebRecipeResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRecipeRepositoryImpl @Inject constructor(
    private val searcher: WebRecipeSearcher,
    private val fetcher: PageFetcher,
    private val extractor: StructuredRecipeExtractor,
) : WebRecipeRepository {
    override suspend fun search(query: String, sourceIds: Set<String>): List<WebSearchResult> =
        RecipeSourceCatalog.SEARCHABLE
            .filter { it.id in sourceIds }
            .flatMap { source ->
                // Eine nicht erreichbare Quelle darf die Suche in den anderen nicht kippen.
                runCatching { searcher.search(source, query) }.getOrDefault(emptyList())
            }.sortedBy { it.title.length }

    override suspend fun loadRecipe(url: String): WebRecipeResult {
        val source = RecipeSourceCatalog.forUrl(url)
        val sourceName = source?.name ?: url.substringAfter("//").substringBefore('/')

        val html = try {
            fetcher.fetch(url, source)
        } catch (exception: WebFetchException) {
            return WebRecipeResult.Failed(exception.error.toDomain())
        }

        val recipe = extractor.extract(html, url, sourceName)
            ?: return WebRecipeResult.Failed(WebImportError.NO_RECIPE_FOUND)

        return WebRecipeResult.Loaded(recipe)
    }

    private fun WebFetchError.toDomain(): WebImportError = when (this) {
        WebFetchError.NoConnection -> WebImportError.NO_CONNECTION
        WebFetchError.Rejected -> WebImportError.REJECTED
        WebFetchError.Disallowed -> WebImportError.DISALLOWED
        WebFetchError.NotFound -> WebImportError.NOT_FOUND
        is WebFetchError.Unexpected -> WebImportError.UNKNOWN
    }
}
