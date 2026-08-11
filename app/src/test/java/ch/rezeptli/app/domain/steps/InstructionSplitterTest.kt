package ch.rezeptli.app.domain.steps

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InstructionSplitterTest {
    private val splitter = InstructionSplitter()

    @Test
    fun `nutzt die Nummerierung, wenn der Text welche hat`() {
        val text = """
            1. Zwiebeln schneiden.
            2. In der Pfanne anbraten.
            3. Mit Bouillon abloeschen.
        """.trimIndent()

        val steps = splitter.split(text)

        assertEquals(3, steps.size)
        assertEquals("Zwiebeln schneiden.", steps[0].text)
        assertEquals("In der Pfanne anbraten.", steps[1].text)
        assertEquals(listOf(0, 1, 2), steps.map { it.position })
    }

    @Test
    fun `erkennt auch die Schreibweise mit Klammer und mit dem Wort Schritt`() {
        val mitKlammer = splitter.split("1) Teig kneten.\n2) Ruhen lassen.")
        assertEquals(listOf("Teig kneten.", "Ruhen lassen."), mitKlammer.map { it.text })

        val mitWort = splitter.split("Schritt 1: Ofen vorheizen.\nSchritt 2: Blech einschieben.")
        assertEquals(listOf("Ofen vorheizen.", "Blech einschieben."), mitWort.map { it.text })
    }

    @Test
    fun `trennt an Leerzeilen, wenn keine Nummerierung da ist`() {
        val text = "Zwiebeln schneiden.\n\nAnbraten bis sie glasig sind.\n\nServieren."

        val steps = splitter.split(text)

        assertEquals(3, steps.size)
        assertEquals("Anbraten bis sie glasig sind.", steps[1].text)
    }

    @Test
    fun `trennt an einfachen Zeilenumbruechen, wenn es mehrere gibt`() {
        val steps = splitter.split("Gemuese ruesten.\nAlles in die Pfanne geben.")

        assertEquals(2, steps.size)
    }

    @Test
    fun `entfernt Aufzaehlungszeichen am Zeilenanfang`() {
        val steps = splitter.split("- Ruesten.\n- Anbraten.")

        assertEquals(listOf("Ruesten.", "Anbraten."), steps.map { it.text })
    }

    @Test
    fun `laesst einen kurzen Text als einen Schritt stehen`() {
        val steps = splitter.split("Alles zusammen kochen und servieren.")

        assertEquals(1, steps.size)
        assertEquals("Alles zusammen kochen und servieren.", steps.first().text)
    }

    @Test
    fun `trennt einen langen Fliesstext nach Saetzen`() {
        val text = "Die Zwiebeln schaelen und fein schneiden. " +
            "Das Olivenoel in einer grossen Pfanne erhitzen und die Zwiebeln glasig duensten. " +
            "Den Reis dazugeben und kurz mitroesten, bis er glasig ist. " +
            "Mit Weisswein abloeschen und vollstaendig einkochen lassen."

        val steps = splitter.split(text)

        assertTrue(steps.size >= 3, "erwartet mehrere Schritte, waren ${steps.size}")
        assertTrue(steps.first().text.startsWith("Die Zwiebeln"))
    }

    @Test
    fun `liefert fuer leeren Text keine Schritte`() {
        assertTrue(splitter.split("").isEmpty())
        assertTrue(splitter.split("   \n  ").isEmpty())
    }

    @Test
    fun `erkennt eine Wartezeit in Minuten`() {
        assertEquals(20, splitter.timerMinutesIn("20 Minuten backen."))
        assertEquals(5, splitter.timerMinutesIn("Ca. 5 Min. ziehen lassen."))
        assertEquals(45, splitter.timerMinutesIn("45 minuten koecheln"))
    }

    @Test
    fun `rechnet Stunden in Minuten um`() {
        assertEquals(120, splitter.timerMinutesIn("2 Stunden schmoren."))
        assertEquals(90, splitter.timerMinutesIn("1,5 Stunden ruhen lassen."))
        assertEquals(60, splitter.timerMinutesIn("1 h garen."))
    }

    @Test
    fun `haelt Temperaturen und Mengen nicht fuer eine Zeit`() {
        assertNull(splitter.timerMinutesIn("Bei 200 Grad backen."))
        assertNull(splitter.timerMinutesIn("3 EL Olivenoel dazugeben."))
        assertNull(splitter.timerMinutesIn("Auf 180 Grad Umluft vorheizen."))
    }

    @Test
    fun `nimmt bei mehreren Zeiten die erste`() {
        assertEquals(20, splitter.timerMinutesIn("20 Minuten backen, dann 5 Minuten ruhen lassen."))
    }

    @Test
    fun `haengt den Timer an den Schritt, in dem die Zeit steht`() {
        val steps = splitter.split("1. Teig kneten.\n2. 30 Minuten ruhen lassen.\n3. Ausrollen.")

        assertNull(steps[0].timerMinutes)
        assertEquals(30, steps[1].timerMinutes)
        assertTrue(steps[1].hasTimer)
        assertNull(steps[2].timerMinutes)
    }

    @Test
    fun `weist unsinnige Zeitangaben ab`() {
        assertNull(splitter.timerMinutesIn("0 Minuten warten."))
        assertNull(splitter.timerMinutesIn("500 Stunden warten."))
    }
}
