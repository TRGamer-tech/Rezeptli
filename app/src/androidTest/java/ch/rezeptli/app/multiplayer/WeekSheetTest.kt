package ch.rezeptli.app.multiplayer

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.plan.PlanPlace
import ch.rezeptli.app.domain.plan.WeekAssignment
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.multiplayer.MatchImport
import ch.rezeptli.app.presentation.multiplayer.MultiplayerActions
import ch.rezeptli.app.presentation.multiplayer.MultiplayerScreen
import ch.rezeptli.app.presentation.multiplayer.MultiplayerStep
import ch.rezeptli.app.presentation.multiplayer.MultiplayerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Woche ueberschreiben" auf einem echten Geraet: der Knopf bei den Treffern und das Blatt,
 * auf dem jedes Gericht seinen Tag bekommt.
 *
 * Die Regeln dahinter (tauschen, weglassen, ersetzen) sind als Logik getestet. Hier geht es
 * darum, dass man sie auch antippen kann: Jeder Tag ist da, traegt seinen Namen fuer die
 * Sprachausgabe und meldet, fuer welches Gericht er gewaehlt wurde.
 */
@RunWith(AndroidJUnit4::class)
class WeekSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val treffer = listOf(
        SharedRecipe(recipeId = 11L, title = "Rösti"),
        SharedRecipe(recipeId = 12L, title = "Risotto"),
        SharedRecipe(recipeId = 13L, title = "Beats Eintopf"),
    )

    private fun text(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val ueberschreiben get() = text(R.string.zuzweit_ueberschreiben)

    private fun abend(tag: Weekday) = PlanPlace(tag, MealSlot.ABEND)

    private fun zeige(
        step: MultiplayerStep,
        woche: WeekAssignment?,
        actions: MultiplayerActions = MultiplayerActions(),
    ) {
        composeTestRule.setContent {
            RezeptliTheme(reduceMotion = true) {
                MultiplayerScreen(
                    uiState = MultiplayerUiState(step = step, matches = treffer, import = MatchImport(week = woche)),
                    actions = actions,
                )
            }
        }
    }

    @Test
    fun beiDenTreffernStehtDerKnopfUndOeffnetDieTageswahl() {
        var gefragt = false
        zeige(
            MultiplayerStep.TREFFER,
            woche = null,
            actions = MultiplayerActions(onOverwriteRequest = { gefragt = true }),
        )

        composeTestRule.onNode(hasText(ueberschreiben) and hasClickAction()).assertIsDisplayed().performClick()

        assertTrue("Der Knopf muss die Tageswahl oeffnen", gefragt)
    }

    @Test
    fun dasBlattZeigtJedenTrefferMitAllenSiebenTagen() {
        val woche = WeekAssignment(
            slots = listOf(MealSlot.ABEND),
            places = mapOf(11L to abend(Weekday.MONTAG), 12L to abend(Weekday.DIENSTAG)),
        )
        zeige(MultiplayerStep.START, woche)

        // Die Liste der Gerichte rollt - auf einem kleinen Bildschirm ist das letzte erst nach dem
        // Blaettern zu sehen. Da sein muessen alle.
        treffer.forEach { composeTestRule.onNodeWithText(it.title).assertExists() }
        composeTestRule.onNodeWithText(treffer.first().title).assertIsDisplayed()
        // Jeder Tag einmal je Gericht - vorgelesen mit vollem Namen, nicht als "Mo".
        Weekday.entries.forEach { tag ->
            composeTestRule.onAllNodesWithContentDescription(tag.label).assertCountEquals(treffer.size)
        }
        // Unter dem Titel steht, wo das Gericht gerade liegt - oder dass es draussen bleibt.
        composeTestRule.onNodeWithText("Montag").assertExists()
        composeTestRule.onNodeWithText("Dienstag").assertExists()
        composeTestRule.onNodeWithText(text(R.string.zuzweit_ueberschreiben_ohne_tag)).assertExists()
        // Was nicht rollt, steht immer da: die Warnung und der Knopf zum Bestaetigen.
        composeTestRule.onNodeWithText(text(R.string.zuzweit_ueberschreiben_warnung)).assertIsDisplayed()
        composeTestRule.onNode(hasText(ueberschreiben) and hasClickAction()).assertIsDisplayed()
    }

    @Test
    fun einTippAufEinenTagMeldetGerichtUndTag() {
        var gewaehlt: Pair<Long, Weekday>? = null
        val woche = WeekAssignment(slots = listOf(MealSlot.ABEND), places = mapOf(11L to abend(Weekday.MONTAG)))
        zeige(
            MultiplayerStep.START,
            woche,
            actions = MultiplayerActions(onOverwriteDay = { gericht, tag -> gewaehlt = gericht to tag }),
        )

        // Der Freitag des zweiten Gerichts.
        composeTestRule.onAllNodesWithContentDescription("Freitag")[1].performScrollTo().performClick()

        assertEquals(12L to Weekday.FREITAG, gewaehlt)
    }

    @Test
    fun mittagOderAbendStehtNurZurWahlWennBeidesGeplantWird() {
        var gewaehlt: Pair<Long, MealSlot>? = null
        val woche = WeekAssignment(slots = MealSlot.entries, places = mapOf(11L to abend(Weekday.MONTAG)))
        zeige(
            MultiplayerStep.START,
            woche,
            actions = MultiplayerActions(onOverwriteSlot = { gericht, zeit -> gewaehlt = gericht to zeit }),
        )

        // Nur das Gericht mit einem Tag fragt nach der Mahlzeit.
        composeTestRule.onNodeWithText(MealSlot.MITTAG.label).performScrollTo().performClick()

        assertEquals(11L to MealSlot.MITTAG, gewaehlt)
    }

    @Test
    fun ohneEinGerichtMitTagLaesstSichNichtsUeberschreiben() {
        zeige(MultiplayerStep.START, WeekAssignment(slots = listOf(MealSlot.ABEND)))

        composeTestRule.onNode(hasText(ueberschreiben) and isNotEnabled()).assertExists()
    }

    @Test
    fun bestaetigenUndAbbrechenMeldenSich() {
        var bestaetigt = false
        var abgebrochen = false
        val woche = WeekAssignment(slots = listOf(MealSlot.ABEND), places = mapOf(11L to abend(Weekday.MONTAG)))
        zeige(
            MultiplayerStep.START,
            woche,
            actions = MultiplayerActions(
                onOverwriteConfirm = { bestaetigt = true },
                onOverwriteDismiss = { abgebrochen = true },
            ),
        )

        composeTestRule.onNode(hasText(ueberschreiben) and hasClickAction()).performClick()
        composeTestRule.onNodeWithText(text(R.string.action_cancel)).performClick()

        assertTrue(bestaetigt)
        assertTrue(abgebrochen)
    }
}
