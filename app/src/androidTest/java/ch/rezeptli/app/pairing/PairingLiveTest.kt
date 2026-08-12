package ch.rezeptli.app.pairing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ch.rezeptli.app.BuildConfig
import ch.rezeptli.app.data.pairing.PairingRepositoryImpl
import ch.rezeptli.app.domain.multiplayer.PairingResult
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedVote
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Spielt eine ganze gemeinsame Runde gegen den echten Pairing-Dienst durch.
 *
 * Bisher war der Mehrspieler-Modus nur gegen eine Attrappe geprueft - deshalb fiel
 * erst auf dem Geraet auf, dass "Jemanden einladen" scheitert. Dieser Test benutzt
 * denselben Code wie die App: dieselbe Repository-Klasse, derselbe HTTP-Client,
 * dieselbe Adresse aus BuildConfig.
 *
 * Die zweite Person wird direkt ueber die Schnittstelle nachgestellt. Sie braucht
 * eine eigene Kennung, und die legt die App pro Geraet nur einmal an.
 *
 * Bewusst runBlocking statt runTest: runTest rechnet mit virtueller Zeit und laesst
 * jedes withTimeout sofort ablaufen, waehrend das Netz noch antwortet. Hier soll die
 * echte Uhr zaehlen.
 */
@RunWith(AndroidJUnit4::class)
class PairingLiveTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val client = OkHttpClient
        .Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val repository = PairingRepositoryImpl(context, client, Dispatchers.IO)
    private val basis = BuildConfig.PAIRING_URL.trimEnd('/')

    private val rezepte = listOf(
        SharedRecipe(recipeId = 1L, title = "Rösti", prepTimeMinutes = 30),
        SharedRecipe(recipeId = 2L, title = "Risotto", prepTimeMinutes = 40),
        SharedRecipe(recipeId = 3L, title = "Älplermagronen", prepTimeMinutes = 35),
    )

    @Test
    fun eineGanzeRundeVonEinladenBisTreffer(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            // 1. Einladen - genau der Schritt, der auf dem Geraet fehlschlug.
            val erstellt = repository.createSession(rezepte)
            assertTrue("Einladen fehlgeschlagen: $erstellt", erstellt is PairingResult.Success)
            val sitzung = (erstellt as PairingResult.Success).value
            assertEquals(rezepte.size, sitzung.recipes.size)
            assertTrue("Code sieht falsch aus: ${sitzung.code}", sitzung.code.length >= 4)

            try {
                // 2. Die zweite Person tritt bei und sieht dieselben Rezepte.
                val gast = "test-gast-" + System.nanoTime()
                val beigetreten = beitreten(sitzung.code, gast)
                assertEquals(
                    rezepte.map { it.title }.toSet(),
                    beigetreten.map { it.title }.toSet(),
                )

                // 3. Beide stimmen ab. Gemeinsam gefaellt nur das Risotto.
                val eigene = listOf(
                    SharedVote(recipeId = 1L, liked = true),
                    SharedVote(recipeId = 2L, liked = true),
                    SharedVote(recipeId = 3L, liked = false),
                )
                val gesendet = repository.sendVotes(sitzung.code, eigene, finished = true)
                assertTrue("Stimmen abgelehnt: $gesendet", gesendet is PairingResult.Success)

                stimmenSenden(
                    sitzung.code,
                    gast,
                    listOf(1L to false, 2L to true, 3L to false),
                )

                // 4. Erst wenn beide fertig sind, gibt es Treffer.
                val stand = repository.sessionState(sitzung.code)
                assertTrue("Stand nicht abrufbar: $stand", stand is PairingResult.Success)
                val zustand = (stand as PairingResult.Success).value
                assertEquals(2, zustand.participants)
                assertTrue("Beide sollten fertig sein: $zustand", zustand.allFinished)
                assertEquals(listOf("Risotto"), zustand.matches.map { it.title })
            } finally {
                repository.closeSession(sitzung.code)
            }
        }
    }

    @Test
    fun einUnbekannterCodeWirdSauberAbgelehnt(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val result = repository.joinSession("ZZZZZZ")

            assertTrue("Erwartet wurde eine Absage, kam: $result", result is PairingResult.Failure)
        }
    }

    /**
     * Bringt die beitretende Person eigene Rezepte mit, landen sie im gemeinsamen Topf -
     * beide Seiten stimmen danach ueber dieselbe, gemischte Auswahl ab statt nur ueber
     * die des Gastgebers.
     */
    @Test
    fun eigeneRezepteBeimBeitretenLandenImGemeinsamenTopf(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MS) {
            val erstellt = repository.createSession(rezepte)
            assertTrue("Einladen fehlgeschlagen: $erstellt", erstellt is PairingResult.Success)
            val sitzung = (erstellt as PairingResult.Success).value

            try {
                val gast = "test-gast-" + System.nanoTime()
                val mitgebracht = listOf(SharedRecipe(recipeId = 4L, title = "Zopf"))

                val topf = beitreten(sitzung.code, gast, mitgebracht)

                assertEquals(
                    (rezepte + mitgebracht).map { it.title }.toSet(),
                    topf.map { it.title }.toSet(),
                )
            } finally {
                repository.closeSession(sitzung.code)
            }
        }
    }

    /** Der Beitritt der zweiten Person - ohne die App, mit eigener Kennung. */
    private fun beitreten(
        code: String,
        kennung: String,
        mitgebracht: List<SharedRecipe> = emptyList(),
    ): List<SharedRecipe> {
        val rumpf = JSONObject().put("teilnehmer", kennung)
        if (mitgebracht.isNotEmpty()) {
            val liste = JSONArray()
            mitgebracht.forEach { rezept ->
                liste.put(JSONObject().put("rezeptId", rezept.recipeId).put("titel", rezept.title))
            }
            rumpf.put("rezepte", liste)
        }

        val antwort = sende("$basis/sitzung/$code/beitreten", rumpf)
        val rohe = antwort.optJSONArray("rezepte") ?: JSONArray()
        return (0 until rohe.length()).map { index ->
            val eintrag = rohe.getJSONObject(index)
            SharedRecipe(
                recipeId = eintrag.getLong("rezeptId"),
                title = eintrag.getString("titel"),
            )
        }
    }

    private fun stimmenSenden(code: String, kennung: String, stimmen: List<Pair<Long, Boolean>>) {
        val liste = JSONArray()
        stimmen.forEach { (id, mag) ->
            liste.put(JSONObject().put("rezeptId", id).put("mag", mag))
        }
        sende(
            "$basis/sitzung/$code/stimmen",
            JSONObject()
                .put("teilnehmer", kennung)
                .put("stimmen", liste)
                .put("fertig", true),
        )
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

    private companion object {
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        const val TEST_TIMEOUT_MS = 60_000L
    }
}
