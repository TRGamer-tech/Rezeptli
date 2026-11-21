package ch.rezeptli.app.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TagShortlistTest {
    /** Dreissig Schlagworte, das meistbenutzte zuerst: "tag01", "tag02", ... */
    private val viele = (1..30).map { "tag%02d".format(it) }

    @Test
    fun `eine lange Liste zeigt eingeklappt nur die meistbenutzten`() {
        val liste = TagShortlist.of(byUse = viele, selected = emptySet(), expanded = false)

        assertEquals(viele.take(TagShortlist.COLLAPSED), liste.visible)
        assertEquals(30, liste.total)
        assertTrue(liste.canToggle)
    }

    @Test
    fun `aufgeklappt steht alles da - nach dem Alphabet`() {
        val nachGebrauch = listOf("Suppe", "asiatisch", "Betty Bossi", "vegan") + viele
        val liste = TagShortlist.of(byUse = nachGebrauch, selected = emptySet(), expanded = true)

        assertEquals(nachGebrauch.size, liste.visible.size)
        assertEquals(listOf("asiatisch", "Betty Bossi", "Suppe"), liste.visible.take(3))
        assertEquals("vegan", liste.visible.last())
        assertTrue(liste.canToggle)
    }

    @Test
    fun `was angewaehlt ist bleibt eingeklappt sichtbar`() {
        val liste = TagShortlist.of(byUse = viele, selected = setOf("tag03", "tag27"), expanded = false)

        // Das Angewaehlte von weiter hinten kommt dazu - ein Filter, der wirkt, muss zu sehen sein.
        assertEquals(viele.take(TagShortlist.COLLAPSED) + "tag27", liste.visible)
    }

    @Test
    fun `eine kurze Liste wird nicht eingeklappt`() {
        val wenige = viele.take(TagShortlist.COLLAPSED)
        val liste = TagShortlist.of(byUse = wenige, selected = emptySet(), expanded = false)

        assertEquals(wenige, liste.visible)
        assertFalse(liste.canToggle)
    }

    @Test
    fun `fuer zwei weitere lohnt das Einklappen nicht`() {
        val knappMehr = viele.take(TagShortlist.COLLAPSED + 2)
        val liste = TagShortlist.of(byUse = knappMehr, selected = emptySet(), expanded = false)

        assertEquals(knappMehr, liste.visible)
        assertFalse(liste.canToggle)

        val eindeutigMehr = viele.take(TagShortlist.COLLAPSED + 3)
        assertTrue(TagShortlist.of(byUse = eindeutigMehr, selected = emptySet(), expanded = false).canToggle)
    }

    @Test
    fun `ohne Schlagworte gibt es nichts zu zeigen`() {
        val liste = TagShortlist.of(byUse = emptyList(), selected = emptySet(), expanded = false)

        assertTrue(liste.visible.isEmpty())
        assertFalse(liste.canToggle)
    }
}
