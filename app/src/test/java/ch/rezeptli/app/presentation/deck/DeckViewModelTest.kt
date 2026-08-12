package ch.rezeptli.app.presentation.deck

import ch.rezeptli.app.domain.deck.CuratedDeckBuilder
import ch.rezeptli.app.domain.deck.DeckEntry
import ch.rezeptli.app.domain.multiplayer.SharedSelectionHolder
import ch.rezeptli.app.domain.parser.IngredientTextParser
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.ranking.SourceOrigin
import ch.rezeptli.app.domain.repository.DeckPool
import ch.rezeptli.app.domain.steps.InstructionSplitter
import ch.rezeptli.app.domain.translate.NoTranslation
import ch.rezeptli.app.domain.usecase.BuildSwipeDeckUseCase
import ch.rezeptli.app.domain.usecase.LoadWebRecipeUseCase
import ch.rezeptli.app.domain.usecase.SaveRecipeUseCase
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.fake.FakeUserProfileRepository
import ch.rezeptli.app.fake.FakeWebRecipeRepository
import ch.rezeptli.app.util.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Deckt die beiden Verhaltensregeln ab, die auf dem Geraet nicht offensichtlich waren:
 * Karten ohne Bild werden nie gezeigt, und eine Runde ohne Ziel endet nur auf Wunsch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val mitBild = DeckEntry(
        url = "https://a.example/mit-bild",
        title = "Hat ein Bild",
        imageUrl = "https://a.example/bild.jpg",
        sourceId = "a",
        sourceName = "A",
    )
    private val ohneBildAberNachladbar = DeckEntry(
        url = "https://a.example/ohne-bild-nachladbar",
        title = "Bild kommt nach",
        sourceId = "a",
        sourceName = "A",
    )
    private val ohneBildUndUnrettbar = DeckEntry(
        url = "https://a.example/ohne-bild-nie",
        title = "Kein Bild zu holen",
        sourceId = "a",
        sourceName = "A",
    )

    private val pool = DeckPool(
        entriesBySource = mapOf(
            "a" to listOf(mitBild, ohneBildAberNachladbar, ohneBildUndUnrettbar),
        ),
        origins = mapOf("a" to SourceOrigin("a", Country.SCHWEIZ)),
    )

    private fun createViewModel(
        webRepository: FakeWebRecipeRepository = FakeWebRecipeRepository(pool = pool).apply {
            cardImagesByUrl = mapOf(ohneBildAberNachladbar.url to "https://a.example/nachgeladen.jpg")
        },
        recipeRepository: FakeRecipeRepository = FakeRecipeRepository(),
    ) = DeckViewModel(
        buildDeck = BuildSwipeDeckUseCase(
            webRepository = webRepository,
            profileRepository = FakeUserProfileRepository(UserProfile(country = Country.SCHWEIZ)),
            builder = CuratedDeckBuilder(),
        ),
        selectionHolder = SharedSelectionHolder(),
        webRepository = webRepository,
        loadWebRecipe = LoadWebRecipeUseCase(
            webRepository,
            IngredientTextParser(),
            InstructionSplitter(),
            NoTranslation,
        ),
        saveRecipe = SaveRecipeUseCase(recipeRepository, InstructionSplitter()),
    )

    @Test
    fun `zeigt nur Karten mit Bild - fehlende werden nachgeladen oder fallen heraus`() = runTest {
        val viewModel = createViewModel()

        viewModel.onStart()
        val state = viewModel.uiState.value
        val sichtbareTitel = state.remainingCards.map { it.title }

        assertTrue("Hat ein Bild" in sichtbareTitel)
        assertTrue("Bild kommt nach" in sichtbareTitel, "Ein nachladbares Bild sollte die Karte sichtbar machen")
        assertFalse("Kein Bild zu holen" in sichtbareTitel, "Ohne Bild darf eine Karte nie erscheinen")
        assertTrue(
            state.cards.none { it.url == ohneBildUndUnrettbar.url },
            "Eine Karte ohne erreichbares Bild soll aus dem Stapel fallen, nicht nur versteckt sein",
        )
    }

    @Test
    fun `endlose Runde endet nicht von selbst`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTargetChange(null)

        viewModel.onStart()
        val karten = viewModel.uiState.value.cards
            .toList()
        karten.forEach { eintrag -> viewModel.onSwiped(eintrag, liked = true) }

        assertEquals(DeckStep.WISCHEN, viewModel.uiState.value.step, "Ohne Ziel darf nichts automatisch beenden")
    }

    @Test
    fun `Fertig-Knopf beendet die endlose Runde`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTargetChange(null)
        viewModel.onStart()

        viewModel.onFinishEarly()

        assertEquals(DeckStep.FERTIG, viewModel.uiState.value.step)
    }

    @Test
    fun `Runde mit Ziel endet weiterhin von selbst`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTargetChange(1)
        viewModel.onStart()

        val ersteSichtbare = viewModel.uiState.value.cards
            .first { it.imageUrl != null }
        viewModel.onSwiped(ersteSichtbare, liked = true)

        assertEquals(DeckStep.FERTIG, viewModel.uiState.value.step)
    }
}
