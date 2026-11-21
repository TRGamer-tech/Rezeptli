package ch.rezeptli.app.swipe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.swipe.SwipeSetupScreen
import ch.rezeptli.app.presentation.swipe.SwipeSetupUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Bereit zum Swipen?" mit einer Sammlung, die hunderte Schlagworte mitbringt.
 *
 * Gemeldet war eine Wand aus Chips. Zu sehen sind jetzt die meistbenutzten; der Rest kommt
 * auf Wunsch - und was angewaehlt ist, verschwindet beim Einklappen nicht.
 */
@RunWith(AndroidJUnit4::class)
class TagFilterChipsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun text(id: Int, vararg werte: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *werte)

    /** Vierzig Schlagworte, das meistbenutzte zuerst. */
    private val tags = (1..40).map { "Stichwort %02d".format(it) }

    private var gewaehlt: RecipeFilter = RecipeFilter()

    private fun zeige() {
        composeTestRule.setContent {
            var auswahl by remember { mutableStateOf(emptySet<String>()) }
            RezeptliTheme(reduceMotion = true) {
                SwipeSetupScreen(
                    uiState = SwipeSetupUiState(
                        availableTags = tags,
                        selectedTags = auswahl,
                        matchingRecipeCount = 12,
                        isCounting = false,
                    ),
                    onBack = {},
                    onToggleTag = { tag ->
                        auswahl = if (tag in auswahl) auswahl - tag else auswahl + tag
                        gewaehlt = RecipeFilter(tags = auswahl)
                    },
                    onMaxPrepTimeChange = {},
                    onResetFilter = {},
                    onStart = {},
                    onStartTogether = {},
                )
            }
        }
    }

    @Test
    fun zuerstStehenNurDieMeistbenutztenDa() {
        zeige()

        composeTestRule.onNodeWithText("Stichwort 01").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stichwort 08").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Stichwort 09").assertDoesNotExist()
        composeTestRule.onNodeWithText("Stichwort 40").assertDoesNotExist()
        composeTestRule.onNodeWithText(text(R.string.tags_alle_anzeigen, 40)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aufWunschKommenAlleUndLassenSichWiederEinklappen() {
        zeige()

        composeTestRule.onNodeWithText(text(R.string.tags_alle_anzeigen, 40)).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Stichwort 40").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithText(text(R.string.tags_weniger_anzeigen)).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Stichwort 40").assertDoesNotExist()
        composeTestRule.onNodeWithText(text(R.string.tags_alle_anzeigen, 40)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun wasAngewaehltIstBleibtBeimEinklappenStehen() {
        zeige()

        composeTestRule.onNodeWithText(text(R.string.tags_alle_anzeigen, 40)).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Stichwort 33").performScrollTo().performClick()
        composeTestRule.onNodeWithText(text(R.string.tags_weniger_anzeigen)).performScrollTo().performClick()

        composeTestRule.onNodeWithText("Stichwort 33").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Stichwort 32").assertDoesNotExist()
        assertEquals(setOf("Stichwort 33"), gewaehlt.tags)
    }
}
