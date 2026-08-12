package ch.rezeptli.app.presentation.multiplayer

import ch.rezeptli.app.domain.deck.CuratedDeckBuilder
import ch.rezeptli.app.domain.deck.DeckEntry
import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedSelectionHolder
import ch.rezeptli.app.domain.multiplayer.SharedSessionState
import ch.rezeptli.app.domain.parser.IngredientTextParser
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.ranking.SourceOrigin
import ch.rezeptli.app.domain.repository.DeckPool
import ch.rezeptli.app.domain.shopping.ShoppingListAggregator
import ch.rezeptli.app.domain.steps.InstructionSplitter
import ch.rezeptli.app.domain.translate.NoTranslation
import ch.rezeptli.app.domain.usecase.AddRecipesToShoppingListUseCase
import ch.rezeptli.app.domain.usecase.BuildSwipeDeckUseCase
import ch.rezeptli.app.domain.usecase.CloseSharedSessionUseCase
import ch.rezeptli.app.domain.usecase.JoinSharedSessionUseCase
import ch.rezeptli.app.domain.usecase.LoadWebRecipeUseCase
import ch.rezeptli.app.domain.usecase.ObserveSharedSessionUseCase
import ch.rezeptli.app.domain.usecase.SaveRecipeUseCase
import ch.rezeptli.app.domain.usecase.SendSharedVotesUseCase
import ch.rezeptli.app.domain.usecase.StartSharedSessionUseCase
import ch.rezeptli.app.fake.FakePairingRepository
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.fake.FakeShoppingListRepository
import ch.rezeptli.app.fake.FakeUserProfileRepository
import ch.rezeptli.app.fake.FakeWebRecipeRepository
import ch.rezeptli.app.util.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class MultiplayerViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    /** Was ein frisch gezogener eigener Vorschlag enthaelt - alle mit Bild, wie gefordert. */
    private val eigeneEintraege = listOf(
        DeckEntry(
            url = "https://a.example/roesti",
            title = "Rösti",
            imageUrl = "https://a.example/roesti.jpg",
            sourceId = "a",
            sourceName = "A",
        ),
        DeckEntry(
            url = "https://a.example/risotto",
            title = "Risotto",
            imageUrl = "https://a.example/risotto.jpg",
            sourceId = "a",
            sourceName = "A",
        ),
        DeckEntry(
            url = "https://a.example/alplermagronen",
            title = "Älplermagronen",
            imageUrl = "https://a.example/alpler.jpg",
            sourceId = "a",
            sourceName = "A",
        ),
    )

    private val gefuellterPool = DeckPool(
        entriesBySource = mapOf("a" to eigeneEintraege),
        origins = mapOf("a" to SourceOrigin("a", Country.SCHWEIZ)),
    )

    /** Das, worueber schon abgestimmt wird, wenn ein Test einer Runde beitritt. */
    private val bestehenderTopf = listOf(
        SharedRecipe(recipeId = 1L, title = "Rösti"),
        SharedRecipe(recipeId = 2L, title = "Risotto"),
        SharedRecipe(recipeId = 3L, title = "Älplermagronen"),
    )
    private val pairing = FakePairingRepository(pool = bestehenderTopf)

    /**
     * Stellt ein ViewModel bereit und beendet danach seine Abfrageschleife.
     *
     * Das ViewModel fragt den Stand der Runde wiederholt ab und hoert erst auf, wenn
     * alle fertig sind. Tests, die genau das absichtlich nicht erreichen - Warten auf
     * die zweite Person etwa -, wuerden `runTest` sonst ewig beschaeftigen: Es wartet
     * am Ende des Testrumpfs darauf, dass keine Coroutine mehr laeuft.
     *
     * Wichtig ist, dass das Aufraeumen *innerhalb* des Rumpfs geschieht. Ein
     * `@AfterEach` kommt zu spaet - da wartet `runTest` bereits.
     */
    private fun TestScope.mitViewModel(
        viewModel: MultiplayerViewModel = createViewModel(),
        block: (MultiplayerViewModel) -> Unit,
    ) {
        try {
            block(viewModel)
        } finally {
            viewModel.onLeave()
            advanceUntilIdle()
        }
    }

    private val selectionHolder = SharedSelectionHolder()
    private val shoppingRepository = FakeShoppingListRepository()

    /**
     * [webRepository] liefert per Voreinstellung einen gefuellten Vorrat, mit dem
     * onHost()/onJoin() ohne Weiteres klappen.
     */
    private fun createViewModel(
        webRepository: FakeWebRecipeRepository = FakeWebRecipeRepository(pool = gefuellterPool),
    ) = MultiplayerViewModel(
        startSession = StartSharedSessionUseCase(pairing),
        selectionHolder = selectionHolder,
        buildDeck = BuildSwipeDeckUseCase(
            webRepository = webRepository,
            profileRepository = FakeUserProfileRepository(UserProfile(country = Country.SCHWEIZ)),
            builder = CuratedDeckBuilder(),
        ),
        loadWebRecipe = LoadWebRecipeUseCase(
            FakeWebRecipeRepository(),
            IngredientTextParser(),
            InstructionSplitter(),
            NoTranslation,
        ),
        saveRecipe = SaveRecipeUseCase(FakeRecipeRepository(), InstructionSplitter()),
        addToShoppingList = AddRecipesToShoppingListUseCase(
            FakeRecipeRepository(),
            shoppingRepository,
            ShoppingListAggregator(),
        ),
        joinSession = JoinSharedSessionUseCase(pairing),
        sendVotes = SendSharedVotesUseCase(pairing),
        closeSession = CloseSharedSessionUseCase(pairing),
        observeSession = ObserveSharedSessionUseCase(pairing),
    )

    @Test
    fun `eine vorbereitete Auswahl geht vor einem frisch gezogenen Vorschlag`() = runTest {
        // So kommt der Party-Modus aus einer Wischrunde: geteilt wird, was dort gefiel.
        val ausDerRunde = listOf(
            SharedRecipe(recipeId = 99L, title = "Wähe aus dem Stapel"),
            SharedRecipe(recipeId = 98L, title = "Polenta aus dem Stapel"),
        )
        selectionHolder.set(ausDerRunde)

        mitViewModel { viewModel ->
            viewModel.onHost()

            assertEquals(
                ausDerRunde.map { it.title },
                pairing.createdSession?.recipes?.map { it.title },
            )
        }
    }

    @Test
    fun `die Auswahl gilt nur fuer eine Runde`() = runTest {
        selectionHolder.set(listOf(SharedRecipe(recipeId = 99L, title = "Einmalig")))

        mitViewModel { viewModel -> viewModel.onHost() }
        mitViewModel { viewModel ->
            viewModel.onHost()

            // Zweite Runde ohne neue Auswahl: wieder frisch aus dem Verzeichnis gezogen.
            assertEquals(eigeneEintraege.size, pairing.createdSession?.recipes?.size)
        }
    }

    @Test
    fun `eroeffnen liefert einen Code und wartet auf die andere Person`() = runTest {
        mitViewModel { viewModel ->
            viewModel.onHost()

            val state = viewModel.uiState.value
            assertEquals(FakePairingRepository.CODE, state.code)
            assertEquals(MultiplayerStep.WARTET_AUF_PERSON, state.step)
            assertTrue(state.isHost)
            assertEquals(eigeneEintraege.size, pairing.createdSession?.recipes?.size)
        }
    }

    @Test
    fun `der eigene Vorschlag zieht nur Rezepte mit Bild`() = runTest {
        mitViewModel { viewModel ->
            viewModel.onHost()

            val geteilt = pairing.createdSession?.recipes.orEmpty()
            assertEquals(
                setOf("Rösti", "Risotto", "Älplermagronen"),
                geteilt.map { it.title }.toSet(),
            )
            assertTrue(geteilt.all { it.imageUrl != null }, "Ohne Bild darf kein Vorschlag mitgehen")
        }
    }

    @Test
    fun `ohne erreichbaren Vorrat meldet das Eroeffnen einen Fehler statt einer leeren Runde`() = runTest {
        mitViewModel(createViewModel(webRepository = FakeWebRecipeRepository())) { viewModel ->
            viewModel.onHost()

            assertEquals(PairingError.NO_RECIPES, viewModel.uiState.value.error)
            assertNull(pairing.createdSession)
        }
    }

    @Test
    fun `beitreten mit falschem Code meldet einen verstaendlichen Fehler`() = runTest {
        mitViewModel { viewModel ->
            viewModel.onCodeInputChange("XXXXXX")
            viewModel.onJoin()

            assertEquals(PairingError.UNKNOWN_CODE, viewModel.uiState.value.error)
            assertEquals(MultiplayerStep.START, viewModel.uiState.value.step)
        }
    }

    @Test
    fun `beitreten mischt den eigenen Vorschlag in den bestehenden Topf`() = runTest {
        mitViewModel { viewModel ->
            viewModel.onCodeInputChange(FakePairingRepository.CODE)
            viewModel.onJoin()

            val state = viewModel.uiState.value
            assertEquals(MultiplayerStep.WISCHEN, state.step)
            assertEquals(bestehenderTopf.size + eigeneEintraege.size, state.pool.size)
            assertTrue(bestehenderTopf.map { it.title }.all { titel -> state.pool.any { it.title == titel } })
            assertFalse(state.isHost)
        }
    }

    @Test
    fun `der Code wird in Grossbuchstaben uebernommen`() = runTest {
        mitViewModel { viewModel ->
            viewModel.onCodeInputChange("abc123")

            assertEquals("ABC123", viewModel.uiState.value.codeInput)
        }
    }

    @Test
    fun `nach dem letzten Rezept gehen alle Stimmen auf einmal raus`() = runTest {
        // Ohne eigenen Vorrat bleibt der Topf beim Beitreten genau die drei
        // vorgegebenen Rezepte - die Stimmenzahl bleibt damit vorhersagbar.
        mitViewModel(createViewModel(webRepository = FakeWebRecipeRepository())) { viewModel ->
            viewModel.onCodeInputChange(FakePairingRepository.CODE)
            viewModel.onJoin()

            viewModel.onSwiped(1L, liked = true)
            viewModel.onSwiped(2L, liked = false)
            assertTrue(pairing.sentVotes.isEmpty(), "Zwischendurch wird nichts gesendet")

            viewModel.onSwiped(3L, liked = true)

            assertEquals(3, pairing.sentVotes.size)
            assertTrue(pairing.markedFinished)
            assertEquals(MultiplayerStep.WARTET_AUF_ENTSCHEIDUNGEN, viewModel.uiState.value.step)
        }
    }

    @Test
    fun `solange nicht alle fertig sind, gibt es keine Treffer zu sehen`() = runTest {
        pairing.state = SharedSessionState(
            code = FakePairingRepository.CODE,
            participants = 2,
            finished = 1,
            allFinished = false,
        )
        mitViewModel { viewModel ->
            viewModel.onCodeInputChange(FakePairingRepository.CODE)
            viewModel.onJoin()
            advanceTimeBy(FIVE_SECONDS)

            val state = viewModel.uiState.value
            assertTrue(state.matches.isEmpty())
            assertFalse(state.step == MultiplayerStep.TREFFER)
        }
    }

    @Test
    fun `sobald alle fertig sind, erscheinen die gemeinsamen Treffer`() = runTest {
        pairing.state = SharedSessionState(
            code = FakePairingRepository.CODE,
            participants = 2,
            finished = 2,
            allFinished = true,
            matches = listOf(SharedRecipe(recipeId = 2L, title = "Risotto")),
        )
        mitViewModel { viewModel ->
            viewModel.onCodeInputChange(FakePairingRepository.CODE)
            viewModel.onJoin()
            advanceTimeBy(FIVE_SECONDS)

            val state = viewModel.uiState.value
            assertEquals(MultiplayerStep.TREFFER, state.step)
            assertEquals(listOf("Risotto"), state.matches.map { it.title })
        }
    }

    @Test
    fun `der Gastgeber startet, sobald jemand beitritt`() = runTest {
        pairing.state = SharedSessionState(
            code = FakePairingRepository.CODE,
            participants = 2,
            finished = 0,
            allFinished = false,
        )
        mitViewModel { viewModel ->
            viewModel.onHost()
            advanceTimeBy(FIVE_SECONDS)

            assertEquals(MultiplayerStep.WISCHEN, viewModel.uiState.value.step)
        }
    }

    @Test
    fun `verlassen schliesst die Runde des Gastgebers beim Dienst`() = runTest {
        mitViewModel { viewModel ->
            viewModel.onHost()

            viewModel.onLeave()

            assertEquals(FakePairingRepository.CODE, pairing.closedCode)
            assertEquals(MultiplayerStep.START, viewModel.uiState.value.step)
            assertEquals("", viewModel.uiState.value.code)
        }
    }

    @Test
    fun `ein Gast schliesst die Runde nicht - sie gehoert ihm nicht`() = runTest {
        mitViewModel { viewModel ->
            viewModel.onCodeInputChange(FakePairingRepository.CODE)
            viewModel.onJoin()

            viewModel.onLeave()

            assertNull(pairing.closedCode)
        }
    }

    @Test
    fun `ohne Verbindung bleibt die Runde beim Start stehen`() = runTest {
        pairing.failWith = PairingError.NO_CONNECTION
        mitViewModel { viewModel ->
            viewModel.onHost()

            assertEquals(PairingError.NO_CONNECTION, viewModel.uiState.value.error)
            assertEquals(MultiplayerStep.START, viewModel.uiState.value.step)
            assertFalse(viewModel.uiState.value.isBusy)
        }
    }

    private companion object {
        const val FIVE_SECONDS = 5_000L
    }
}
