package ch.rezeptli.app.data.pairing

import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.RoundEnd
import ch.rezeptli.app.domain.multiplayer.RoundOffer
import ch.rezeptli.app.domain.multiplayer.RoundPhase
import ch.rezeptli.app.domain.multiplayer.RoundSource
import ch.rezeptli.app.domain.multiplayer.SharedVote
import ch.rezeptli.app.domain.plan.CourseChoice
import ch.rezeptli.app.domain.plan.DietaryFilter
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Die Antworten in diesen Tests sind wortwoertlich die des Dienstes (Fassung 2) - erzeugt,
 * indem der echte Dienst lokal eine Runde durchgespielt hat.
 *
 * Das hat einen Grund: Die erste Fassung scheiterte auf dem Geraet daran, dass die App
 * eine Antwort anders las, als der Dienst sie schrieb. Ein Test mit ausgedachten Antworten
 * haette das nie bemerkt.
 */
class PairingApiTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun body(text: String) = json.parseToJsonElement(text).jsonObject

    private val angaben =
        """"einschraenkungen":{"ernaehrung":["VEGETARISCH"],"unvertraeglich":["NUESSE"],""" +
            """"ohne":["FISCH","VEGAN"],"eigene":["Rosinen"]}"""

    @Test
    fun `nach dem Eroeffnen steht der Code - noch ohne Rezepte und ohne zweite Person`() {
        val state = PairingApi.stateFrom(
            body(
                """{"version":2,"code":"LUH64E","verfaelltAm":1786600000000,"standNr":0,"zustand":"lobby",""" +
                    """"gastgeber":true,"teilnehmer":1,"ziel":3,"art":"essen","quelle":"gespeichert","runde":0,""" +
                    """"einschraenkungen":{"ernaehrung":[],"unvertraeglich":["NUESSE"],"ohne":["FISCH"],""" +
                    """"eigene":["Rosinen"]},"beigetragen":false,"wartetAuf":1,"karten":0,"entschieden":0,""" +
                    """"andereFertig":false,"trefferAnzahl":0,"endeGrund":null}""",
            ),
        )

        assertEquals("LUH64E", state.code)
        assertEquals(1786600000000L, state.expiresAt)
        assertEquals(RoundPhase.LOBBY, state.phase)
        assertTrue(state.isHost)
        assertTrue(state.isWaitingForPartner)
        assertEquals(3, state.target)
        assertEquals(CourseChoice.ESSEN, state.course)
        assertEquals(RoundSource.GESPEICHERT, state.source)
        // "endeGrund":null ist kein Grund - und kein Stapel ist nicht dasselbe wie ein leerer.
        assertNull(state.end)
        assertNull(state.stack)
        assertNull(state.matches)
    }

    @Test
    fun `die vereinigten Angaben beider kommen als Filter an`() {
        val state = PairingApi.stateFrom(
            body(
                """{"version":2,"code":"LUH64E","verfaelltAm":1786600000000,"standNr":1,"zustand":"lobby",""" +
                    """"gastgeber":false,"teilnehmer":2,"ziel":3,"art":"essen","quelle":"gespeichert","runde":0,""" +
                    angaben + ""","beigetragen":false,"wartetAuf":2,"karten":0,"entschieden":0,""" +
                    """"andereFertig":false,"trefferAnzahl":0,"endeGrund":null}""",
            ),
        )

        assertFalse(state.isHost)
        assertFalse(state.isWaitingForPartner)
        assertEquals(
            DietaryFilter(
                diets = setOf(Diet.VEGETARISCH),
                intolerances = setOf(Intolerance.NUESSE),
                dislikes = setOf(Dislike.FISCH, Dislike.VEGAN),
                customDislikes = listOf("Rosinen"),
            ),
            state.restrictions,
        )
    }

    @Test
    fun `mit dem Stapel kommen die eigenen bisherigen Entscheidungen`() {
        val state = PairingApi.stateFrom(
            body(
                """{"version":2,"code":"LUH64E","verfaelltAm":1786600000000,"standNr":7,"zustand":"laeuft",""" +
                    """"gastgeber":false,"teilnehmer":2,"ziel":2,"art":"beides","quelle":"gespeichert","runde":0,""" +
                    angaben + ""","beigetragen":true,"wartetAuf":0,"karten":3,"entschieden":2,""" +
                    """"andereFertig":false,"trefferAnzahl":0,"endeGrund":null,"stapel":[""" +
                    """{"rezeptId":1,"titel":"Rösti","quelleUrl":"https://beispiel.ch/roesti",""" +
                    """"bildUrl":"https://beispiel.ch/roesti.jpg","zubereitungszeit":30,""" +
                    """"schluessel":"u1","vonMir":false},""" +
                    """{"rezeptId":3,"titel":"Älplermagronen","quelleUrl":"https://beispiel.ch/aelpler",""" +
                    """"bildUrl":null,"zubereitungszeit":null,"schluessel":"u3","vonMir":true},""" +
                    """{"rezeptId":2,"titel":"Omas Gratin","quelleUrl":null,"bildUrl":null,"zubereitungszeit":null,""" +
                    """"schluessel":"e:anna:7","vonMir":false}],""" +
                    """"stimmen":[{"rezeptId":1,"mag":true},{"rezeptId":3,"mag":false}]}""",
            ),
        )

        assertEquals(RoundPhase.LAEUFT, state.phase)
        assertEquals(CourseChoice.BEIDES, state.course)
        assertEquals(2, state.target)
        // Die Reihenfolge ist die des Dienstes - beide Telefone sehen dieselbe.
        assertEquals(listOf("Rösti", "Älplermagronen", "Omas Gratin"), state.stack?.map { it.title })
        assertEquals(listOf(1L, 3L, 2L), state.stack?.map { it.recipeId })

        val roesti = state.stack!!.first()
        assertEquals("https://beispiel.ch/roesti", roesti.sourceUrl)
        assertEquals(30, roesti.prepTimeMinutes)
        assertEquals("u1", roesti.key)
        assertFalse(roesti.isMine)

        val gratin = state.stack!!.last()
        // "null" ist weder eine Adresse noch ein Bild.
        assertNull(gratin.sourceUrl)
        assertNull(gratin.imageUrl)
        assertEquals("e:anna:7", gratin.key)

        assertTrue(state.stack!![1].isMine)
        assertEquals(listOf(SharedVote(1L, true), SharedVote(3L, false)), state.votes)
        // Waehrend des Wischens nennt der Dienst nur die Zahl der Treffer.
        assertNull(state.matches)
    }

    @Test
    fun `hat sich nichts getan, sagt der Dienst nur das`() {
        val antwort = body("""{"version":2,"code":"LUH64E","standNr":7,"unveraendert":true}""")

        assertTrue(PairingApi.isUnchanged(antwort))
        assertFalse(PairingApi.isUnchanged(body("""{"version":2,"code":"LUH64E","standNr":8,"zustand":"laeuft"}""")))
    }

    @Test
    fun `ist der Stapel durch, kommen die bisherigen Treffer mit`() {
        val state = PairingApi.stateFrom(
            body(
                """{"version":2,"code":"LUH64E","verfaelltAm":1786600000000,"standNr":10,"zustand":"frage",""" +
                    """"gastgeber":true,"teilnehmer":2,"ziel":2,"art":"beides","quelle":"gespeichert","runde":0,""" +
                    angaben + ""","beigetragen":true,"wartetAuf":0,"karten":3,"entschieden":3,""" +
                    """"andereFertig":true,"trefferAnzahl":1,"endeGrund":null,"treffer":[""" +
                    """{"rezeptId":1,"titel":"Rösti","quelleUrl":"https://beispiel.ch/roesti",""" +
                    """"bildUrl":"https://beispiel.ch/roesti.jpg","zubereitungszeit":30,"schluessel":"u1","vonMir":true}]}""",
            ),
        )

        assertEquals(RoundPhase.FRAGE, state.phase)
        assertEquals(1, state.matchCount)
        assertTrue(state.othersDone)
        assertEquals(listOf("Rösti"), state.matches?.map { it.title })
        assertNull(state.end)
    }

    @Test
    fun `das Ende nennt seinen Grund`() {
        fun ende(grund: String) = PairingApi.stateFrom(
            body(
                """{"version":2,"code":"LUH64E","standNr":11,"zustand":"beendet","endeGrund":"$grund","treffer":[]}""",
            ),
        )

        assertEquals(RoundPhase.BEENDET, ende("ziel").phase)
        assertEquals(RoundEnd.ZIEL, ende("ziel").end)
        assertEquals(RoundEnd.LEER, ende("leer").end)
        assertEquals(RoundEnd.GASTGEBER, ende("gastgeber").end)
        assertEquals(RoundEnd.VERLASSEN, ende("verlassen").end)
        // Keine Treffer ist eine leere Liste - nicht "nicht mitgeschickt".
        assertEquals(emptyList<Any>(), ende("ziel").matches)
    }

    @Test
    fun `Unbekanntes von einer neueren App bringt nichts durcheinander`() {
        val state = PairingApi.stateFrom(
            body(
                """{"version":3,"code":"LUH64E","standNr":4,"zustand":"abstimmung","art":"brunch",""" +
                    """"quelle":"wunschliste","ziel":99,"endeGrund":"stromausfall","neuesFeld":{"a":1},""" +
                    """"einschraenkungen":{"ernaehrung":["FRUTARISCH","VEGAN"],"ohne":["LAKRITZ"],"eigene":[]}}""",
            ),
        )

        // Ein Zustand, den diese App nicht kennt: lieber warten als raten.
        assertEquals(RoundPhase.SAMMELN, state.phase)
        assertEquals(CourseChoice.ESSEN, state.course)
        assertEquals(RoundSource.GESPEICHERT, state.source)
        assertEquals(20, state.target)
        assertNull(state.end)
        // Bekannte Angaben bleiben, unbekannte fallen weg.
        assertEquals(setOf(Diet.VEGAN), state.restrictions.diets)
        assertTrue(state.restrictions.dislikes.isEmpty())
    }

    @Test
    fun `beim Eroeffnen und Beitreten gehen die eigenen Angaben getrennt nach ihrer Bedeutung mit`() {
        val meine = DietaryFilter(
            diets = setOf(Diet.PESCETARISCH),
            intolerances = setOf(Intolerance.LAKTOSE, Intolerance.GLUTEN),
            dislikes = setOf(Dislike.VEGAN, Dislike.PILZE),
            customDislikes = listOf("Rosinen"),
        )

        val eroeffnen = PairingApi.createBody("ich", meine)
        val beitreten = PairingApi.joinBody("ich", meine, knownRound = -1)

        val angaben = eroeffnen["einschraenkungen"]!!.jsonObject
        // "Vegan" unter den Abneigungen heisst "keine veganen Gerichte" - es darf nie bei der
        // Ernaehrung landen, wo es "nur vegan" hiesse.
        assertEquals(listOf("PESCETARISCH"), angaben["ernaehrung"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(
            listOf("LAKTOSE", "GLUTEN"),
            angaben["unvertraeglich"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(listOf("VEGAN", "PILZE"), angaben["ohne"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("Rosinen"), angaben["eigene"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("ich", eroeffnen["teilnehmer"]!!.jsonPrimitive.content)

        assertEquals(angaben, beitreten["einschraenkungen"])
        assertEquals("-1", beitreten["kenntRunde"]!!.jsonPrimitive.content)
    }

    @Test
    fun `die Einstellungen gehen in den Worten des Dienstes`() {
        val rumpf = PairingApi.settingsBody(
            "ich",
            target = 5,
            course = CourseChoice.GETRAENKE,
            source = RoundSource.NEU,
        )

        assertEquals("5", rumpf["ziel"]!!.jsonPrimitive.content)
        assertEquals("getraenke", rumpf["art"]!!.jsonPrimitive.content)
        assertEquals("neu", rumpf["quelle"]!!.jsonPrimitive.content)
    }

    @Test
    fun `ein Beitrag nennt Schluessel und Titel - und Adresse oder Bild nur, wenn es sie gibt`() {
        val rumpf = PairingApi.contributionBody(
            participantId = "ich",
            round = 1,
            offers = listOf(
                RoundOffer(key = "u1", title = "Rösti", sourceUrl = "https://beispiel.ch/roesti", prepTimeMinutes = 30),
                RoundOffer(key = "e:ich:7", title = "Omas Gratin"),
            ),
            knownRound = 0,
        )

        assertEquals("1", rumpf["runde"]!!.jsonPrimitive.content)
        assertEquals("0", rumpf["kenntRunde"]!!.jsonPrimitive.content)
        val (roesti, gratin) = rumpf["rezepte"]!!.jsonArray.map { it.jsonObject }
        assertEquals("u1", roesti["schluessel"]!!.jsonPrimitive.content)
        assertEquals("https://beispiel.ch/roesti", roesti["quelleUrl"]!!.jsonPrimitive.content)
        assertEquals("30", roesti["zubereitungszeit"]!!.jsonPrimitive.content)
        assertEquals(setOf("schluessel", "titel"), gratin.keys)
    }

    @Test
    fun `eine zurueckgenommene Entscheidung geht als null`() {
        val rumpf = PairingApi.votesBody(
            participantId = "ich",
            votes = listOf(SharedVote(1L, true), SharedVote(2L, false), SharedVote(1L, null)),
            knownRound = 0,
        )

        val stimmen = rumpf["stimmen"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("1", "2", "1"), stimmen.map { it["rezeptId"]!!.jsonPrimitive.content })
        assertEquals("true", stimmen[0]["mag"]!!.jsonPrimitive.content)
        assertEquals("false", stimmen[1]["mag"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, stimmen[2]["mag"])
    }

    @Test
    fun `Antwortcodes werden zu erklaerbaren Fehlern`() {
        assertEquals(PairingError.UNKNOWN_CODE, PairingApi.errorFor(404))
        // Nicht (mehr) Teil der Runde ist fuer die Person dasselbe wie "gibt es nicht".
        assertEquals(PairingError.UNKNOWN_CODE, PairingApi.errorFor(403))
        assertEquals(PairingError.FULL, PairingApi.errorFor(409))
        assertEquals(PairingError.EXPIRED, PairingApi.errorFor(410))
        // Alte und neue App treffen aufeinander: bitte aktualisieren.
        assertEquals(PairingError.OUTDATED, PairingApi.errorFor(426))
        assertEquals(PairingError.UNKNOWN, PairingApi.errorFor(500))
    }
}
