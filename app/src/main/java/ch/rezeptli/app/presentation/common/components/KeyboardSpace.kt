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
 * [bars] ist, was das Geruest schon freihaelt: unten die Leiste, oben die Statusleiste. Das
 * gilt fuer alles darin als erledigt - auf jeder Seite:
 *
 * - Unten ueberdeckt die Tastatur die Leiste, also zaehlt nur, was sie darueber hinaus braucht.
 *   Sonst stuende der Inhalt um die Hoehe der Leiste zu hoch ueber der Tastatur.
 * - Oben zoege sonst jede Kopfzeile die Statusleiste ein zweites Mal ab. So war es: Auf dem
 *   Emulator begann die Kopfzeile 24 px unter der Statusleiste und war selbst noch einmal 24 px
 *   hoeher - ein leerer Streifen in Hoehe der Statusleiste auf jedem Bildschirm.
 */
fun Modifier.aboveBarsAndKeyboard(bars: PaddingValues): Modifier =
    padding(bars)
        .consumeWindowInsets(bars)
        .imePadding()

/**
 * Das Geruest der App: unten die Leiste, darueber der Inhalt - und beide wissen von der Tastatur.
 *
 * Der Inhalt bekommt den Platz ueber Leiste und Tastatur ([aboveBarsAndKeyboard]). Die Leiste
 * erfaehrt, ob die Tastatur offen ist, und soll dann verschwinden: Hinter der Tastatur nuetzt
 * sie nichts, und sie kann ihr im Weg stehen. In einem Testfenster mit Android 15 wuchs ihr
 * unterer Abstand mit der Tastatur mit - sie stieg ueber die Tastatur und nahm dem Inhalt
 * genau den Platz, um den es geht (gemessen: 80 px Luecke zwischen Eingabefeld und Tastatur).
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
