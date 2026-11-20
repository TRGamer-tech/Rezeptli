package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.deck.CuratedDeckBuilder
import ch.rezeptli.app.domain.deck.DeckEntry
import ch.rezeptli.app.domain.deck.DeckTitles
import ch.rezeptli.app.domain.plan.CardRules
import ch.rezeptli.app.domain.plan.CourseChoice
import ch.rezeptli.app.domain.plan.RecipeClassifier
import ch.rezeptli.app.domain.profile.Appliance
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.LanguageRegion
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.ranking.SourceOrigin
import ch.rezeptli.app.domain.repository.DeckPool
import ch.rezeptli.app.fake.FakeUserProfileRepository
import ch.rezeptli.app.fake.FakeWebRecipeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BuildSwipeDeckUseCaseTest {
    private fun eintraege(quelle: String, anzahl: Int) = (1..anzahl).map { nummer ->
        DeckEntry(
            url = "https://$quelle.example/rezept-$nummer",
            title = "Rezept $nummer",
            sourceId = quelle,
            sourceName = quelle,
        )
    }

    private fun eintrag(titel: String, quelle: String = "schweiz") = DeckEntry(
        url = "https://$quelle.example/${titel.lowercase().replace(' ', '-')}",
        title = titel,
        sourceId = quelle,
        sourceName = quelle,
    )

    private val herkunft = mapOf(
        "schweiz" to SourceOrigin("schweiz", Country.SCHWEIZ),
        "italien" to SourceOrigin("italien", Country.ITALIEN),
    )

    private val pool = DeckPool(
        entriesBySource = mapOf(
            "schweiz" to eintraege("schweiz", 40),
            "italien" to eintraege("italien", 40),
        ),
        origins = herkunft,
    )

    private fun useCase(
        profile: UserProfile = UserProfile(country = Country.SCHWEIZ),
        web: FakeWebRecipeRepository = FakeWebRecipeRepository(pool = pool),
    ) = BuildSwipeDeckUseCase(
        webRepository = web,
        profileRepository = FakeUserProfileRepository(profile),
        builder = CuratedDeckBuilder(),
        titles = DeckTitles(RecipeClassifier()),
        dispatcher = UnconfinedTestDispatcher(),
    )

    private fun mitTiteln(vararg titel: String) =
        FakeWebRecipeRepository(pool = DeckPool(mapOf("schweiz" to titel.map { eintrag(it) }), herkunft))

    @Test
    fun `zieht so viele Karten wie verlangt`() = runTest {
        val deck = useCase()(size = 20)

        assertEquals(20, deck.size)
    }

    @Test
    fun `wiederholt keine Adresse innerhalb eines Stapels`() = runTest {
        val deck = useCase()(size = 30)

        assertEquals(deck.size, deck.map { it.url }.distinct().size)
    }

    @Test
    fun `laesst ausgeschlossene Adressen aus`() = runTest {
        val gesehen = eintraege("schweiz", 40).map { it.url }.toSet()

        val deck = useCase()(size = 20, exclude = gesehen)

        assertTrue(deck.none { it.url in gesehen })
        assertTrue(deck.all { it.sourceId == "italien" })
    }

    @Test
    fun `ein gespeichertes Rezept gilt als ausgeschlossen, auch wenn seine Adresse leicht anders aussieht`() = runTest {
        // Gespeichert ueber "Teilen" oder die Suche: mit "www", ohne "https", mit Anhaengseln.
        val gespeichert = setOf(
            "http://www.schweiz.example/rezept-1/",
            "https://schweiz.example/rezept-2?utm_source=newsletter",
            "https://SCHWEIZ.example/rezept-3#zutaten",
        )

        val deck = useCase()(size = 80, exclude = gespeichert)

        assertEquals(77, deck.size)
        assertTrue(deck.none { it.url.endsWith("schweiz.example/rezept-1") })
        assertTrue(deck.none { it.url.endsWith("schweiz.example/rezept-2") })
        assertTrue(deck.none { it.url.endsWith("schweiz.example/rezept-3") })
    }

    @Test
    fun `gibt einen leeren Stapel zurueck, wenn das Verzeichnis leer ist`() = runTest {
        val leer = useCase(profile = UserProfile(), web = FakeWebRecipeRepository(pool = DeckPool()))

        assertTrue(leer().isEmpty())
    }

    @Test
    fun `holt den Vorrat einmal pro Stapel`() = runTest {
        val repository = FakeWebRecipeRepository(pool = pool)

        useCase(web = repository)(size = 10)

        assertEquals(1, repository.deckPoolCalls)
    }

    @Test
    fun `nur Essen laesst Getraenke weg, nur Getraenke alles andere`() = runTest {
        val web = mitTiteln("Kartoffelgratin", "Erdbeer-Smoothie", "Schokoladenkuchen", "Mojito", "Linsensuppe")
        val regeln = CardRules.of(UserProfile())

        val essen = useCase(web = web)(size = 10, rules = regeln.copy(course = CourseChoice.ESSEN))
        val getraenke = useCase(web = web)(size = 10, rules = regeln.copy(course = CourseChoice.GETRAENKE))
        val beides = useCase(web = web)(size = 10, rules = regeln.copy(course = CourseChoice.BEIDES))

        assertEquals(setOf("Kartoffelgratin", "Schokoladenkuchen", "Linsensuppe"), essen.map { it.title }.toSet())
        assertEquals(setOf("Erdbeer-Smoothie", "Mojito"), getraenke.map { it.title }.toSet())
        assertEquals(5, beides.size)
    }

    @Test
    fun `ohne eigene Regeln gelten die Vorlieben aus dem Profil`() = runTest {
        val web = mitTiteln("Lachsfilet mit Dill", "Kartoffelgratin", "Pilzrisotto", "Pommes aus dem Airfryer")
        val profil = UserProfile(
            country = Country.SCHWEIZ,
            dislikes = setOf(Dislike.FISCH, Dislike.PILZE),
            appliances = setOf(Appliance.HERD, Appliance.BACKOFEN),
        )

        val deck = useCase(profile = profil, web = web)(size = 10)

        // Was sich schon am Titel als unpassend erkennen laesst, kommt gar nicht erst in den Stapel.
        assertEquals(listOf("Kartoffelgratin"), deck.map { it.title })
    }

    @Test
    fun `eine gewaehlte Kueche kommt haeufiger vor, ohne dass etwas ganz verschwindet`() = runTest {
        val asiatisch = (1..30).map { "Gemuesecurry Nummer $it" }
        val italienisch = (1..30).map { "Lasagne Nummer $it" }
        val ohneKueche = (1..30).map { "Kartoffelgratin Nummer $it" }
        val web = mitTiteln(*(asiatisch + italienisch + ohneKueche).toTypedArray())
        val profil = UserProfile(country = Country.SCHWEIZ, cuisines = setOf(Cuisine.ASIATISCH))

        val deck = useCase(profile = profil, web = web)(size = 20)

        // Die Kueche wird am echten Titel erkannt - hier haengt die ganze Kette zusammen.
        assertEquals(10, deck.count { it.title.startsWith("Gemuesecurry") })
        assertEquals(2, deck.count { it.title.startsWith("Lasagne") })
        assertEquals(8, deck.count { it.title.startsWith("Kartoffelgratin") })
    }

    @Test
    fun `auf einen Sprachraum beschraenkt kommen nur dessen Quellen`() = runTest {
        val deck = useCase()(size = 30, onlyRegion = LanguageRegion.DEUTSCH)

        assertEquals(30, deck.size)
        assertTrue(deck.all { it.sourceId == "schweiz" })
    }
}
