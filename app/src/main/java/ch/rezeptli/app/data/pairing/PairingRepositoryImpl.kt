package ch.rezeptli.app.data.pairing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ch.rezeptli.app.BuildConfig
import ch.rezeptli.app.di.IoDispatcher
import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.PairingResult
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedSession
import ch.rezeptli.app.domain.multiplayer.SharedSessionState
import ch.rezeptli.app.domain.multiplayer.SharedVote
import ch.rezeptli.app.domain.repository.PairingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val Context.pairingDataStore: DataStore<Preferences> by preferencesDataStore(name = "pairing")

/**
 * Spricht mit dem Pairing-Dienst.
 *
 * Jeder Fehler wird zu einem [PairingResult.Failure] - der Mehrspieler-Modus darf die
 * App nie zum Absturz bringen. Faellt der Dienst aus, kocht man eben allein weiter.
 */
@Singleton
class PairingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PairingRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun createSession(recipes: List<SharedRecipe>): PairingResult<SharedSession> {
        if (recipes.isEmpty()) return PairingResult.Failure(PairingError.NO_RECIPES)

        val body = PairingApi.createBody(participantId(), recipes)
        // Der Dienst bestaetigt beim Eroeffnen nur die Anzahl. Die Rezepte selbst
        // stehen schon hier - sie muessen nicht zurueckkommen.
        return post("$BASE_URL/sitzung", body) { PairingApi.sessionFrom(it, fallback = recipes) }
    }

    override suspend fun joinSession(code: String): PairingResult<SharedSession> {
        val cleaned = code.normalizeCode()
        if (cleaned.isEmpty()) return PairingResult.Failure(PairingError.UNKNOWN_CODE)

        val body = PairingApi.joinBody(participantId())
        return post("$BASE_URL/sitzung/$cleaned/beitreten", body) { PairingApi.sessionFrom(it) }
    }

    override suspend fun sendVotes(
        code: String,
        votes: List<SharedVote>,
        finished: Boolean,
    ): PairingResult<Unit> {
        val body = PairingApi.votesBody(participantId(), votes, finished)
        return post("$BASE_URL/sitzung/${code.normalizeCode()}/stimmen", body) { }
    }

    override suspend fun sessionState(code: String): PairingResult<SharedSessionState> =
        get("$BASE_URL/sitzung/${code.normalizeCode()}") { PairingApi.stateFrom(it) }

    override suspend fun closeSession(code: String): PairingResult<Unit> {
        val body = PairingApi.joinBody(participantId())
        return post("$BASE_URL/sitzung/${code.normalizeCode()}/schliessen", body) { }
    }

    /**
     * Die Kennung dieses Geraets - beim ersten Bedarf erzeugt und danach behalten.
     *
     * Sie stammt aus [SecureRandom] und hat mit dem Geraet nichts zu tun: Sie laesst
     * sich weder auf eine Person noch auf ein Telefon zurueckfuehren, und wer die App
     * loescht, bekommt eine neue.
     */
    override suspend fun participantId(): String = withContext(ioDispatcher) {
        val stored = context.pairingDataStore.data.first()[PARTICIPANT_KEY]
        if (stored != null) return@withContext stored

        val fresh = newParticipantId()
        context.pairingDataStore.edit { it[PARTICIPANT_KEY] = fresh }
        fresh
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun newParticipantId(): String {
        val bytes = ByteArray(PARTICIPANT_BYTES)
        SecureRandom().nextBytes(bytes)
        // Der Dienst laesst nur Buchstaben, Ziffern, Bindestrich und Unterstrich zu.
        return Base64.UrlSafe.encode(bytes).trimEnd('=')
    }

    private suspend fun <T> post(
        url: String,
        body: JsonObject,
        transform: (JsonObject) -> T,
    ): PairingResult<T> = execute(
        Request
            .Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_TYPE))
            .build(),
        transform,
    )

    private suspend fun <T> get(url: String, transform: (JsonObject) -> T): PairingResult<T> =
        execute(
            Request
                .Builder()
                .url(url)
                .get()
                .build(),
            transform,
        )

    private suspend fun <T> execute(
        request: Request,
        transform: (JsonObject) -> T,
    ): PairingResult<T> = withContext(ioDispatcher) {
        try {
            val call = client.newCall(request)
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext PairingResult.Failure(PairingApi.errorFor(response.code))
                }
                val text = response.body?.string().orEmpty()
                val parsed = if (text.isBlank()) {
                    JsonObject(emptyMap())
                } else {
                    json.parseToJsonElement(text).jsonObject
                }
                PairingResult.Success(transform(parsed))
            }
        } catch (exception: IOException) {
            PairingResult.Failure(PairingError.NO_CONNECTION)
        } catch (exception: IllegalArgumentException) {
            // Antwort war kein JSON - der Dienst ist nicht der, den wir erwarten.
            PairingResult.Failure(PairingError.UNKNOWN)
        }
    }

    /** Grossbuchstaben, keine Leerzeichen - so getippt wie gedruckt. */
    private fun String.normalizeCode(): String =
        filter { it.isLetterOrDigit() }.uppercase()

    private companion object {
        val BASE_URL = BuildConfig.PAIRING_URL.trimEnd('/')
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        val PARTICIPANT_KEY = stringPreferencesKey("teilnehmer_kennung")
        const val PARTICIPANT_BYTES = 16
    }
}
