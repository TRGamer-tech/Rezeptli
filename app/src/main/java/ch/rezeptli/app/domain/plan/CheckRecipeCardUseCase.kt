package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.nutrition.NutritionFacts
import ch.rezeptli.app.domain.nutrition.RecipeNutrition
import ch.rezeptli.app.domain.parser.IngredientTextParser
import ch.rezeptli.app.domain.repository.WebRecipeRepository
import ch.rezeptli.app.domain.repository.WebRecipeResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Was die App von einer Rezeptseite fuer eine Wischkarte gelesen hat.
 *
 * Genug, um zu entscheiden und nachzuschauen - Zutaten, Quelle, Zeit -, aber bewusst ohne
 * Zubereitung: Die gehoert zum gespeicherten Rezept, nicht auf eine Karte.
 */
data class CardDetails(
    val url: String,
    val title: String,
    val sourceName: String,
    val imageUrl: String?,
    val ingredientLines: List<String>,
    val totalMinutes: Int?,
    val servings: String?,
    /**
     * Die Einschaetzung des Rezepts - `null`, wenn sich die Seite nicht pruefen laesst:
     * Sie ist nicht auf Deutsch (die Zutaten wuerden an deutschen Stichworten vorbei
     * gelesen) oder nennt keine Zutaten.
     */
    val traits: RecipeTraits?,
    /** Die Seite ist nicht auf Deutsch - die Zutaten stehen in der Sprache der Quelle. */
    val isForeign: Boolean,
    /** Kalorien und Eiweiss: was die Seite nennt, sonst aus den Zutaten geschaetzt. */
    val nutrition: NutritionFacts? = null,
)

/**
 * Liest eine Rezeptseite fuer eine Karte und merkt sich das Ergebnis.
 *
 * Dieselbe Seite wird fuer mehrere Dinge gebraucht - die Pruefung gegen die Vorlieben, das
 * Bild, die Angaben beim Antippen der Karte. Geladen wird sie einmal; das Ergebnis haengt
 * nur an der Adresse und gilt fuer die Laufzeit der App.
 *
 * Ein fehlgeschlagener Abruf wird nicht behalten: Beim naechsten Mal darf es wieder
 * versucht werden.
 */
@Singleton
class ReadRecipeCardUseCase @Inject constructor(
    private val webRepository: WebRecipeRepository,
    private val ingredientParser: IngredientTextParser,
    private val classifier: RecipeClassifier,
) {
    private val gelesen = ConcurrentHashMap<String, CardDetails>()

    /** `null`, wenn die Seite gerade nicht zu lesen war. */
    suspend operator fun invoke(url: String): CardDetails? {
        gelesen[url]?.let { return it }

        val web = (webRepository.loadRecipe(url) as? WebRecipeResult.Loaded)?.recipe ?: return null
        val fremd = web.sourceLanguage != null

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
        val pruefbar = !fremd && rezept.ingredients.isNotEmpty()

        return CardDetails(
            url = url,
            title = web.title,
            sourceName = web.sourceName,
            imageUrl = web.imageUrl,
            ingredientLines = web.ingredientLines,
            totalMinutes = web.totalMinutes,
            servings = web.servings,
            traits = if (pruefbar) classifier.classify(rezept) else null,
            isForeign = fremd,
            nutrition = RecipeNutrition.of(
                // Fremdsprachige Zutaten wuerden an den deutschen Stichworten vorbei gelesen -
                // dort zaehlt nur, was die Seite selbst nennt.
                ingredients = if (fremd) emptyList() else rezept.ingredients,
                servings = ServingsParser.parse(web.servings),
                source = web.nutrition,
            ),
        ).also { gelesen[url] = it }
    }
}

/** Was die Pruefung einer Karte ergeben hat. */
data class CardCheck(
    /** Darf die Karte gezeigt werden? */
    val allowed: Boolean,
    /** Das Bild der Rezeptseite - fuer Karten, deren Verzeichnis keins nennt. */
    val imageUrl: String?,
)

/**
 * Prueft eine Wischkarte gegen die Regeln der Runde, bevor sie gezeigt wird.
 *
 * Das Verzeichnis kennt nur Titel und Adresse; ob ein Rezept Nuesse enthaelt, steht erst
 * auf der Rezeptseite. Deshalb wird die Seite geladen und mit derselben Einschaetzung
 * gelesen wie fuer den Wochenplan.
 *
 * Im Zweifel faellt die Karte heraus: Laesst sich die Seite nicht lesen oder nicht
 * pruefen, wird sie nicht gezeigt, sobald Ernaehrung, Unvertraeglichkeiten oder Abneigungen
 * im Spiel sind. Ein verpasstes Rezept ist harmloser als ein uebersehenes Allergen.
 */
class CheckRecipeCardUseCase @Inject constructor(
    private val read: ReadRecipeCardUseCase,
) {
    suspend operator fun invoke(url: String, rules: CardRules): CardCheck {
        val seite = read(url)
        val traits = seite?.traits
        val erlaubt = when {
            traits != null -> rules.allows(traits)
            else -> !rules.needsPage
        }
        return CardCheck(allowed = erlaubt, imageUrl = seite?.imageUrl)
    }
}
