package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.WebRecipe
import ch.rezeptli.app.domain.parser.IngredientTextParser
import ch.rezeptli.app.domain.ranking.SourceRankingService
import ch.rezeptli.app.domain.repository.UserProfileRepository
import ch.rezeptli.app.domain.repository.WebImportError
import ch.rezeptli.app.domain.repository.WebRecipeRepository
import ch.rezeptli.app.domain.repository.WebRecipeResult
import ch.rezeptli.app.domain.repository.WebSearchUpdate
import ch.rezeptli.app.domain.steps.InstructionSplitter
import ch.rezeptli.app.domain.steps.RecipeStep
import ch.rezeptli.app.domain.translate.RecipeTranslator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject

private const val DEUTSCH = "de"

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
    operator fun invoke(query: String, sourceIds: Set<String>): Flow<WebSearchUpdate> {
        if (query.isBlank() || sourceIds.isEmpty()) return flowOf(WebSearchUpdate())

        return repository.search(query.trim(), sourceIds).map { update ->
            val profile = profileRepository.profile.first()
            update.copy(results = ranking.rankItems(update.results, profile) { it.origin })
        }
    }

    /** Laedt die Verzeichnisse im Voraus, damit die erste Suche nicht darauf wartet. */
    suspend fun warmUp(sourceIds: Set<String>) = repository.warmUp(sourceIds)
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
    private val splitter: InstructionSplitter,
    private val translator: RecipeTranslator,
) {
    suspend operator fun invoke(url: String): WebImportOutcome =
        when (val result = repository.loadRecipe(url.trim())) {
            is WebRecipeResult.Failed -> WebImportOutcome.Failed(result.error)
            is WebRecipeResult.Loaded -> WebImportOutcome.Loaded(
                uebersetzeWennNoetig(result.recipe).toRecipe(),
            )
        }

    /**
     * Uebersetzt ein fremdsprachiges Rezept, wenn das Geraet auf Deutsch steht.
     *
     * Titel, Zutaten und Schritte gehen in einem Rutsch durch die Uebersetzung und
     * kommen in derselben Reihenfolge zurueck - so bleibt zusammen, was zusammengehoert,
     * und das Modell sieht genug Zusammenhang, um "Blanc d oeuf" nicht als
     * "Weiss von Ei" auszugeben.
     *
     * Klappt es nicht, bleibt das Original stehen. Ein franzoesisches Rezept ist
     * besser als gar keins.
     */
    private suspend fun uebersetzeWennNoetig(recipe: WebRecipe): WebRecipe {
        val sprache = recipe.sourceLanguage ?: return recipe
        if (Locale.getDefault().language != DEUTSCH) return recipe

        val schritte = recipe.instructionSteps.ifEmpty {
            recipe.instructions.split('\n').filter { it.isNotBlank() }
        }
        val stapel = listOf(recipe.title) + recipe.ingredientLines + schritte

        val uebersetzt = translator.toGerman(stapel, sprache) ?: return recipe
        if (uebersetzt.size != stapel.size) return recipe

        val zutatenEnde = 1 + recipe.ingredientLines.size
        val neueSchritte = uebersetzt.drop(zutatenEnde)

        return recipe.copy(
            title = uebersetzt.first(),
            ingredientLines = uebersetzt.subList(1, zutatenEnde),
            instructionSteps = neueSchritte,
            instructions = neueSchritte.joinToString("\n"),
        )
    }

    private fun WebRecipe.toRecipe(): Recipe = Recipe(
        title = title,
        instructions = instructions,
        // Die Gliederung der Quelle stammt von Menschen und ist damit besser als jede
        // Aufteilung, die die App selbst vornehmen koennte. Nur wenn die Seite die
        // Zubereitung als einen Block liefert, wird sie spaeter aufgeteilt.
        steps = instructionSteps.mapIndexed { index, text ->
            RecipeStep(
                position = index,
                text = text,
                timerMinutes = splitter.timerMinutesIn(text),
            )
        },
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
