package ch.rezeptli.app.nutrition

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.nutrition.NutritionBasis
import ch.rezeptli.app.domain.nutrition.NutritionFacts
import ch.rezeptli.app.domain.nutrition.NutritionValue
import ch.rezeptli.app.presentation.common.components.NutritionNote
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.swipe.CardInfo
import ch.rezeptli.app.presentation.swipe.RecipeInfoSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kalorien und Eiweiss unter der Zutatenliste - so, wie sie auf dem Bildschirm stehen.
 *
 * Worauf es ankommt: Man sieht der Zahl an, woher sie kommt. Eine Schaetzung traegt "ca."
 * und den Satz dazu; die Angabe einer Rezeptseite nennt die Seite.
 */
@RunWith(AndroidJUnit4::class)
class NutritionNoteTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun text(id: Int, vararg werte: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *werte)

    private fun zeige(werte: NutritionFacts?, quelle: String? = null) {
        composeTestRule.setContent {
            RezeptliTheme(reduceMotion = true) { NutritionNote(facts = werte, sourceName = quelle) }
        }
    }

    @Test
    fun eineSchaetzungTraegtCaUndSagtDassGeschaetztWurde() {
        zeige(NutritionFacts(NutritionValue(520, true), NutritionValue(24, true), NutritionBasis.SERVING))

        composeTestRule.onNodeWithText(text(R.string.naehrwerte_pro_portion)).assertIsDisplayed()
        composeTestRule.onNodeWithText("ca. 520 kcal").assertIsDisplayed()
        composeTestRule.onNodeWithText("ca. 24 g").assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.naehrwerte_geschaetzt)).assertIsDisplayed()
    }

    @Test
    fun dieAngabeDerQuelleStehtOhneCaUndNenntDieSeite() {
        zeige(
            NutritionFacts(NutritionValue(412, false), NutritionValue(24, false), NutritionBasis.SERVING),
            quelle = "Betty Bossi",
        )

        composeTestRule.onNodeWithText("412 kcal").assertIsDisplayed()
        composeTestRule.onNodeWithText("24 g").assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.naehrwerte_quelle_name, "Betty Bossi")).assertIsDisplayed()
    }

    @Test
    fun ohnePortionszahlGiltDieSchaetzungFuersGanzeRezept() {
        zeige(NutritionFacts(NutritionValue(2080, true), NutritionValue(80, true), NutritionBasis.RECIPE))

        composeTestRule.onNodeWithText(text(R.string.naehrwerte_ganzes_rezept)).assertIsDisplayed()
        composeTestRule.onNodeWithText("ca. 2080 kcal").assertIsDisplayed()
        composeTestRule.onNodeWithText(text(R.string.naehrwerte_geschaetzt_ganz)).assertIsDisplayed()
    }

    @Test
    fun laesstSichNichtsSchaetzenStehtDasDa() {
        zeige(null)

        composeTestRule.onNodeWithText(text(R.string.naehrwerte_unbekannt)).assertIsDisplayed()
    }

    @Test
    fun imBlattDerWischkarteStehenDieNaehrwerteUnterDenZutaten() {
        composeTestRule.setContent {
            RezeptliTheme(reduceMotion = true) {
                RecipeInfoSheet(
                    info = CardInfo(
                        title = "Risotto",
                        sourceName = "Quelle B",
                        lines = listOf("300 g Risottoreis", "1 l Bouillon"),
                        nutrition = NutritionFacts(
                            kcal = NutritionValue(412, estimated = false),
                            protein = NutritionValue(9, estimated = true),
                            basis = NutritionBasis.SERVING,
                        ),
                    ),
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("300 g Risottoreis").assertIsDisplayed()
        composeTestRule.onNodeWithText("412 kcal").assertIsDisplayed()
        composeTestRule.onNodeWithText("ca. 9 g").assertIsDisplayed()
        // Gemischt: Die Seite nannte die Kalorien, das Eiweiss ist geschaetzt - und so steht es da.
        composeTestRule.onNodeWithText(text(R.string.naehrwerte_kalorien_quelle)).assertIsDisplayed()
    }
}
