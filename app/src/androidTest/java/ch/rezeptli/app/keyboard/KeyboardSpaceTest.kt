package ch.rezeptli.app.keyboard

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.presentation.common.components.aboveBarsAndKeyboard
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.multiplayer.MultiplayerActions
import ch.rezeptli.app.presentation.multiplayer.MultiplayerScreen
import ch.rezeptli.app.presentation.multiplayer.MultiplayerStep
import ch.rezeptli.app.presentation.multiplayer.MultiplayerUiState
import ch.rezeptli.app.presentation.plan.DislikePicker
import ch.rezeptli.app.presentation.plan.NameField
import ch.rezeptli.app.presentation.shoppinglist.ShoppingListScreen
import ch.rezeptli.app.presentation.shoppinglist.ShoppingListUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Die Tastatur darf das Feld nicht verdecken, in das gerade getippt wird.
 *
 * Gemeldet war genau das: beim Vornamen, bei den Abneigungen und beim Code einer gemeinsamen
 * Runde. Hier laeuft die echte Tastatur des Geraets, in einem Fenster, das wie die App bis
 * unter die Systemleisten zeichnet. Gemessen wird, wo das Feld steht, sobald die Tastatur
 * offen ist: Seine Unterkante muss ueber ihrer Oberkante liegen.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardSpaceTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /** Baut das Geruest der App nach: Leiste unten, Inhalt darueber - mit oder ohne Platz fuer die Tastatur. */
    private fun zeige(mitPlatz: Boolean = true, inhalt: @Composable () -> Unit) {
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.enableEdgeToEdge()
            // Wie im Manifest der App.
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        composeTestRule.setContent {
            RezeptliTheme(reduceMotion = true) {
                Scaffold(
                    bottomBar = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .height(LEISTE),
                        )
                    },
                ) { leiste: PaddingValues ->
                    val platz = if (mitPlatz) Modifier.aboveBarsAndKeyboard(leiste) else Modifier.padding(leiste)
                    Box(modifier = platz.fillMaxSize()) { inhalt() }
                }
            }
        }
    }

    /** Die Hoehe der Tastatur in Pixeln - 0, solange sie nicht zu sehen ist. */
    private fun tastatur(): Int {
        var hoehe = 0
        composeTestRule.activityRule.scenario.onActivity { activity ->
            val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
            if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                hoehe = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            }
        }
        return hoehe
    }

    private fun fensterHoehe(): Int {
        var hoehe = 0
        composeTestRule.activityRule.scenario.onActivity { hoehe = it.window.decorView.height }
        return hoehe
    }

    /** Wartet, bis die Tastatur offen ist und nicht mehr waechst, und gibt ihre Oberkante zurueck. */
    private fun oberkanteDerTastatur(): Int {
        composeTestRule.waitUntil(timeoutMillis = WARTEN_MS) { tastatur() > 0 }
        var zuletzt = -1
        composeTestRule.waitUntil(timeoutMillis = WARTEN_MS) {
            val jetzt = tastatur()
            val ruhig = jetzt > 0 && jetzt == zuletzt
            zuletzt = jetzt
            ruhig
        }
        composeTestRule.waitForIdle()
        return fensterHoehe() - tastatur()
    }

    /**
     * Wo das Feld wirklich endet - nicht, wo sein sichtbarer Teil endet.
     *
     * `boundsInWindow` schneidet an allem ab, was das Feld verdeckt oder abschneidet: Ein Feld,
     * das ganz unter dem Rand einer Scroll-Liste liegt, endete danach genau an diesem Rand und
     * saehe aus wie "gerade noch sichtbar". Gerechnet wird darum mit Lage und Groesse.
     */
    private fun SemanticsNodeInteraction.unterkante(): Float {
        val knoten = fetchSemanticsNode()
        return knoten.positionInWindow.y + knoten.size.height
    }

    private fun SemanticsNodeInteraction.oberkante(): Float = fetchSemanticsNode().positionInWindow.y

    /** Alles, was man braucht, um eine fehlgeschlagene Messung zu verstehen. */
    private fun lage(feld: SemanticsNodeInteraction): String {
        val knoten = feld.fetchSemanticsNode()
        var fenster = ""
        composeTestRule.activityRule.scenario.onActivity { activity ->
            val decor = activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(decor)
            val inhalt = activity.findViewById<View>(android.R.id.content)
            fenster = "Fenster ${decor.width}x${decor.height} px, Inhalt ${inhalt.height} px, " +
                "Tastatur ${insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom} px, " +
                "Leisten ${insets?.getInsets(WindowInsetsCompat.Type.systemBars())}, " +
                "Dichte ${activity.resources.displayMetrics.density}, Android ${Build.VERSION.SDK_INT}"
        }
        return "$fenster; Feld von ${feld.oberkante()} bis ${feld.unterkante()} px, " +
            "sichtbar ${knoten.boundsInWindow}"
    }

    /** Tippt in das Feld und prueft, dass es danach ganz ueber der Tastatur steht. */
    private fun pruefeFrei(feld: SemanticsNodeInteraction) {
        feld.performClick()
        val oberkante = oberkanteDerTastatur()
        // Das Feld rollt ins Bild, sobald der Platz kleiner wird - das darf einen Augenblick dauern.
        runCatching {
            composeTestRule.waitUntil(timeoutMillis = RUHE_MS) { feld.unterkante() <= oberkante }
        }
        assertTrue(
            "Die Tastatur beginnt bei $oberkante px und verdeckt das Feld. ${lage(feld)}",
            feld.unterkante() <= oberkante,
        )
        assertTrue("Das Feld ist oben aus dem Bild gerutscht. ${lage(feld)}", feld.oberkante() >= 0f)
        feld.assertIsDisplayed()
        // Und es laesst sich hineinschreiben, waehrend man es sieht.
        feld.performTextInput("Pilze")
        composeTestRule.waitForIdle()
        assertTrue("Nach dem Tippen verdeckt: ${lage(feld)}", feld.unterkante() <= oberkante)
    }

    @Test
    fun derCodeEinerGemeinsamenRundeBleibtSichtbar() {
        zeige {
            var code by remember { mutableStateOf("") }
            MultiplayerScreen(
                uiState = MultiplayerUiState(step = MultiplayerStep.START, codeInput = code),
                actions = MultiplayerActions(onCodeInputChange = { code = it }),
            )
        }

        val feld = composeTestRule.onNode(hasSetTextAction())
        feld.performScrollTo()
        pruefeFrei(feld)
    }

    @Test
    fun vornameUndEigeneAbneigungBleibenSichtbar() {
        zeige {
            var name by remember { mutableStateOf("") }
            var eigene by remember { mutableStateOf(emptyList<String>()) }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                // Wie auf der Vorlieben-Seite: Die Felder stehen weit unten auf einer langen Seite.
                repeat(FUELLZEILEN) { Text("Zeile $it", modifier = Modifier.height(48.dp)) }
                NameField(name = name, onChange = { name = it })
                repeat(FUELLZEILEN) { Text("Weiter $it", modifier = Modifier.height(48.dp)) }
                DislikePicker(
                    selected = setOf(Dislike.PILZE),
                    custom = eigene,
                    onToggle = {},
                    onAddCustom = { eigene = eigene + it },
                    onRemoveCustom = { eigene = eigene - it },
                )
                repeat(FUELLZEILEN) { Text("Ende $it", modifier = Modifier.height(48.dp)) }
            }
        }

        val abneigung = composeTestRule.onNode(hasSetTextAction() and hasText(text(R.string.abneigung_eigene)))
        abneigung.performScrollTo()
        pruefeFrei(abneigung)

        val vorname = composeTestRule.onNode(hasSetTextAction() and hasText(text(R.string.onboarding_vorname)))
        vorname.performScrollTo()
        pruefeFrei(vorname)
    }

    @Test
    fun dasFeldDerEinkaufslisteGanzUntenBleibtSichtbar() {
        zeige { Einkaufsliste() }

        pruefeFrei(composeTestRule.onNode(hasSetTextAction()))
    }

    /**
     * Die Gegenprobe - der gemeldete Fehler, nachgestellt: Ohne den eigens gemachten Platz
     * liegt die Tastatur ueber dem Feld. Schlaegt dieser Test eines Tages fehl, macht Android
     * den Platz inzwischen selbst, und [aboveBarsAndKeyboard] ist zu ueberdenken.
     */
    @Test
    fun ohneDenPlatzVerdecktDieTastaturDasFeld() {
        zeige(mitPlatz = false) { Einkaufsliste() }

        val feld = composeTestRule.onNode(hasSetTextAction())
        feld.performClick()
        val oberkante = oberkanteDerTastatur()

        assertTrue(
            "Erwartet war ein verdecktes Feld, die Tastatur beginnt bei $oberkante px. ${lage(feld)}",
            feld.unterkante() > oberkante,
        )
    }

    @Composable
    private fun Einkaufsliste() {
        var eingabe by remember { mutableStateOf("") }
        ShoppingListScreen(
            uiState = ShoppingListUiState(isLoading = false, newItemName = eingabe),
            snackbarHostState = remember { SnackbarHostState() },
            onCopy = {},
            onShare = {},
            onCheckedChange = { _, _ -> },
            onDelete = {},
            onNewItemNameChange = { eingabe = it },
            onAddItem = {},
            onRemoveChecked = {},
            onClearRequest = {},
            onClearDismiss = {},
            onClearConfirm = {},
        )
    }

    private companion object {
        val LEISTE = 80.dp
        const val FUELLZEILEN = 12
        const val WARTEN_MS = 20_000L
        const val RUHE_MS = 5_000L
    }
}
