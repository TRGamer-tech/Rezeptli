package ch.rezeptli.app.data.pairing

import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Die Antworten in diesen Tests sind wortwoertlich die des laufenden Dienstes.
 *
 * An genau diesem Unterschied ist "Jemanden einladen" auf dem Geraet gescheitert: Beim
 * Eroeffnen steht in `rezepte` eine Anzahl, beim Beitreten eine Liste. Der Parser las
 * beides als Liste, warf beim Eroeffnen eine Ausnahme, und die App zeigte nur noch
 * "Das hat nicht geklappt" - obwohl die Runde beim Dienst laengst stand.
 */
class PairingApiTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun body(text: String) = json.parseToJsonElement(text).jsonObject

    private val gesendet = listOf(
        SharedRecipe(recipeId = 1L, title = "Rösti", prepTimeMinutes = 30),
        SharedRecipe(recipeId = 2L, title = "Risotto"),
    )

    @Test
    fun `Antwort auf das Eroeffnen nennt nur die Anzahl`() {
        val antwort = body("""{"code":"LUH64E","verfaelltAm":1786600000000,"rezepte":2}""")

        val sitzung = PairingApi.sessionFrom(antwort, fallback = gesendet)

        assertEquals("LUH64E", sitzung.code)
        assertEquals(1786600000000L, sitzung.expiresAt)
        // Die Rezepte stammen aus dem, was der Gastgeber geschickt hat.
        assertEquals(listOf("Rösti", "Risotto"), sitzung.recipes.map { it.title })
    }

    @Test
    fun `Antwort auf das Beitreten bringt die Liste mit`() {
        val antwort = body(
            """
            {"code":"LUH64E","verfaelltAm":1786600000000,"rezepte":[
              {"rezeptId":1,"titel":"Rösti","quelleUrl":null,"bildUrl":null,"zubereitungszeit":30},
              {"rezeptId":2,"titel":"Risotto","quelleUrl":null,"bildUrl":null,"zubereitungszeit":null}
            ]}
            """.trimIndent(),
        )

        val sitzung = PairingApi.sessionFrom(antwort)

        assertEquals(listOf("Rösti", "Risotto"), sitzung.recipes.map { it.title })
        assertEquals(30, sitzung.recipes.first().prepTimeMinutes)
        // "null" ist kein Bild.
        assertEquals(null, sitzung.recipes.first().imageUrl)
    }

    @Test
    fun `fehlendes Rezeptfeld faellt auf das Gesendete zurueck`() {
        val sitzung = PairingApi.sessionFrom(body("""{"code":"AB12CD"}"""), fallback = gesendet)

        assertEquals(gesendet, sitzung.recipes)
    }

    @Test
    fun `Stand vor dem Ende zeigt keine Treffer`() {
        val zustand = PairingApi.stateFrom(
            body("""{"code":"LUH64E","teilnehmer":2,"fertig":1,"alleFertig":false}"""),
        )

        assertEquals(2, zustand.participants)
        assertEquals(1, zustand.finished)
        assertTrue(zustand.matches.isEmpty())
    }

    @Test
    fun `Stand am Ende bringt die Treffer`() {
        val zustand = PairingApi.stateFrom(
            body(
                """
                {"code":"LUH64E","teilnehmer":2,"fertig":2,"alleFertig":true,
                 "treffer":[{"rezeptId":2,"titel":"Risotto"}]}
                """.trimIndent(),
            ),
        )

        assertTrue(zustand.allFinished)
        assertEquals(listOf("Risotto"), zustand.matches.map { it.title })
    }

    @Test
    fun `Stand bringt den aktuellen Topf mit - auch mitgebrachte Rezepte der beitretenden Person`() {
        val zustand = PairingApi.stateFrom(
            body(
                """
                {"code":"LUH64E","teilnehmer":2,"fertig":0,"alleFertig":false,"rezepte":[
                  {"rezeptId":1,"titel":"Rösti","quelleUrl":null,"bildUrl":null,"zubereitungszeit":null},
                  {"rezeptId":2,"titel":"Risotto","quelleUrl":null,"bildUrl":null,"zubereitungszeit":null}
                ]}
                """.trimIndent(),
            ),
        )

        assertEquals(listOf("Rösti", "Risotto"), zustand.pool.map { it.title })
    }

    @Test
    fun `Antwortcodes werden erklaerbar uebersetzt`() {
        assertEquals(PairingError.UNKNOWN_CODE, PairingApi.errorFor(404))
        assertEquals(PairingError.FULL, PairingApi.errorFor(409))
        assertEquals(PairingError.EXPIRED, PairingApi.errorFor(410))
        assertEquals(PairingError.NO_RECIPES, PairingApi.errorFor(413))
        assertEquals(PairingError.UNKNOWN, PairingApi.errorFor(500))
    }
}
