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
import ch.rezeptli.app.domain.multiplayer.RoundOffer
import ch.rezeptli.app.domain.multiplayer.RoundSource
import ch.rezeptli.app.domain.multiplayer.RoundState
import ch.rezeptli.app.domain.multiplayer.SharedContent
import ch.rezeptli.app.domain.multiplayer.SharedVote
import ch.rezeptli.app.domain.plan.CourseChoice
import ch.rezeptli.app.domain.plan.DietaryFilter
import ch.rezeptli.app.domain.repository.PairingRepository
import ch.rezeptli.app.domain.repository.PairingRepository.Companion.NO_ROUND
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

    override suspend fun createRound(restrictions: DietaryFilter): PairingResult<RoundState> =
        post("$BASE_URL/sitzung", PairingApi.createBody(participantId(), restrictions), PairingApi::stateFrom)

    override suspend fun joinRound(
        code: String,
        restrictions: DietaryFilter,
        knownRound: Int,
    ): PairingResult<RoundState> {
        val cleaned = code.normalizeCode()
        if (cleaned.isEmpty()) return PairingResult.Failure(PairingError.UNKNOWN_CODE)

        val body = PairingApi.joinBody(participantId(), restrictions, knownRound)
        return post("$BASE_URL/sitzung/$cleaned/beitreten", body, PairingApi::stateFrom)
    }

    override suspend fun configureRound(
        code: String,
        target: Int,
        course: CourseChoice,
        source: RoundSource,
    ): PairingResult<RoundState> {
        val body = PairingApi.settingsBody(participantId(), target, course, source)
        return post("${roundUrl(code)}/einstellungen", body, PairingApi::stateFrom)
    }

    override suspend fun startRound(code: String): PairingResult<RoundState> =
        post("${roundUrl(code)}/start", PairingApi.plainBody(participantId(), NO_ROUND), PairingApi::stateFrom)

    override suspend fun contribute(
        code: String,
        round: Int,
        offers: List<RoundOffer>,
        knownRound: Int,
    ): PairingResult<RoundState> {
        val body = PairingApi.contributionBody(participantId(), round, offers, knownRound)
        return post("${roundUrl(code)}/beitrag", body, PairingApi::stateFrom)
    }

    override suspend fun sendVotes(code: String, votes: List<SharedVote>, knownRound: Int): PairingResult<RoundState> =
        post(
            "${roundUrl(code)}/stimmen",
            PairingApi.votesBody(participantId(), votes, knownRound),
            PairingApi::stateFrom,
        )

    override suspend fun continueRound(code: String, knownRound: Int): PairingResult<RoundState> =
        post("${roundUrl(code)}/weiter", PairingApi.plainBody(participantId(), knownRound), PairingApi::stateFrom)

    override suspend fun endRound(code: String): PairingResult<RoundState> =
        post("${roundUrl(code)}/beenden", PairingApi.plainBody(participantId(), NO_ROUND), PairingApi::stateFrom)

    override suspend fun closeRound(code: String): PairingResult<Unit> =
        post("${roundUrl(code)}/schliessen", PairingApi.plainBody(participantId(), NO_ROUND)) { }

    override suspend fun roundState(code: String, revision: Int?, knownRound: Int): PairingResult<RoundState?> {
        // Die Kennung gehoert in die Adresse: Den Stand bekommt nur, wer zur Runde gehoert.
        val url = "${roundUrl(code)}?teilnehmer=${participantId()}&runde=$knownRound" +
            (revision?.let { "&stand=$it" } ?: "")
        return get(url) { body -> if (PairingApi.isUnchanged(body)) null else PairingApi.stateFrom(body) }
    }

    override suspend fun shareContent(code: String, recipeId: Long, content: SharedContent): PairingResult<Unit> =
        post("${roundUrl(code)}/inhalt", PairingApi.contentBody(participantId(), recipeId, content)) { }

    override suspend fun fetchContent(code: String, recipeId: Long): PairingResult<SharedContent?> =
        get("${roundUrl(code)}/inhalt?teilnehmer=${participantId()}&rezept=$recipeId", PairingApi::contentFrom)

    private fun roundUrl(code: String): String = "$BASE_URL/sitzung/${code.normalizeCode()}"

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
        /** Die Runde spricht Fassung 2 des Dienstes - Lobby, gemeinsames Ziel, Stimmen waehrend des Wischens. */
        val BASE_URL = BuildConfig.PAIRING_URL.trimEnd('/') + "/v2"
        val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        val PARTICIPANT_KEY = stringPreferencesKey("teilnehmer_kennung")
        const val PARTICIPANT_BYTES = 16
    }
}
