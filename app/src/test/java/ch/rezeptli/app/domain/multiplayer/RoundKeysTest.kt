package ch.rezeptli.app.domain.multiplayer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `selbst erfasste Rezepte zweier Telefone mit derselben Nummer bleiben verschieden`() {
        // Beide Sammlungen beginnen bei 1 - genau daran hat sich frueher ein Rezept ueberschrieben.
        val meins = RoundKeys.forOwn("aBcDeFgHiJkLmNoP", 7)
        val deins = RoundKeys.forOwn("ZyXwVuTsRqPoNmLk", 7)

        assertNotEquals(meins, deins)
        assertTrue(erlaubt.matches(meins), meins)
    }

    @Test
    fun `das eigene Rezept findet nur das Telefon wieder, von dem es stammt`() {
        val schluessel = RoundKeys.forOwn("aBcDeFgHiJkLmNoP", 7)

        assertEquals(7L, RoundKeys.ownRecipeId(schluessel, "aBcDeFgHiJkLmNoP"))
        assertNull(RoundKeys.ownRecipeId(schluessel, "ZyXwVuTsRqPoNmLk"))
        // Ein Rezept aus dem Netz gehoert niemandem allein.
        assertNull(RoundKeys.ownRecipeId(RoundKeys.forUrl("https://beispiel.ch/roesti"), "aBcDeFgHiJkLmNoP"))
    }
}
