package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.WebRecipe
import ch.rezeptli.app.domain.model.WebSearchResult
import ch.rezeptli.app.domain.parser.IngredientTextParser
import ch.rezeptli.app.domain.ranking.SourceRankingService
import ch.rezeptli.app.domain.repository.UserProfileRepository
import ch.rezeptli.app.domain.repository.WebImportError
import ch.rezeptli.app.domain.repository.WebRecipeRepository
import ch.rezeptli.app.domain.repository.WebRecipeResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SearchWebRecipesUseCase @Inject constructor(
    private val repository: WebRecipeRepository,
    private val profileRepository: UserProfileRepository,
    private val ranking: SourceRankingService,
) {
    /**
     * Sucht und bringt die Treffer in die Reihenfolge, die zum Profil passt.
     *
     * Die Quelle bestimmt die Reihenfolge, nicht der Treffer selbst: Wer in der Schweiz
     * kocht, sieht Schweizer Quellen zuerst. Innerhalb derselben Quelle bleibt die
     * Reihenfolge der Suche erhalten.
     */
    suspend operator fun invoke(query: String, sourceIds: Set<String>): List<WebSearchResult> {
        if (query.isBlank() || sourceIds.isEmpty()) return emptyList()

        val results = repository.search(query.trim(), sourceIds)
        val profile = profileRepository.profile.first()
        return ranking.rankItems(results, profile) { it.origin }
    }
}

sealed interface WebImportOutcome {
    data class Loaded(val recipe: Recipe) : WebImportOutcome

    data class Failed(val error: WebImportError) : WebImportOutcome
}

/**
 * Holt ein Rezept von seiner Adresse und macht ein Rezeptli-Rezept daraus.
 *
 * Die Zutatenzeilen laufen durch denselben Parser wie ein von Hand eingefuegter Text -
 * ein importiertes Rezept ist danach nicht von einem selbst erfassten zu unterscheiden
 * und laesst sich genauso bearbeiten. Gespeichert wird hier noch nichts: Der Vorschlag
 * geht zuerst zur Kontrolle an die Nutzerin.
 */
class LoadWebRecipeUseCase @Inject constructor(
    private val repository: WebRecipeRepository,
    private val ingredientParser: IngredientTextParser,
) {
    suspend operator fun invoke(url: String): WebImportOutcome =
        when (val result = repository.loadRecipe(url.trim())) {
            is WebRecipeResult.Failed -> WebImportOutcome.Failed(result.error)
            is WebRecipeResult.Loaded -> WebImportOutcome.Loaded(result.recipe.toRecipe())
        }

    private fun WebRecipe.toRecipe(): Recipe = Recipe(
        title = title,
        instructions = instructions,
        ingredients = ingredientLines
            .mapNotNull { line -> ingredientParser.parseLine(line) }
            .mapIndexed { index, parsed -> parsed.toIngredient(index) },
        // Die Quelle kommt als Tag dazu, damit sich importierte Rezepte filtern lassen.
        tags = (keywords + listOfNotNull(sourceName)).distinctBy { it.lowercase() },
        prepTimeMinutes = totalMinutes,
        photoUri = imageUrl,
        sourceUrl = sourceUrl,
        sourceName = sourceName,
    )
}
