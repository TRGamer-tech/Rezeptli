package ch.rezeptli.app.pairing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ch.rezeptli.app.BuildConfig
import ch.rezeptli.app.data.pairing.PairingRepositoryImpl
import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.PairingResult
import ch.rezeptli.app.domain.multiplayer.RoundEnd
import ch.rezeptli.app.domain.multiplayer.RoundOffer
import ch.rezeptli.app.domain.multiplayer.RoundPhase
import ch.rezeptli.app.domain.multiplayer.RoundSource
import ch.rezeptli.app.domain.multiplayer.RoundState
import ch.rezeptli.app.domain.multiplayer.SharedContent
import ch.rezeptli.app.domain.multiplayer.SharedIngredient
import ch.rezeptli.app.domain.multiplayer.SharedVote
import ch.rezeptli.app.domain.plan.CourseChoice
import ch.rezeptli.app.domain.plan.DietaryFilter
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.repository.PairingRepository.Companion.NO_ROUND
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Spielt ganze gemeinsame Runden gegen den echten Pairing-Dienst durch.
 *
 * Die erste Fassung des Mehrspieler-Modus war nur gegen eine Attrappe geprueft - deshalb
 * fiel erst auf dem Geraet auf, dass "Jemanden einladen" scheitert. Dieser Test benutzt
 * denselben Code wie die App: dieselbe Repository-Klasse, derselbe HTTP-Client, dieselbe
 * Adresse aus BuildConfig.
 *
 * Die App ist hier die Person, die eroeffnet. Die zweite Person wird direkt ueber die
 * Schnittstelle nachgestellt: Sie braucht eine eigene Kennung, und die legt die App pro
 * Geraet nur einmal an.
 *
 * Bewusst runBlocking statt runTest: runTest rechnet mit virtueller Zeit und laesst jedes
 * withTimeout sofort ablaufen, waehrend das Netz noch antwortet. Hier soll die echte Uhr
 * zaehlen.
 */
@RunWith(AndroidJUnit4::class)
class PairingLiveTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val client = OkHttpClient
        .Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val repository = PairingRepositoryImpl(context, client, Dispatchers.IO)
    private val dienst = BuildConfig.PAIRING_URL.trimEnd('/')
    private val basis = "$dienst/v2"

    private val roesti = RoundOffer(key = "u-roesti", title = "Rösti", sourceUrl = "https://beispiel.ch/roesti")
    private val risotto = RoundOffer(key = "u-risotto", title = "Risotto", sourceUrl = "https://beispiel.ch/risotto")
    private val aelpler = RoundOffer(key = "u-aelpler", title = "Älplermagronen")

    private fun <T> PairingResult<T>.wert(schritt: String): T {
        assertTrue("$schritt fehlgeschlagen: $this", this is PairingResult.Success)
        return (this as PairingResult.Success).value
    }

    /** Eroeffnet eine Runde, laesst die zweite Person beitreten und bringt beide bis zum gemeinsamen Stapel. */
    private suspend fun bisZumStapel(ziel: Int, gast: String): RoundState {
        val lobby = repository.createRound(DietaryFilter(dislikes = setOf(Dislike.FISCH))).wert("Eröffnen")
        val code = lobby.code
        sende("$basis/sitzung/$code/beitreten", JSONObject().put("teilnehmer", gast))
        repository.configureRound(code, ziel, CourseChoice.ESSEN, RoundSource.GESPEICHERT).wert("Einstellen")
        repository.startRound(code).wert("Starten")
        repository.contribute(code, round = 0, offers = listOf(roesti, risotto)).wert("Eigener Beitrag")
        beitragen(code, gast, runde = 0, karten = listOf(risotto, aelpler))
        return repository.roundState(code, revision = null, knownRound = NO_ROUND).wert("Stand")!!
    }

    @Test
    fun eineGanzeRundeVomCodeBisZumZiel(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            // 1. Der Code steht sofort - noch ohne Rezepte, ohne zweite Person, ohne Einstellungen.
            val lobby = repository.createRound(DietaryFilter(dislikes = setOf(Dislike.FISCH))).wert("Eröffnen")
            val code = lobby.code
            assertTrue("Code sieht falsch aus: $code", code.length >= 4)
            assertEquals(RoundPhase.LOBBY, lobby.phase)
            assertTrue(lobby.isHost)
            assertTrue(lobby.isWaitingForPartner)

            try {
                // 2. Die zweite Person tritt bei. Was sie nicht will, gilt ab jetzt fuer beide.
                val gast = "test-gast-" + System.nanoTime()
                val beimGast = sende(
                    "$basis/sitzung/$code/beitreten",
                    JSONObject()
                        .put("teilnehmer", gast)
                        .put(
                            "einschraenkungen",
                            JSONObject()
                                .put("ohne", JSONArray().put("VEGAN"))
                                .put("unvertraeglich", JSONArray().put("NUESSE")),
                        ),
                )
                assertFalse("Die eingeladene Person ist nicht Gastgeber", beimGast.getBoolean("gastgeber"))

                val zuZweit = repository.roundState(code).wert("Stand in der Lobby")!!
                assertEquals(2, zuZweit.participants)
                assertEquals(setOf(Dislike.FISCH, Dislike.VEGAN), zuZweit.restrictions.dislikes)
                assertEquals(setOf(Intolerance.NUESSE), zuZweit.restrictions.intolerances)

                // 3. Wer eroeffnet hat, legt fest, was gesucht wird - die andere Person sieht es.
                val eingestellt = repository
                    .configureRound(code, target = 1, course = CourseChoice.BEIDES, source = RoundSource.GESPEICHERT)
                    .wert("Einstellen")
                assertEquals(1, eingestellt.target)
                val gesehen = hole("$basis/sitzung/$code?teilnehmer=$gast")
                assertEquals(1, gesehen.getInt("ziel"))
                assertEquals("beides", gesehen.getString("art"))

                // 4. Start: Beide steuern ihre gespeicherten Rezepte bei.
                assertEquals(RoundPhase.SAMMELN, repository.startRound(code).wert("Starten").phase)
                val wartet = repository.contribute(code, round = 0, offers = listOf(roesti, risotto)).wert("Beitrag")
                assertEquals(RoundPhase.SAMMELN, wartet.phase)
                assertTrue(wartet.hasContributed)
                beitragen(code, gast, runde = 0, karten = listOf(risotto, aelpler))

                // 5. Beide sehen denselben Stapel in derselben Reihenfolge - das doppelte Rezept nur einmal.
                val laeuft = repository.roundState(code, revision = null, knownRound = NO_ROUND).wert("Stapel")!!
                assertEquals(RoundPhase.LAEUFT, laeuft.phase)
                val stapel = laeuft.stack!!
                assertEquals(listOf("Rösti", "Risotto", "Älplermagronen"), stapel.map { it.title })
                val beimGastStapel = hole("$basis/sitzung/$code?teilnehmer=$gast&runde=-1").getJSONArray("stapel")
                assertEquals(
                    stapel.map { it.recipeId },
                    (0 until beimGastStapel.length()).map { beimGastStapel.getJSONObject(it).getLong("rezeptId") },
                )

                // 6. Jede Stimme zaehlt sofort. Mit dem ersten gemeinsamen Ja ist das Ziel erreicht.
                val risottoKarte = stapel.first { it.title == "Risotto" }.recipeId
                val einJa = repository
                    .sendVotes(
                        code,
                        listOf(SharedVote(risottoKarte, true)),
                        knownRound = 0,
                    ).wert("Stimme")
                assertEquals(RoundPhase.LAEUFT, einJa.phase)
                assertEquals(0, einJa.matchCount)
                stimmen(code, gast, listOf(risottoKarte to true))

                // 7. Schluss - obwohl zwei Karten noch niemand entschieden hat.
                val ende = repository.roundState(code, revision = einJa.revision, knownRound = 0).wert("Ende")!!
                assertEquals(RoundPhase.BEENDET, ende.phase)
                assertEquals(RoundEnd.ZIEL, ende.end)
                assertEquals(listOf("Risotto"), ende.matches?.map { it.title })
            } finally {
                repository.closeRound(code)
            }
        }
    }

    @Test
    fun istDerStapelDurchWirdGefragtUndEsGehtMitNeuenRezeptenWeiter(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val gast = "test-gast-" + System.nanoTime()
            val laeuft = bisZumStapel(ziel = 2, gast = gast)
            val code = laeuft.code

            try {
                val karten = laeuft.stack!!.map { it.recipeId }
                val roestiKarte = laeuft.stack!!.first { it.title == "Rösti" }.recipeId

                // Beide wischen alles durch; gemeinsam gefaellt nur die Rösti - eines von zwei.
                repository
                    .sendVotes(
                        code,
                        karten.map { SharedVote(it, it == roestiKarte) },
                        knownRound = 0,
                    ).wert("Stimmen")
                stimmen(code, gast, karten.map { it to (it == roestiKarte) })

                val frage = repository.roundState(code, revision = null, knownRound = 0).wert("Frage")!!
                assertEquals(RoundPhase.FRAGE, frage.phase)
                assertEquals(1, frage.matchCount)
                assertEquals(listOf("Rösti"), frage.matches?.map { it.title })

                // Die eingeladene Person kann das nicht entscheiden.
                assertEquals(403, status("$basis/sitzung/$code/weiter", JSONObject().put("teilnehmer", gast)))

                val weiter = repository.continueRound(code, knownRound = 0).wert("Weitermachen")
                assertEquals(RoundPhase.SAMMELN, weiter.phase)
                assertEquals(1, weiter.round)
                assertEquals(RoundSource.NEU, weiter.source)
                assertFalse(weiter.hasContributed)

                // Aufhoeren behaelt, was gefunden wurde.
                val ende = repository.endRound(code).wert("Aufhören")
                assertEquals(RoundPhase.BEENDET, ende.phase)
                assertEquals(RoundEnd.GASTGEBER, ende.end)
                assertEquals(listOf("Rösti"), ende.matches?.map { it.title })
            } finally {
                repository.closeRound(code)
            }
        }
    }

    @Test
    fun eineEntscheidungLaesstSichZuruecknehmen(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val gast = "test-gast-" + System.nanoTime()
            val laeuft = bisZumStapel(ziel = 3, gast = gast)
            val code = laeuft.code

            try {
                val karte = laeuft.stack!!.first().recipeId
                stimmen(code, gast, listOf(karte to true))

                val treffer = repository.sendVotes(code, listOf(SharedVote(karte, true)), knownRound = 0).wert("Ja")
                assertEquals(1, treffer.matchCount)
                assertEquals(1, treffer.decidedCount)

                val zurueck = repository.sendVotes(code, listOf(SharedVote(karte, null)), knownRound = 0).wert("Zurück")
                assertEquals(0, zurueck.matchCount)
                assertEquals(0, zurueck.decidedCount)

                // Wer nach einem Unterbruch zurueckkehrt, bekommt Stapel und eigene Entscheidungen.
                repository.sendVotes(code, listOf(SharedVote(karte, false)), knownRound = 0).wert("Nein")
                val zurueckgekehrt = repository
                    .joinRound(
                        code,
                        DietaryFilter(),
                        knownRound = NO_ROUND,
                    ).wert("Fortsetzen")
                assertEquals(3, zurueckgekehrt.stack?.size)
                assertEquals(listOf(SharedVote(karte, false)), zurueckgekehrt.votes)
            } finally {
                repository.closeRound(code)
            }
        }
    }

    @Test
    fun nachDemErstenBeitragLaeuftEineFrist(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val gast = "test-gast-" + System.nanoTime()
            val lobby = repository.createRound(DietaryFilter()).wert("Eröffnen")
            val code = lobby.code
            try {
                sende("$basis/sitzung/$code/beitreten", JSONObject().put("teilnehmer", gast))
                repository.configureRound(code, 2, CourseChoice.ESSEN, RoundSource.GESPEICHERT).wert("Einstellen")
                val gestartet = repository.startRound(code).wert("Starten")
                // Solange niemand etwas geschickt hat, laeuft keine Frist: Beide suchen noch.
                assertNull(gestartet.contributionWaitMs)

                // Ab dem ersten Beitrag hat das andere Telefon eine Minute - danach geht es ohne es weiter.
                val wartend = repository.contribute(code, round = 0, offers = listOf(roesti)).wert("Eigener Beitrag")
                val rest = wartend.contributionWaitMs
                assertTrue("Die Frist sollte laufen: $rest", rest != null && rest in 1L..60_000L)

                beitragen(code, gast, runde = 0, karten = listOf(risotto))
                val stapel = repository.roundState(code, revision = null, knownRound = NO_ROUND).wert("Stapel")!!
                assertEquals(RoundPhase.LAEUFT, stapel.phase)
                assertNull("Steht der Stapel, wartet niemand mehr", stapel.contributionWaitMs)
            } finally {
                repository.closeRound(code)
            }
        }
    }

    @Test
    fun einSelbstErfassterTrefferKommtMitSeinemInhaltBeimAnderenAn(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val gast = "test-gast-" + System.nanoTime()
            val lobby = repository.createRound(DietaryFilter()).wert("Eröffnen")
            val code = lobby.code
            try {
                sende("$basis/sitzung/$code/beitreten", JSONObject().put("teilnehmer", gast))
                repository.configureRound(code, 2, CourseChoice.ESSEN, RoundSource.GESPEICHERT).wert("Einstellen")
                repository.startRound(code).wert("Starten")

                // Beide bringen ein selbst erfasstes Rezept mit - ohne Adresse, nur mit Titel.
                val meines = RoundOffer(key = "e:app:1", title = "Omas Hackbraten")
                val ihres = RoundOffer(key = "e:gast:1", title = "Beats Eintopf")
                repository.contribute(code, round = 0, offers = listOf(meines)).wert("Eigener Beitrag")
                beitragen(code, gast, runde = 0, karten = listOf(ihres))
                val stapel = repository
                    .roundState(code, revision = null, knownRound = NO_ROUND)
                    .wert("Stapel")!!
                    .stack!!
                val hackbraten = stapel.first { it.title == "Omas Hackbraten" }.recipeId
                val eintopf = stapel.first { it.title == "Beats Eintopf" }.recipeId

                val inhalt = SharedContent(
                    instructions = "Mischen, formen, backen.",
                    steps = listOf("Mischen und formen.", "Eine Stunde backen."),
                    ingredients = listOf(
                        SharedIngredient("Hackfleisch", amount = 800.0, unit = "GRAMM"),
                        SharedIngredient("Salz"),
                    ),
                    servings = 4,
                    prepTimeMinutes = 75,
                )

                // Solange gewischt wird, nimmt der Dienst keinen Rezeptinhalt an.
                val zuFrueh = repository.shareContent(code, hackbraten, inhalt)
                assertTrue("Vor dem Treffer darf nichts hinterlegt werden: $zuFrueh", zuFrueh is PairingResult.Failure)

                stimmen(code, gast, stapel.map { it.recipeId to true })
                val alleJa = stapel.map { SharedVote(it.recipeId, true) }
                val ende = repository.sendVotes(code, alleJa, knownRound = 0).wert("Stimmen")
                assertEquals(RoundPhase.BEENDET, ende.phase)
                assertEquals(2, ende.matches?.size)
                assertTrue(ende.matches!!.none { it.hasContent })

                // 1. Das eigene Rezept geht an die andere Person.
                repository.shareContent(code, hackbraten, inhalt).wert("Hinterlegen")
                val beimGast = hole("$basis/sitzung/$code/inhalt?teilnehmer=$gast&rezept=$hackbraten")
                    .getJSONObject("inhalt")
                assertEquals("Mischen, formen, backen.", beimGast.getString("zubereitung"))
                assertEquals(2, beimGast.getJSONArray("zutaten").length())
                assertEquals(4, beimGast.getInt("portionen"))

                // 2. Ihres ist noch nicht da - das ist kein Fehler, nur zu frueh.
                assertNull(repository.fetchContent(code, eintopf).wert("Noch nicht da"))

                // 3. Sobald ihr Telefon es hinterlegt hat, kommt es hier vollstaendig an.
                sende(
                    "$basis/sitzung/$code/inhalt",
                    JSONObject()
                        .put("teilnehmer", gast)
                        .put("rezeptId", eintopf)
                        .put(
                            "inhalt",
                            JSONObject()
                                .put("zubereitung", "Eine Stunde köcheln.")
                                .put("zutaten", JSONArray().put(JSONObject().put("name", "Lauch").put("menge", 2)))
                                .put("portionen", 4),
                        ),
                )
                val geholt = repository.fetchContent(code, eintopf).wert("Holen")!!
                assertEquals("Eine Stunde köcheln.", geholt.instructions)
                assertEquals(listOf(SharedIngredient("Lauch", amount = 2.0)), geholt.ingredients)
                assertEquals(4, geholt.servings)

                val amEnde = repository.roundState(code, revision = null, knownRound = 0).wert("Stand")!!
                assertTrue("Beide Treffer haben jetzt ihren Inhalt", amEnde.matches!!.all { it.hasContent })
            } finally {
                repository.closeRound(code)
            }
        }
    }

    @Test
    fun einUnbekannterCodeWirdSauberAbgelehnt(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val result = repository.joinRound("ZZZZZZ", DietaryFilter())

            assertEquals(PairingResult.Failure(PairingError.UNKNOWN_CODE), result)
        }
    }

    @Test
    fun hatSichNichtsGetanKommtNurDasZurueck(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val lobby = repository.createRound(DietaryFilter()).wert("Eröffnen")
            try {
                val unveraendert = repository.roundState(lobby.code, revision = lobby.revision).wert("Stand")

                assertNull("Ohne Aenderung gibt es keinen neuen Stand", unveraendert)
            } finally {
                repository.closeRound(lobby.code)
            }
        }
    }

    @Test
    fun eineAeltereAppWirdInEinerNeuenRundeGebetenZuAktualisieren(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val lobby = repository.createRound(DietaryFilter()).wert("Eröffnen")
            try {
                // So tritt die vorige Fassung der App bei: ohne /v2 im Pfad.
                val alt = status("$dienst/sitzung/${lobby.code}/beitreten", JSONObject().put("teilnehmer", "alte-app"))

                assertEquals(426, alt)
            } finally {
                repository.closeRound(lobby.code)
            }
        }
    }

    /** Der Beitrag der zweiten Person - ohne die App, mit eigener Kennung. */
    private fun beitragen(code: String, kennung: String, runde: Int, karten: List<RoundOffer>) {
        val liste = JSONArray()
        karten.forEach { karte ->
            val eintrag = JSONObject().put("schluessel", karte.key).put("titel", karte.title)
            karte.sourceUrl?.let { eintrag.put("quelleUrl", it) }
            liste.put(eintrag)
        }
        sende(
            "$basis/sitzung/$code/beitrag",
            JSONObject().put("teilnehmer", kennung).put("runde", runde).put("rezepte", liste),
        )
    }

    private fun stimmen(code: String, kennung: String, stimmen: List<Pair<Long, Boolean>>) {
        val liste = JSONArray()
        stimmen.forEach { (id, mag) -> liste.put(JSONObject().put("rezeptId", id).put("mag", mag)) }
        sende("$basis/sitzung/$code/stimmen", JSONObject().put("teilnehmer", kennung).put("stimmen", liste))
    }

    private fun sende(url: String, rumpf: JSONObject): JSONObject {
        val request = Request
            .Builder()
            .url(url)
            .post(rumpf.toString().toRequestBody(JSON_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            assertTrue("HTTP ${response.code} für $url: $text", response.isSuccessful)
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    private fun hole(url: String): JSONObject {
        val request = Request
            .Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            assertTrue("HTTP ${response.code} für $url: $text", response.isSuccessful)
            return JSONObject(text)
        }
    }

    /** Nur der Antwortcode - fuer Anfragen, die der Dienst abweisen soll. */
    private fun status(url: String, rumpf: JSONObject): Int {
        val request = Request
            .Builder()
            .url(url)
            .post(rumpf.toString().toRequestBody(JSON_TYPE))
            .build()

        return client.newCall(request).execute().use { it.code }
    }

    private companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        const val TEST_TIMEOUT_MS = 60_000L
    }
}
