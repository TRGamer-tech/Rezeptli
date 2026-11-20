package ch.rezeptli.app.data.pairing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ch.rezeptli.app.domain.multiplayer.ActiveRound
import ch.rezeptli.app.domain.multiplayer.ActiveRoundRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.roundDataStore: DataStore<Preferences> by preferencesDataStore(name = "runde")

/**
 * Legt die laufende Runde als kleine Einstellungsdatei ab: Code, Rolle, Ablauf.
 *
 * Aeltere Fassungen der App haben hier auch die eigenen Stimmen abgelegt. Das Feld wird
 * nicht mehr gelesen - die Stimmen liegen beim Dienst - und verschwindet mit dem naechsten
 * [clear].
 */
@Singleton
class ActiveRoundStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActiveRoundRepository {
    override suspend fun load(): ActiveRound? {
        val daten = runCatching { context.roundDataStore.data.first() }.getOrNull() ?: return null
        val code = daten[CODE] ?: return null
        return ActiveRound(
            code = code,
            isHost = daten[HOST] ?: false,
            expiresAt = daten[EXPIRES] ?: 0L,
        )
    }

    override suspend fun save(round: ActiveRound) {
        context.roundDataStore.edit {
            it[CODE] = round.code
            it[HOST] = round.isHost
            it[EXPIRES] = round.expiresAt
        }
    }

    override suspend fun clear() {
        context.roundDataStore.edit { it.clear() }
    }

    private companion object {
        val CODE = stringPreferencesKey("code")
        val HOST = booleanPreferencesKey("gastgeber")
        val EXPIRES = longPreferencesKey("verfaellt_am")
    }
}
