package ch.rezeptli.app.multiplayer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.multiplayer.MatchAction
import ch.rezeptli.app.presentation.multiplayer.MatchImport
import ch.rezeptli.app.presentation.multiplayer.MultiplayerActions
import ch.rezeptli.app.presentation.multiplayer.MultiplayerScreen
import ch.rezeptli.app.presentation.multiplayer.MultiplayerStep
import ch.rezeptli.app.presentation.multiplayer.MultiplayerUiState
import ch.rezeptli.app.presentation.multiplayer.PlanSummary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Fertig" bei den Treffern einer gemeinsamen Runde.
 *
 * Gemeldet war: Wer die Treffer nur in seine Rezepte nimmt, hat danach keinen Knopf, um zu
 * sagen "das war's". Den gibt es jetzt - und er fragt nur nach, wenn mit den Treffern noch
 * gar nichts geschehen ist, denn dann waeren sie mit dem Gehen verloren.
 */
@RunWith(AndroidJUnit4::class)
class DoneButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val treffer = listOf(
        SharedRecipe(recipeId = 11L, title = "Rösti"),
        SharedRecipe(recipeId = 12L, title = "Risotto"),
    )

    private var gegangen = 0
    private var neueRunden = 0

    private fun text(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun mehrzahl(id: Int, anzahl: Int): String =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext.resources
            .getQuantityString(id, anzahl, anzahl)

    private val fertig get() = text(R.string.zuzweit_fertig)
    private val frage get() = text(R.string.zuzweit_verwerfen_titel)

    private fun zeige(stand: MatchImport = MatchImport(), matches: List<SharedRecipe> = treffer) {
        composeTestRule.setContent {
            RezeptliTheme(reduceMotion = true) {
                MultiplayerScreen(
                    uiState = MultiplayerUiState(step = MultiplayerStep.TREFFER, matches = matches, import = stand),
                    actions = MultiplayerActions(
                        onDone = { gegangen += 1 },
                        onBack = { gegangen += 1 },
                        onNewRound = { neueRunden += 1 },
                    ),
                )
            }
        }
    }

    @Test
    fun nachDemBehaltenIstFertigEinfachFertig() {
        zeige(MatchImport(kept = mapOf(11L to 1L, 12L to 2L), keepTried = true))

        composeTestRule
            .onNodeWithText(fertig)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeTestRule.onNodeWithText(frage).assertDoesNotExist()
        assertEquals(1, gegangen)
    }

    @Test
    fun nachDemEinplanenIstFertigEinfachFertig() {
        zeige(MatchImport(plan = PlanSummary(overwritten = 2)))

        composeTestRule.onNodeWithText(fertig).performScrollTo().performClick()

        composeTestRule.onNodeWithText(frage).assertDoesNotExist()
        assertEquals(1, gegangen)
    }

    @Test
    fun ohneDassEtwasGeschehenIstFragtFertigNach() {
        zeige()

        composeTestRule.onNodeWithText(fertig).performScrollTo().performClick()

        // Noch ist niemand gegangen - erst die Frage, mit der Zahl der Treffer, die verloren gingen.
        assertEquals(0, gegangen)
        composeTestRule.onNodeWithText(frage).assertIsDisplayed()
        composeTestRule.onNodeWithText(mehrzahl(R.plurals.zuzweit_verwerfen_text, 2)).assertIsDisplayed()

        composeTestRule.onNodeWithText(text(R.string.zuzweit_verwerfen_bleiben)).performClick()
        composeTestRule.onNodeWithText(frage).assertDoesNotExist()
        assertEquals(0, gegangen)

        composeTestRule.onNodeWithText(fertig).performScrollTo().performClick()
        composeTestRule.onNodeWithText(text(R.string.zuzweit_verwerfen_gehen)).performClick()
        assertEquals(1, gegangen)
    }

    @Test
    fun auchZurueckUndNeueRundeFragenBevorDieTrefferVerschwinden() {
        zeige()

        Espresso.pressBack()
        composeTestRule.onNodeWithText(frage).assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.zuzweit_verwerfen_bleiben)).performClick()
        assertEquals(0, gegangen)

        composeTestRule.onNodeWithText(text(R.string.zuzweit_neue_runde)).performScrollTo().performClick()
        composeTestRule.onNodeWithText(frage).assertIsDisplayed()
        assertEquals(0, neueRunden)
        composeTestRule.onNodeWithText(text(R.string.zuzweit_verwerfen_gehen)).performClick()
        assertEquals(1, neueRunden)
        assertEquals(0, gegangen)
    }

    @Test
    fun waehrendGeholtWirdLaesstSichNichtGehen() {
        zeige(MatchImport(running = MatchAction.KEEP, progress = 1, total = 2))

        composeTestRule.onNodeWithText(fertig).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun ohneTrefferGibtEsNichtsZuVerlieren() {
        zeige(matches = emptyList())

        composeTestRule.onNodeWithText(fertig).assertIsDisplayed().performClick()

        composeTestRule.onNodeWithText(frage).assertDoesNotExist()
        assertEquals(1, gegangen)
    }
}
