package ch.rezeptli.app.presentation.multiplayer

import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedSelectionHolder
import ch.rezeptli.app.domain.multiplayer.SharedSessionState
import ch.rezeptli.app.domain.usecase.CloseSharedSessionUseCase
import ch.rezeptli.app.domain.usecase.JoinSharedSessionUseCase
import ch.rezeptli.app.domain.usecase.ObserveSharedSessionUseCase
import ch.rezeptli.app.domain.usecase.SendSharedVotesUseCase
import ch.rezeptli.app.domain.usecase.StartSharedSessionUseCase
import ch.rezeptli.app.fake.FakePairingRepository
import ch.rezeptli.app.fake.FakeRecipeRepository
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

    private val rezepte = listOf(
        Recipe(id = 1L, title = "Rösti"),
        Recipe(id = 2L, title = "Risotto"),
        Recipe(id = 3L, title = "Älplermagronen"),
    )

    private val pool = rezepte.map { SharedRecipe(recipeId = it.id, title = it.title) }
    private val recipeRepository = FakeRecipeRepository(rezepte)
    private val pairing = FakePairingRepository(pool = pool)

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
    private fun TestScope.mitViewModel(block: (MultiplayerViewModel) -> Unit) {
        val viewModel = createViewModel()
        try {
            block(viewModel)
        } finally {
            viewModel.onLeave()
            advanceUntilIdle()
        }
    }

    /** Ohne vorbereitete Auswahl - die Runde kommt hier aus der eigenen Sammlung. */
    private val selectionHolder = SharedSelectionHolder()

    private fun createViewModel() = MultiplayerViewModel(
        startSession = StartSharedSessionUseCase(pairing, recipeRepository),
        selectionHolder = selectionHolder,
        joinSession = JoinSharedSessionUseCase(pairing),
        sendVotes = SendSharedVotesUseCase(pairing),
        closeSession = CloseSharedSessionUseCase(pairing),
        observeSession = ObserveSharedSessionUseCase(pairing),
    )

    @Test
    fun `eine vorbereitete Auswahl geht vor der eigenen Sammlung`() = runTest {
        // So kommt der Party-Modus aus einer Wischrunde: geteilt wird, was dort gefiel.
        val ausDerRunde = listOf(
            SharedRecipe(recipeId = 99L, title = "Wähe aus dem Stapel"),
            SharedRecipe(recipeId = 98L, title = "Polenta aus dem Stapel"),
        )
        selectionHolder.set(ausDerRunde)

        mitViewModel { viewModel ->
            viewModel.onHost(RecipeFilter.NONE)

            assertEquals(
                ausDerRunde.map { it.title },
                pairing.createdSession?.recipes?.map { it.title },
            )
        }
    }

    @Test
    fun `die Auswahl gilt nur fuer eine Runde`() = runTest {
        selectionHolder.set(listOf(SharedRecipe(recipeId = 99L, title = "Einmalig")))

        mitViewModel { viewModel -> viewModel.onHost(RecipeFilter.NONE) }
        mitViewModel { viewModel ->
            viewModel.onHost(RecipeFilter.NONE)

            // Zweite Runde ohne neue Auswahl: wieder aus der eigenen Sammlung.
            assertEquals(rezepte.size, pairing.createdSession?.recipes?.size)
        }
    }

    @Test
    fun `eroeffnen liefert einen Code und wartet auf die andere Person`() = runTest {
        mitViewModel { viewModel ->
            viewModel.onHost(RecipeFilter.NONE)

            val state = viewModel.uiState.value
            assertEquals(FakePairingRepository.CODE, state.code)
            assertEquals(MultiplayerStep.WARTET_AUF_PERSON, state.step)
            assertTrue(state.isHost)
            assertEquals(rezepte.size, pairing.createdSession?.recipes?.size)
        }
    }

    @Test
    fun `geteilt werden nur Titel und Bild, keine Zutaten`() = runTest {
        mitViewModel { viewModel ->
            viewModel.onHost(RecipeFilter.NONE)

            val geteilt = pairing.createdSession?.recipes.orEmpty()
            assertEquals(
                setOf("Rösti", "Risotto", "Älplermagronen"),
                geteilt.map { it.title }.toSet(),
            )
            // SharedRecipe hat schlicht kein Feld fuer Zutaten oder Zubereitung.
            assertTrue(geteilt.all { it.sourceUrl == null })
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
    fun `beitreten holt den Rezeptstapel und beginnt sofort`() = runTest {
        mitViewModel { viewModel ->
            viewModel.onCodeInputChange(FakePairingRepository.CODE)
            viewModel.onJoin()

            val state = viewModel.uiState.value
            assertEquals(MultiplayerStep.WISCHEN, state.step)
            assertEquals(pool.size, state.pool.size)
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
        mitViewModel { viewModel ->
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
            viewModel.onHost(RecipeFilter.NONE)
            advanceTimeBy(FIVE_SECONDS)

            assertEquals(MultiplayerStep.WISCHEN, viewModel.uiState.value.step)
        }
    }

    @Test
    fun `verlassen schliesst die Runde des Gastgebers beim Dienst`() = runTest {
        mitViewModel { viewModel ->
            viewModel.onHost(RecipeFilter.NONE)

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
            viewModel.onHost(RecipeFilter.NONE)

            assertEquals(PairingError.NO_CONNECTION, viewModel.uiState.value.error)
            assertEquals(MultiplayerStep.START, viewModel.uiState.value.step)
            assertFalse(viewModel.uiState.value.isBusy)
        }
    }

    private companion object {
        const val FIVE_SECONDS = 5_000L
    }
}
