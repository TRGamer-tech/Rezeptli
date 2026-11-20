package ch.rezeptli.app.presentation.common.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

/**
 * Haelt den Inhalt eines Bildschirms ueber der unteren Leiste - und ueber der Tastatur.
 *
 * Die App zeichnet bis unter die Systemleisten. Seither schiebt Android den Inhalt nicht mehr
 * von selbst nach oben, wenn die Tastatur aufgeht: Sie legte sich ueber das Feld, in das man
 * gerade tippte - beim Vornamen, bei den Abneigungen, beim Code einer gemeinsamen Runde, und
 * am deutlichsten beim Eingabefeld der Einkaufsliste, das ganz unten steht.
 *
 * Darum macht der Inhalt hier selbst Platz: Er endet ueber der Tastatur. Rollbare Bereiche
 * werden dadurch kuerzer und holen das Feld, in dem der Cursor steht, von sich aus ins Bild.
 *
 * [bars] ist, was die Leiste unten (und die Statusleiste oben) schon freihaelt. Der untere
 * Teil gilt fuer alles darin als erledigt: Die Tastatur ueberdeckt die Leiste, also zaehlt
 * nur, was sie darueber hinaus braucht - sonst stuende der Inhalt um die Hoehe der Leiste
 * zu hoch ueber der Tastatur.
 */
fun Modifier.aboveBarsAndKeyboard(bars: PaddingValues): Modifier =
    padding(bars)
        .consumeWindowInsets(PaddingValues(bottom = bars.calculateBottomPadding()))
        .imePadding()

/**
 * Das Geruest der App: unten die Leiste, darueber der Inhalt - und beide wissen von der Tastatur.
 *
 * Der Inhalt bekommt den Platz ueber Leiste und Tastatur ([aboveBarsAndKeyboard]). Die Leiste
 * erfaehrt, ob die Tastatur offen ist, und soll dann verschwinden: Bis Android 14 liegt sie
 * ohnehin hinter der Tastatur. Ab Android 15 waechst ihr unterer Abstand mit der Tastatur mit -
 * sie stiege ueber die Tastatur und naehme dem Inhalt genau den Platz, um den es geht (auf dem
 * Emulator gemessen: 80 px Luecke zwischen Eingabefeld und Tastatur).
 *
 * Geruest und Geraetetest benutzen dieselbe Funktion - der Test misst so, was die App tut.
 */
@Composable
fun KeyboardAwareScaffold(
    bottomBar: @Composable (keyboardOpen: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    // Offen ist die Tastatur, sobald sie Platz beansprucht.
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(
        modifier = modifier,
        bottomBar = { bottomBar(keyboardOpen) },
    ) { bars ->
        content(Modifier.aboveBarsAndKeyboard(bars))
    }
}
