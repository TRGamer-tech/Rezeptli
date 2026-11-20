package ch.rezeptli.app.domain.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * "Gibt es dieses Rezept schon?" - die eine Stelle, an der das entschieden wird.
 *
 * Gemeldet war, dass beim Uebernehmen gemeinsamer Treffer Rezepte doppelt in der Sammlung
 * landen. Die Adresse wurde Zeichen fuer Zeichen verglichen, und zwei Telefone kennen
 * dieselbe Seite oft leicht verschieden geschrieben.
 */
class RecipeMatcherTest {
    private val roesti = StoredRecipe(1L, "Rösti", "https://www.beispiel.ch/rezepte/roesti", inCollection = true)
    private val gratin = StoredRecipe(2L, "Omas Gratin", null, inCollection = true)
    private val sammlung = listOf(roesti, gratin)

    @Test
    fun `dieselbe Seite wird in jeder Schreibweise ihrer Adresse wiedererkannt`() {
        listOf(
            "https://www.beispiel.ch/rezepte/roesti",
            "http://www.beispiel.ch/rezepte/roesti",
            "https://beispiel.ch/rezepte/roesti",
            "https://www.beispiel.ch/rezepte/roesti/",
            "https://WWW.Beispiel.CH/rezepte/Roesti",
            "https://www.beispiel.ch/rezepte/roesti?utm_source=teilen&utm_medium=app",
            "https://www.beispiel.ch/rezepte/roesti#zutaten",
            "  https://www.beispiel.ch/rezepte/roesti  ",
        ).forEach { adresse ->
            assertEquals(1L, RecipeMatcher.byUrl(sammlung, adresse), adresse)
        }
    }

    @Test
    fun `eine andere Seite ist ein anderes Rezept - auch beim selben Anbieter`() {
        assertNull(RecipeMatcher.byUrl(sammlung, "https://www.beispiel.ch/rezepte/roesti-mit-speck"))
        assertNull(RecipeMatcher.byUrl(sammlung, "https://www.beispiel.ch/rezepte"))
        assertNull(RecipeMatcher.byUrl(sammlung, "https://anderswo.ch/rezepte/roesti"))
        assertNull(RecipeMatcher.byUrl(sammlung, ""))
    }

    @Test
    fun `ein selbst erfasstes Rezept wird am Titel wiedererkannt - ohne Gross und Klein`() {
        assertEquals(2L, RecipeMatcher.byTitle(sammlung, "Omas Gratin"))
        assertEquals(2L, RecipeMatcher.byTitle(sammlung, "  omas   GRATIN "))
        assertNull(RecipeMatcher.byTitle(sammlung, "Omas Gratin mit Speck"))
        assertNull(RecipeMatcher.byTitle(sammlung, "   "))
    }

    @Test
    fun `ein Rezept von einer Seite und ein selbst erfasstes mit demselben Titel bleiben zwei`() {
        // Die "Rösti" in der Sammlung stammt von einer Rezeptseite - die eigene waere ein anderes Rezept.
        assertNull(RecipeMatcher.byTitle(sammlung, "Rösti"))
        // Und umgekehrt: Der Titel einer Seite macht sie nicht zum selbst erfassten Rezept.
        assertNull(RecipeMatcher.byUrl(sammlung, "https://beispiel.ch/omas-gratin"))
    }

    @Test
    fun `liegt es in der Sammlung und im Wochenplan, zaehlt das aus der Sammlung`() {
        val nurImPlan = StoredRecipe(5L, "Rösti", "https://beispiel.ch/rezepte/roesti/", inCollection = false)
        val spaeterGespeichert = roesti.copy(id = 9L)

        assertEquals(9L, RecipeMatcher.byUrl(listOf(nurImPlan, spaeterGespeichert), roesti.sourceUrl!!))
        // Gibt es nur das aus dem Plan, wird eben das gefunden - damit kein zweites entsteht.
        assertEquals(5L, RecipeMatcher.byUrl(listOf(nurImPlan), roesti.sourceUrl!!))
    }

    @Test
    fun `unter mehreren gleichen gewinnt das aelteste`() {
        val doppelt = listOf(gratin.copy(id = 8L), gratin.copy(id = 3L))

        assertEquals(3L, RecipeMatcher.byTitle(doppelt, "Omas Gratin"))
    }
}
