package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.parser.IngredientTextParser
import ch.rezeptli.app.domain.repository.WebRecipeRepository
import ch.rezeptli.app.domain.repository.WebRecipeResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Was die Pruefung einer Karte ergeben hat. */
data class CardCheck(
    /** Darf die Karte gezeigt werden? */
    val allowed: Boolean,
    /** Das Bild der Rezeptseite - fuer Karten, deren Verzeichnis keins nennt. */
    val imageUrl: String?,
)

/**
 * Prueft eine Wischkarte gegen Ernaehrung und Unvertraeglichkeiten, bevor sie gezeigt wird.
 *
 * Das Verzeichnis kennt nur Titel und Adresse; ob ein Rezept Nuesse enthaelt, steht erst
 * auf der Rezeptseite. Deshalb wird die Seite geladen und mit derselben Einschaetzung
 * gelesen wie fuer den Wochenplan.
 *
 * Im Zweifel faellt die Karte heraus: Laesst sich die Seite nicht lesen oder ist sie nicht
 * auf Deutsch (die Zutaten wuerden dann an deutschen Stichworten vorbei gelesen), wird sie
 * bei aktivem Filter nicht gezeigt. Ein verpasstes Rezept ist harmloser als ein
 * uebersehenes Allergen.
 *
 * Das Ergebnis haengt nur an der Adresse und wird fuer die Laufzeit der App behalten.
 */
@Singleton
class CheckRecipeCardUseCase @Inject constructor(
    private val webRepository: WebRecipeRepository,
    private val ingredientParser: IngredientTextParser,
    private val classifier: RecipeClassifier,
) {
    private data class Gelesen(val traits: RecipeTraits?, val imageUrl: String?)

    private val gelesen = ConcurrentHashMap<String, Gelesen>()

    suspend operator fun invoke(url: String, filter: DietaryFilter): CardCheck {
        val seite = gelesen[url] ?: lesen(url).also { gelesen[url] = it }
        val erlaubt = when {
            !filter.isActive -> true
            seite.traits == null -> false
            else -> filter.allows(seite.traits)
        }
        return CardCheck(allowed = erlaubt, imageUrl = seite.imageUrl)
    }

    private suspend fun lesen(url: String): Gelesen {
        val web = (webRepository.loadRecipe(url) as? WebRecipeResult.Loaded)?.recipe
            ?: return Gelesen(traits = null, imageUrl = null)

        // Fremdsprachige Seiten lassen sich mit den deutschen Stichworten nicht pruefen.
        if (web.sourceLanguage != null) return Gelesen(traits = null, imageUrl = web.imageUrl)

        val rezept = Recipe(
            title = web.title,
            instructions = (web.instructionSteps.ifEmpty { listOf(web.instructions) }).joinToString("\n"),
            ingredients = web.ingredientLines
                .mapNotNull { ingredientParser.parseLine(it) }
                .mapIndexed { index, parsed -> parsed.toIngredient(index) },
            tags = web.keywords,
            prepTimeMinutes = web.totalMinutes,
        )
        // Ohne Zutaten gibt es nichts zu pruefen - und damit auch keine Zusage.
        if (rezept.ingredients.isEmpty()) return Gelesen(traits = null, imageUrl = web.imageUrl)

        return Gelesen(traits = classifier.classify(rezept), imageUrl = web.imageUrl)
    }
}
