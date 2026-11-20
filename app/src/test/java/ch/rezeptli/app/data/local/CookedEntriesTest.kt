package ch.rezeptli.app.data.local

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Das Gedaechtnis des Plans fuer gekochte Gerichte - so, wie es in der Einstellungsdatei steht. */
class CookedEntriesTest {
    private val tag = 24L * 60 * 60 * 1_000
    private val heute = 400 * tag

    @Test
    fun `ein Vermerk ueberlebt das Speichern und Lesen - auch bei einer Adresse mit Trennzeichen`() {
        val adresse = "https://beispiel.ch/rezept?a=1|2&b=3"

        val zeilen = CookedEntries.updated(null, listOf(adresse, "rösti"), at = heute, now = heute)

        assertEquals(mapOf(adresse to heute, "rösti" to heute), CookedEntries.decode(zeilen))
    }

    @Test
    fun `ein neuer Vermerk ersetzt den alten`() {
        val alt = CookedEntries.updated(null, listOf("rösti"), at = heute - 10 * tag, now = heute - 10 * tag)

        val neu = CookedEntries.updated(alt, listOf("rösti"), at = heute, now = heute)

        assertEquals(mapOf("rösti" to heute), CookedEntries.decode(neu))
    }

    @Test
    fun `den Vermerk zuruecknehmen entfernt nur dieses Gericht`() {
        val zeilen = CookedEntries.updated(null, listOf("rösti", "gulasch"), at = heute, now = heute)

        val danach = CookedEntries.updated(zeilen, listOf("rösti"), at = null, now = heute)

        assertEquals(mapOf("gulasch" to heute), CookedEntries.decode(danach))
    }

    @Test
    fun `was lange her ist, wird beim naechsten Vermerk vergessen`() {
        val alt = CookedEntries.updated(null, listOf("fondue"), at = heute - 70 * tag, now = heute - 70 * tag)
        val mittel = CookedEntries.updated(alt, listOf("raclette"), at = heute - 20 * tag, now = heute - 20 * tag)

        val neu = CookedEntries.updated(mittel, listOf("rösti"), at = heute, now = heute)

        assertEquals(mapOf("raclette" to heute - 20 * tag, "rösti" to heute), CookedEntries.decode(neu))
    }

    @Test
    fun `kaputte Zeilen werden uebergangen statt alles zu verlieren`() {
        val zeilen = setOf("kein-zeitpunkt|rösti", "123", "", "$heute|gulasch", "$heute|")

        assertEquals(mapOf("gulasch" to heute), CookedEntries.decode(zeilen))
        assertTrue(CookedEntries.decode(null).isEmpty())
    }
}
