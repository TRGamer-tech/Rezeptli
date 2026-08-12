package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.deck.CuratedDeckBuilder
import ch.rezeptli.app.domain.deck.DeckEntry
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.ranking.SourceOrigin
import ch.rezeptli.app.domain.repository.DeckPool
import ch.rezeptli.app.fake.FakeUserProfileRepository
import ch.rezeptli.app.fake.FakeWebRecipeRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuildSwipeDeckUseCaseTest {
    private fun eintraege(quelle: String, anzahl: Int) = (1..anzahl).map { nummer ->
        DeckEntry(
            url = "https://$quelle.example/rezept-$nummer",
            title = "Rezept $nummer",
            sourceId = quelle,
            sourceName = quelle,
        )
    }

    private val pool = DeckPool(
        entriesBySource = mapOf(
            "schweiz" to eintraege("schweiz", 40),
            "italien" to eintraege("italien", 40),
        ),
        origins = mapOf(
            "schweiz" to SourceOrigin("schweiz", Country.SCHWEIZ),
            "italien" to SourceOrigin("italien", Country.ITALIEN),
        ),
    )

    private fun useCase(profile: UserProfile = UserProfile(country = Country.SCHWEIZ)) =
        BuildSwipeDeckUseCase(
            webRepository = FakeWebRecipeRepository(pool = pool),
            profileRepository = FakeUserProfileRepository(profile),
            builder = CuratedDeckBuilder(),
        )

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
    fun `gibt einen leeren Stapel zurueck, wenn das Verzeichnis leer ist`() = runTest {
        val leer = BuildSwipeDeckUseCase(
            webRepository = FakeWebRecipeRepository(pool = DeckPool()),
            profileRepository = FakeUserProfileRepository(UserProfile()),
            builder = CuratedDeckBuilder(),
        )

        assertTrue(leer().isEmpty())
    }

    @Test
    fun `holt den Vorrat einmal pro Stapel`() = runTest {
        val repository = FakeWebRecipeRepository(pool = pool)
        val useCase = BuildSwipeDeckUseCase(
            webRepository = repository,
            profileRepository = FakeUserProfileRepository(UserProfile(country = Country.SCHWEIZ)),
            builder = CuratedDeckBuilder(),
        )

        useCase(size = 10)

        assertEquals(1, repository.deckPoolCalls)
    }
}
