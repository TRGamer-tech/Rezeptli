package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.deck.CuratedDeckBuilder
import ch.rezeptli.app.domain.deck.DeckEntry
import ch.rezeptli.app.domain.deck.DeckTitles
import ch.rezeptli.app.domain.model.WebRecipe
import ch.rezeptli.app.domain.multiplayer.RoundSource
import ch.rezeptli.app.domain.multiplayer.RoundState
import ch.rezeptli.app.domain.parser.IngredientTextParser
import ch.rezeptli.app.domain.plan.CheckRecipeCardUseCase
import ch.rezeptli.app.domain.plan.DietaryFilter
import ch.rezeptli.app.domain.plan.ReadRecipeCardUseCase
import ch.rezeptli.app.domain.plan.RecipeClassifier
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.ranking.SourceOrigin
import ch.rezeptli.app.domain.repository.DeckPool
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.fake.FakeUserProfileRepository
import ch.rezeptli.app.fake.FakeWebRecipeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Neue Karten fuer die gemeinsame Runde: wie viele es sind und wie lange es dauert.
 *
 * Gemeldet war, dass "weiter mit neuen Rezepten" eine Viertelminute braucht. Der erste
 * Versuch, das zu beheben, halbierte die Zahl der Karten - gewuenscht war aber schnelleres
 * Laden bei gleicher Auswahl. Beides haelt dieser Test fest: die volle Anzahl, und dass eine
 * traege Seite die anderen nicht aufhaelt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BuildRoundContributionUseCaseTest {
    private val anbieter = listOf("a", "b", "c", "d")
    private val jeAnbieter = 40

    private fun eintrag(quelle: String, nummer: Int) = DeckEntry(
        url = "https://$quelle.example/eintopf-$nummer",
        title = "Eintopf $quelle $nummer",
        imageUrl = "https://$quelle.example/eintopf-$nummer.jpg",
        sourceId = quelle,
        sourceName = "Anbieter $quelle",
    )

    private val verzeichnis = anbieter.associateWith { quelle -> (1..jeAnbieter).map { eintrag(quelle, it) } }

    /** Jede dritte Seite nennt Fisch - das zeigt erst die Rezeptseite, nicht der Titel. */
    private fun hatFisch(url: String) = url.substringAfterLast('-').toInt() % 3 == 0

    private val web = FakeWebRecipeRepository(
        pool = DeckPool(
            entriesBySource = verzeichnis,
            origins = anbieter.associateWith { SourceOrigin(it, Country.SCHWEIZ) },
        ),
        recipeByUrl = verzeichnis.values.flatten().associate { eintrag ->
            eintrag.url to WebRecipe(
                title = eintrag.title,
                ingredientLines = if (hatFisch(eintrag.url)) {
                    listOf("400 g Lachsfilet", "300 g Kartoffeln")
                } else {
                    listOf("300 g Kartoffeln", "2 Karotten")
                },
                instructions = "Kochen.",
                imageUrl = eintrag.imageUrl,
                servings = "4",
                sourceUrl = eintrag.url,
                sourceName = eintrag.sourceName,
            )
        },
    )

    private fun TestScope.useCase(): BuildRoundContributionUseCase {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val profile = FakeUserProfileRepository(UserProfile(country = Country.SCHWEIZ))
        return BuildRoundContributionUseCase(
            recipeRepository = FakeRecipeRepository(),
            classifier = RecipeClassifier(),
            buildDeck = BuildSwipeDeckUseCase(
                webRepository = web,
                profileRepository = profile,
                builder = CuratedDeckBuilder(),
                titles = DeckTitles(RecipeClassifier()),
                dispatcher = dispatcher,
            ),
            checkCard = CheckRecipeCardUseCase(ReadRecipeCardUseCase(web, IngredientTextParser(), RecipeClassifier())),
            dispatcher = dispatcher,
        )
    }

    /** Eine Runde mit neuen Rezepten, in der jemand keinen Fisch isst - also muss jede Seite geprueft werden. */
    private fun runde(ziel: Int) = RoundState(
        code = "ABCDEF",
        source = RoundSource.NEU,
        target = ziel,
        restrictions = DietaryFilter(dislikes = setOf(Dislike.FISCH)),
    )

    @Test
    fun `jedes Telefon bringt den ganzen Stapel mit, nicht den halben`() = runTest {
        web.latencyMs = { 300L }

        val karten = useCase()(runde(ziel = 5))

        // Fuenf Gerichte gesucht: dreissig Karten - so viele wie beim Wischen allein.
        assertEquals(CuratedDeckBuilder.deckSizeFor(5), karten.size)
        assertEquals(30, karten.size)
        assertEquals(karten.size, karten.map { it.key }.toSet().size)
        assertTrue(karten.none { hatFisch(it.sourceUrl!!) }, "Fisch darf nicht dabei sein")
    }

    @Test
    fun `ein traeger Anbieter haelt die Karten der anderen nicht auf`() = runTest {
        // Anbieter d braucht neun Sekunden je Seite, die anderen eine halbe.
        web.latencyMs = { url -> if (url.startsWith("https://d.example/")) 9_000L else 500L }
        val beginn = testScheduler.currentTime

        val karten = useCase()(runde(ziel = 5))
        val dauer = testScheduler.currentTime - beginn

        // In Schueben haette jeder Schub auf seine langsamste Seite gewartet: bis zur Zeitgrenze
        // von dreissig Sekunden, und dann mit weniger Karten als gewuenscht.
        assertEquals(30, karten.size)
        assertTrue(dauer <= 6_000L, "Das Zusammenstellen dauerte $dauer ms")
    }

    @Test
    fun `sind genug Karten beisammen, wird nicht weitergeladen`() = runTest {
        web.latencyMs = { 300L }

        val karten = useCase()(runde(ziel = 2))

        assertEquals(12, karten.size)
        // Gezogen wurden 48 Kandidaten. Geladen wird, bis zwoelf bestanden haben, plus was
        // in dem Moment gerade unterwegs war - nicht alle.
        assertTrue(web.loadCalls.size < 40, "${web.loadCalls.size} Seiten geladen")
    }

    @Test
    fun `je Anbieter laufen hoechstens zwei Abrufe zugleich`() = runTest {
        web.latencyMs = { 300L }

        useCase()(runde(ziel = 5))

        assertTrue(web.peakLoadsByHost.isNotEmpty())
        assertTrue(web.peakLoadsByHost.values.all { it <= 2 }, web.peakLoadsByHost.toString())
        assertTrue(web.peakLoads <= 12, "${web.peakLoads} gleichzeitig")
        // Und es wird wirklich nebeneinander geladen, nicht eine Seite nach der anderen.
        assertTrue(web.peakLoads >= 4, "${web.peakLoads} gleichzeitig")
    }

    @Test
    fun `antwortet gar nichts, endet das Warten an der Zeitgrenze statt nie`() = runTest {
        web.latencyMs = { 120_000L }
        val beginn = testScheduler.currentTime

        val karten = useCase()(runde(ziel = 5))
        val dauer = testScheduler.currentTime - beginn

        assertTrue(karten.isEmpty())
        assertTrue(dauer in 30_000L..31_000L, "Das Warten dauerte $dauer ms")
    }

    @Test
    fun `ohne Angaben braucht es keine einzige Rezeptseite`() = runTest {
        web.latencyMs = { 5_000L }
        val beginn = testScheduler.currentTime

        val karten = useCase()(runde(ziel = 5).copy(restrictions = DietaryFilter()))

        assertEquals(30, karten.size)
        assertTrue(web.loadCalls.isEmpty())
        assertEquals(beginn, testScheduler.currentTime)
    }
}
