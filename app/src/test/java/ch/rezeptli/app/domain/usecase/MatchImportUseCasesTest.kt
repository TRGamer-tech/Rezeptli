package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.model.WebRecipe
import ch.rezeptli.app.domain.multiplayer.RoundKeys
import ch.rezeptli.app.domain.multiplayer.SharedContent
import ch.rezeptli.app.domain.multiplayer.SharedIngredient
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.parser.IngredientTextParser
import ch.rezeptli.app.domain.plan.PlanPersister
import ch.rezeptli.app.domain.steps.InstructionSplitter
import ch.rezeptli.app.domain.translate.NoTranslation
import ch.rezeptli.app.fake.FakePairingRepository
import ch.rezeptli.app.fake.FakePlanRepository
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.fake.FakeShoppingListRepository
import ch.rezeptli.app.fake.FakeWebRecipeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Gemeinsame Treffer uebernehmen, ohne dass etwas doppelt in der Sammlung landet.
 *
 * Jeder Test ist ein Weg, auf dem dasselbe Rezept ein zweites Mal entstehen konnte.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MatchImportUseCasesTest {
    private val roestiUrl = "https://www.beispiel.ch/rezepte/roesti"
    private val risottoUrl = "https://www.beispiel.ch/rezepte/risotto"

    private val meineRoesti = Recipe(
        id = 1L,
        title = "Rösti",
        instructions = "Raffeln und braten.",
        ingredients = listOf(Ingredient(name = "Kartoffeln")),
        sourceUrl = roestiUrl,
        sourceName = "Beispiel",
    )
    private val meinGratin = Recipe(
        id = 2L,
        title = "Omas Gratin",
        instructions = "Im Ofen backen.",
        ingredients = listOf(Ingredient(name = "Kartoffeln"), Ingredient(name = "Rahm")),
    )

    private val sammlung = FakeRecipeRepository(listOf(meineRoesti, meinGratin))
    private val pairing = FakePairingRepository().apply { partnerHosts() }
    private val web = FakeWebRecipeRepository(
        recipeByUrl = listOf(roestiUrl, risottoUrl, "$risottoUrl/").associateWith { url ->
            WebRecipe(
                title = if ("roesti" in url) "Rösti" else "Risotto",
                ingredientLines = listOf("300 g Risottoreis", "1 l Bouillon"),
                instructions = "Rühren.",
                servings = "4",
                sourceUrl = url,
                sourceName = "Beispiel",
            )
        },
    )

    private val holen = FetchMatchesUseCase(
        pairing,
        sammlung,
        LoadWebRecipeUseCase(web, IngredientTextParser(), InstructionSplitter(), NoTranslation),
    )
    private val behalten = KeepMatchesUseCase(sammlung, SaveRecipeUseCase(sammlung, InstructionSplitter()))

    private val eintopfInhalt = SharedContent(
        instructions = "Eine Stunde köcheln.",
        ingredients = listOf(SharedIngredient("Lauch", amount = 2.0), SharedIngredient("Kartoffeln")),
        servings = 4,
    )

    private fun karte(nummer: Long, titel: String, adresse: String? = null, schluessel: String? = null) = SharedRecipe(
        recipeId = nummer,
        title = titel,
        sourceUrl = adresse,
        key = schluessel ?: adresse?.let(RoundKeys::forUrl) ?: RoundKeys.forTitle(titel),
    )

    /** Holt und behaelt - so, wie es "In meine Rezepte uebernehmen" tut. */
    private suspend fun uebernehmen(vararg karten: SharedRecipe): Map<Long, Long> =
        behalten(holen("ABCDEF", karten.toList()).filterIsInstance<FetchedMatch.Ready>())

    private suspend fun titelInDerSammlung(): List<String> =
        sammlung.getFilteredRecipeIds(RecipeFilter()).mapNotNull { sammlung.getRecipe(it)?.title }.sorted()

    @Test
    fun `das eigene Rezept wird wiedererkannt, auch wenn das andere Telefon die Adresse anders schreibt`() = runTest {
        // Genau so entstanden die Dubletten: Beide haben die Rösti gespeichert, die Karte der
        // Runde traegt aber die Adresse des anderen Telefons.
        val ihreSchreibweise = "http://beispiel.ch/rezepte/roesti/?utm_source=teilen"

        val zuordnung = uebernehmen(karte(11L, "Rösti", ihreSchreibweise))

        assertEquals(mapOf(11L to meineRoesti.id), zuordnung)
        assertEquals(listOf("Omas Gratin", "Rösti"), titelInDerSammlung())
        // Es liegt schon hier - dafuer muss keine Seite geladen werden.
        assertTrue(web.loadCalls.isEmpty(), web.loadCalls.toString())
    }

    @Test
    fun `ein neues Rezept kommt einmal dazu - auch wenn es zweimal uebernommen wird`() = runTest {
        uebernehmen(karte(12L, "Risotto", risottoUrl))
        assertEquals(listOf("Omas Gratin", "Risotto", "Rösti"), titelInDerSammlung())

        // Noch einmal, diesmal mit der Adresse so, wie sie das andere Telefon kennt.
        val nochmal = uebernehmen(karte(12L, "Risotto", "$risottoUrl/"))

        assertEquals(listOf("Omas Gratin", "Risotto", "Rösti"), titelInDerSammlung())
        assertEquals(3, sammlung.everything.size)
        assertEquals(sammlung.findIdBySourceUrl(risottoUrl), nochmal.getValue(12L))
    }

    @Test
    fun `dasselbe Rezept unter zwei Schreibweisen in einer Runde wird nur einmal angelegt`() = runTest {
        uebernehmen(karte(12L, "Risotto", risottoUrl), karte(13L, "Risotto", "$risottoUrl/"))

        assertEquals(listOf("Omas Gratin", "Risotto", "Rösti"), titelInDerSammlung())
        assertEquals(3, sammlung.everything.size)
    }

    @Test
    fun `das eigene selbst erfasste Rezept wird am Titel wiedererkannt, nicht nachgeholt`() = runTest {
        // Die Karte hat die andere Person beigesteuert - sie hat das Gratin frueher uebernommen.
        val zuordnung = uebernehmen(karte(14L, "omas gratin"))

        assertEquals(mapOf(14L to meinGratin.id), zuordnung)
        assertEquals(listOf("Omas Gratin", "Rösti"), titelInDerSammlung())
        assertEquals(2, sammlung.everything.size)
    }

    @Test
    fun `eine Karte aus einer aelteren App wird genauso am Titel wiedererkannt`() = runTest {
        // Aeltere Fassungen nannten im Schluessel das Geraet und die dortige Nummer.
        val zuordnung = uebernehmen(karte(14L, "Omas Gratin", schluessel = "e:gegenueber:7"))

        assertEquals(mapOf(14L to meinGratin.id), zuordnung)
        assertEquals(2, sammlung.everything.size)
    }

    @Test
    fun `ein selbst erfasstes Rezept der anderen Person kommt einmal dazu - auch beim zweiten Mal`() = runTest {
        pairing.partnerShares(15L, eintopfInhalt)

        uebernehmen(karte(15L, "Beats Eintopf"))
        assertEquals(listOf("Beats Eintopf", "Omas Gratin", "Rösti"), titelInDerSammlung())

        // In der naechsten Runde ist es wieder ein Treffer, unter neuer Nummer des Dienstes.
        pairing.partnerShares(31L, eintopfInhalt)
        uebernehmen(karte(31L, "Beats Eintopf"))

        assertEquals(listOf("Beats Eintopf", "Omas Gratin", "Rösti"), titelInDerSammlung())
        assertEquals(3, sammlung.everything.size)
    }

    @Test
    fun `erst in den Wochenplan, dann in die Sammlung - es bleibt ein Rezept`() = runTest {
        val plan = PlanPersister(sammlung, FakeShoppingListRepository(), FakePlanRepository())
        pairing.partnerShares(15L, eintopfInhalt)
        val geholt = holen("ABCDEF", listOf(karte(12L, "Risotto", risottoUrl), karte(15L, "Beats Eintopf")))
            .filterIsInstance<FetchedMatch.Ready>()

        // Der Wochenplan speichert, was er zeigt - aber nicht in der Sammlung.
        val imPlan = geholt.map { plan.idOf(it.recipe) }
        assertEquals(listOf("Omas Gratin", "Rösti"), titelInDerSammlung())
        // Zweimal einplanen legt nichts Zweites an.
        assertEquals(imPlan, geholt.map { plan.idOf(it.recipe) })

        // Danach "In meine Rezepte uebernehmen" - mit frisch geholten Treffern, wie auf dem Telefon.
        val zuordnung = uebernehmen(karte(12L, "Risotto", "$risottoUrl/"), karte(15L, "Beats Eintopf"))

        assertEquals(listOf("Beats Eintopf", "Omas Gratin", "Risotto", "Rösti"), titelInDerSammlung())
        // Dieselben zwei Rezepte, die der Plan gespeichert hat - nicht zwei weitere daneben.
        assertEquals(imPlan, listOf(zuordnung.getValue(12L), zuordnung.getValue(15L)))
        assertEquals(4, sammlung.everything.size)
    }

    @Test
    fun `wurde das Rezept inzwischen geloescht, entsteht es neu statt zu fehlen`() = runTest {
        val geholt = holen("ABCDEF", listOf(karte(11L, "Rösti", roestiUrl))).filterIsInstance<FetchedMatch.Ready>()
        sammlung.deleteRecipe(meineRoesti.id)

        val zuordnung = behalten(geholt)

        assertEquals(listOf("Omas Gratin", "Rösti"), titelInDerSammlung())
        assertTrue(zuordnung.getValue(11L) != meineRoesti.id)
        assertEquals(2, sammlung.everything.size)
    }
}
