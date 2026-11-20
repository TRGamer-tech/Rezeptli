package ch.rezeptli.app.keyboard

import android.os.Build
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import ch.rezeptli.app.presentation.MainActivity
import ch.rezeptli.app.presentation.common.components.KeyboardAwareScaffold
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Die Tastatur darf das Feld nicht verdecken, in das gerade getippt wird.
 *
 * Gemeldet war genau das: beim Vornamen, bei den Abneigungen und beim Code einer gemeinsamen
 * Runde. Hier laeuft die echte Tastatur des Geraets im echten Fenster der App ([MainActivity]).
 * Gemessen wird, wo das Feld steht, sobald die Tastatur offen ist: Seine Unterkante muss ueber
 * ihrer Oberkante liegen.
 *
 * Ein nachgebautes Fenster taugt dafuer nicht. Es verhielt sich anders als das der App: Auf
 * Android 14 schob das System es als Ganzes nach oben, und der Fehler schien dort nicht zu
 * bestehen; ausserdem hatte es eine Titelleiste, die die App nicht hat.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardSpaceTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /** Was Compose an Abstaenden sieht - fuer die Meldung, wenn eine Messung fehlschlaegt. */
    private var abstaende = ""

    /**
     * Zeigt [inhalt] im Geruest der App ([KeyboardAwareScaffold]) - oder, fuer die Gegenprobe,
     * so, wie die App vorher gebaut war: Leiste unten, Inhalt darueber, kein Platz fuer die Tastatur.
     */
    private fun zeige(mitPlatz: Boolean = true, inhalt: @Composable () -> Unit) {
        // Das Fenster ist das der App: ihr Thema, ihr Eintrag im Manifest, ihr Zeichnen bis unter
        // die Systemleisten. Nur der Inhalt wird ersetzt - ueber die Activity selbst, denn die
        // Testregel setzt keinen Inhalt, wo schon einer steht.
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                RezeptliTheme(reduceMotion = true) {
                    Abstaende()
                    if (mitPlatz) {
                        KeyboardAwareScaffold(
                            bottomBar = { tastaturOffen -> if (!tastaturOffen) Leiste() },
                        ) { platz ->
                            Box(modifier = platz.fillMaxSize()) { inhalt() }
                        }
                    } else {
                        Scaffold(bottomBar = { Leiste() }) { leiste: PaddingValues ->
                            Box(
                                modifier = Modifier
                                    .padding(leiste)
                                    .fillMaxSize(),
                            ) { inhalt() }
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        // Gemessen wird von der geschlossenen Tastatur aus.
        composeTestRule.waitUntil(timeoutMillis = WARTEN_MS) { tastatur() == 0 }
    }

    /** Steht fuer die Leiste der App: so hoch wie sie, und wie sie ueber der Navigationsleiste. */
    @Composable
    private fun Leiste() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(LEISTE),
        )
    }

    /** Merkt sich, was Compose gerade an Abstaenden sieht. */
    @Composable
    private fun Abstaende() {
        val dichte = LocalDensity.current
        val gesehen = "Status ${WindowInsets.statusBars.getTop(dichte)}, " +
            "Navigation ${WindowInsets.navigationBars.getBottom(dichte)}, " +
            "Tastatur ${WindowInsets.ime.getBottom(dichte)}, " +
            "Titelleiste ${WindowInsets.captionBar.getTop(dichte)}/${WindowInsets.captionBar.getBottom(dichte)}, " +
            "sicher ${WindowInsets.safeDrawing.getTop(dichte)}/${WindowInsets.safeDrawing.getBottom(dichte)}"
        SideEffect { abstaende = gesehen }
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

    /** Die Hoehe der Statusleiste in Pixeln, wie das Fenster sie meldet. */
    private fun statusleiste(): Int {
        var hoehe = 0
        composeTestRule.activityRule.scenario.onActivity { activity ->
            val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
            hoehe = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        }
        return hoehe
    }

    private fun fensterHoehe(): Int {
        var hoehe = 0
        composeTestRule.activityRule.scenario.onActivity { hoehe = it.window.decorView.height }
        return hoehe
    }

    /**
     * Tippt ins Feld, bis die Tastatur aufgeht, und gibt ihre Oberkante zurueck.
     *
     * Ein Tipp genuegt nicht immer: Gleich nach dem Start kann das Startbild der App noch ueber
     * dem Fenster liegen und ihn abfangen.
     */
    private fun oeffneTastatur(feld: SemanticsNodeInteraction): Int {
        repeat(VERSUCHE) {
            feld.performClick()
            val offen = runCatching {
                composeTestRule.waitUntil(timeoutMillis = KURZ_MS) { tastatur() > 0 }
            }.isSuccess
            if (offen) return oberkanteDerTastatur()
        }
        return oberkanteDerTastatur()
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
        // Vom Feld nach aussen: wo jede Ebene beginnt und wie hoch sie ist. Daran zeigt sich,
        // welche Ebene dem Feld den Platz nimmt.
        val ebenen = generateSequence(knoten.parent) { it.parent }
            .joinToString(" < ") { "${it.positionInWindow.y.toInt()}+${it.size.height}" }
        return "$fenster; Feld von ${feld.oberkante()} bis ${feld.unterkante()} px, " +
            "sichtbar ${knoten.boundsInWindow}, platziert ${knoten.layoutInfo.isPlaced}; Ebenen $ebenen; " +
            "Compose sieht: $abstaende"
    }

    /** Tippt in das Feld und prueft, dass es danach ganz ueber der Tastatur steht. */
    private fun pruefeFrei(feld: SemanticsNodeInteraction) {
        val oberkante = oeffneTastatur(feld)
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

        val feld = composeTestRule.onNode(hasSetTextAction())
        val titel = composeTestRule.onNodeWithText(text(R.string.shopping_title))
        val titelVorher = titel.fetchSemanticsNode().positionInWindow.y
        val abstaendeVorher = abstaende
        pruefeFrei(feld)

        // Und es sitzt direkt ueber der Tastatur: Der Platz wird einmal gemacht, nicht doppelt.
        // Zwischen Feld und Tastatur liegt nur der Rand der Zeile.
        val luft = oberkanteDerTastatur() - feld.unterkante()
        val erlaubt = with(composeTestRule.density) { (ZEILENRAND + SPIEL).toPx() }
        assertTrue(
            "Zwischen Feld und Tastatur sind $luft px frei, erlaubt sind $erlaubt. ${lage(feld)}",
            luft <= erlaubt,
        )

        // Oben bleibt alles, wo es war: Die Tastatur schiebt den Bildschirm nicht nach oben weg.
        titel.assertIsDisplayed()
        val titelNachher = titel.fetchSemanticsNode().positionInWindow.y
        assertEquals(
            "Der Titel hat sich mit der Tastatur bewegt. Vorher sah Compose: $abstaendeVorher. ${lage(feld)}",
            titelVorher,
            titelNachher,
            1f,
        )
    }

    /**
     * Auch oben wird der Platz nur einmal gemacht: Die Kopfzeile steht direkt unter der
     * Statusleiste, ihr Titel in der Mitte ihrer Hoehe.
     */
    @Test
    fun dieKopfzeileStehtDirektUnterDerStatusleiste() {
        zeige { Einkaufsliste() }

        val titel = composeTestRule.onNodeWithText(text(R.string.shopping_title))
        val knoten = titel.fetchSemanticsNode()
        val mitte = knoten.positionInWindow.y + knoten.size.height / 2f
        val erwartet = statusleiste() + with(composeTestRule.density) { (KOPFZEILE / 2).toPx() }
        val spiel = with(composeTestRule.density) { SPIEL.toPx() }
        assertEquals(
            "Die Mitte des Titels liegt bei $mitte px, erwartet waren $erwartet " +
                "(Statusleiste ${statusleiste()} px). ${lage(titel)}",
            erwartet,
            mitte,
            spiel,
        )
    }

    /**
     * Die Gegenprobe - der gemeldete Fehler, nachgestellt: Ohne den eigens gemachten Platz ist
     * das Feld bei offener Tastatur nicht zu sehen.
     *
     * Schlaegt sie fehl, macht Android den Platz wieder selbst, und [aboveBarsAndKeyboard] ist
     * zu ueberdenken.
     */
    @Test
    fun ohneDenPlatzIstDasFeldBeiOffenerTastaturNichtZuSehen() {
        zeige(mitPlatz = false) { Einkaufsliste() }

        val feld = composeTestRule.onNode(hasSetTextAction())
        val oberkante = oeffneTastatur(feld)

        val ganzZuSehen = feld.unterkante() > feld.oberkante() &&
            feld.oberkante() >= 0f &&
            feld.unterkante() <= oberkante
        assertFalse(
            "Erwartet war ein Feld, das man nicht sieht; die Tastatur beginnt bei $oberkante px. ${lage(feld)}",
            ganzZuSehen,
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

        /** Der Rand um die Eingabezeile der Einkaufsliste - und etwas Spiel fuer Rundungen. */
        val ZEILENRAND = 16.dp
        val SPIEL = 8.dp

        /** So hoch ist die Kopfzeile der Bildschirme. */
        val KOPFZEILE = 64.dp
        const val FUELLZEILEN = 12
        const val WARTEN_MS = 20_000L
        const val KURZ_MS = 4_000L
        const val VERSUCHE = 4
        const val RUHE_MS = 5_000L
    }
}
