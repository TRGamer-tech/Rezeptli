package ch.rezeptli.app.domain.multiplayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Woran zwei Telefone dasselbe Rezept wiedererkennen - und zwei verschiedene auseinanderhalten. */
class RoundKeysTest {
    private val erlaubt = Regex("^[A-Za-z0-9:_-]+$")

    @Test
    fun `dieselbe Adresse ergibt auf beiden Telefonen denselben Schluessel`() {
        val eins = RoundKeys.forUrl("https://www.beispiel.ch/rezepte/roesti/")
        val zwei = RoundKeys.forUrl("http://beispiel.ch/rezepte/roesti?utm_source=teilen")

        // Mit oder ohne "www", mit Parametern vom Teilen: Es ist dasselbe Rezept.
        assertEquals(eins, zwei)
        assertNotEquals(eins, RoundKeys.forUrl("https://www.beispiel.ch/rezepte/risotto"))
    }

    @Test
    fun `der Schluessel ist kurz und besteht nur aus Zeichen, die der Dienst annimmt`() {
        val schluessel = RoundKeys.forUrl("https://www.beispiel.ch/rezepte/älplermagronen-mit-apfelmus?seite=2#zutaten")

        assertEquals(17, schluessel.length)
        assertTrue(erlaubt.matches(schluessel), schluessel)
    }

    @Test
    fun `ein selbst erfasstes Rezept hat auf beiden Telefonen denselben Schluessel`() {
        // Hat die eine Person es von der anderen uebernommen, liegt es sonst zweimal im Stapel.
        val meins = RoundKeys.forTitle("Omas Gratin")
        val deins = RoundKeys.forTitle("  omas   GRATIN ")

        assertEquals(meins, deins)
        assertEquals(17, meins.length)
        assertTrue(erlaubt.matches(meins), meins)
    }

    @Test
    fun `verschiedene Titel und ein Rezept aus dem Netz bleiben auseinander`() {
        assertNotEquals(RoundKeys.forTitle("Omas Gratin"), RoundKeys.forTitle("Omas Hackbraten"))
        // Ein Titel, der zufaellig wie eine Adresse aussieht, ist trotzdem kein Rezept aus dem Netz.
        assertNotEquals(RoundKeys.forTitle("beispiel.ch/roesti"), RoundKeys.forUrl("https://beispiel.ch/roesti"))
    }
}
