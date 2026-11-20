package ch.rezeptli.app.presentation.common.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier

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
