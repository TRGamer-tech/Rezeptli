package ch.rezeptli.app.domain.multiplayer

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Merkt sich kurz, worueber zu zweit entschieden werden soll.
 *
 * Aus einer Wischrunde geht es direkt in den Party-Modus: geteilt wird genau das, was
 * gerade gefallen hat. Diese Liste durch die Navigation zu reichen hiesse, sie in die
 * Adresse zu schreiben - fuer ein paar Dutzend Rezepte der falsche Weg.
 *
 * Die Auswahl lebt nur bis zum Abholen und nur im Speicher. Geht die App vorher
 * dazwischen verloren, faellt der Mehrspieler-Modus auf die eigene Sammlung zurueck -
 * schlimmstenfalls muss man einmal neu wischen.
 */
@Singleton
class SharedSelectionHolder @Inject constructor() {
    private var auswahl: List<SharedRecipe> = emptyList()

    fun set(recipes: List<SharedRecipe>) {
        auswahl = recipes
    }

    /** Gibt die Auswahl heraus und vergisst sie - sie gilt fuer genau eine Runde. */
    fun take(): List<SharedRecipe> {
        val aktuell = auswahl
        auswahl = emptyList()
        return aktuell
    }
}
