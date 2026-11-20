package ch.rezeptli.app.plan

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.plan.MealLabel
import ch.rezeptli.app.domain.plan.PlanEntry
import ch.rezeptli.app.domain.plan.PlanPlace
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.plan.PlanScreen
import ch.rezeptli.app.presentation.plan.PlanUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Der Hinweis im Wochenplan, wenn eine Mahlzeit nicht mehr zu den Vorlieben passt.
 *
 * Gemeldet war: Pilzgerichte trotz "keine Pilze". Der Plan bleibt stehen, wenn sich die
 * Vorlieben aendern - dann soll wenigstens dastehen, welche Mahlzeit nicht mehr passt.
 */
@RunWith(AndroidJUnit4::class)
class UnfittingMealTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val hinweis: String
        get() = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.plan_passt_nicht)

    private val liste get() = composeTestRule.onNode(hasScrollToNodeAction())

    private fun mahlzeit(tag: Weekday, titel: String, nummer: Long) = PlanEntry(
        day = tag,
        slot = MealSlot.ABEND,
        recipeId = nummer,
        title = titel,
        photoUri = null,
        prepTimeMinutes = 30,
        servings = 2,
        cost = 9.5,
        label = MealLabel.ALLTAG,
    )

    private fun zeige(unpassend: Set<PlanPlace>) {
        composeTestRule.setContent {
            RezeptliTheme(reduceMotion = true) {
                PlanScreen(
                    uiState = PlanUiState(
                        isLoading = false,
                        meals = listOf(
                            mahlzeit(Weekday.MONTAG, "Pilzrisotto", 1L),
                            mahlzeit(Weekday.DIENSTAG, "Rösti", 2L),
                        ),
                        unfitting = unpassend,
                    ),
                    snackbarHostState = SnackbarHostState(),
                    onOpenRecipe = {},
                    onOpenShoppingList = {},
                    onBuildPlan = {},
                    onReplace = { _, _ -> },
                    onRebuildRequest = {},
                    onRebuildDismiss = {},
                )
            }
        }
    }

    @Test
    fun eineMahlzeitDieNichtMehrPasstTraegtDenHinweis() {
        zeige(unpassend = setOf(PlanPlace(Weekday.MONTAG, MealSlot.ABEND)))

        // Die Woche ist eine Liste, die nur baut, was zu sehen ist - also erst hinblaettern.
        liste.performScrollToNode(hasText(hinweis))
        composeTestRule.onNodeWithText(hinweis).assertIsDisplayed()

        // Nur die eine - beim Rösti daneben steht nichts.
        liste.performScrollToNode(hasText("Rösti"))
        composeTestRule.onAllNodesWithText(hinweis).assertCountEquals(
            composeTestRule.onAllNodesWithText("Pilzrisotto").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun passtAllesStehtNirgendsEinHinweis() {
        zeige(unpassend = emptySet())

        liste.performScrollToNode(hasText("Pilzrisotto"))
        composeTestRule.onAllNodesWithText(hinweis).assertCountEquals(0)
        liste.performScrollToNode(hasText("Rösti"))
        composeTestRule.onAllNodesWithText(hinweis).assertCountEquals(0)
    }
}
