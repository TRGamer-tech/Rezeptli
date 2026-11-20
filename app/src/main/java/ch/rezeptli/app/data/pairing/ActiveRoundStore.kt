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
import ch.rezeptli.app.domain.multiplayer.SharedVote
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.roundDataStore: DataStore<Preferences> by preferencesDataStore(name = "runde")

/**
 * Legt die laufende Runde als kleine Einstellungsdatei ab.
 *
 * Die Stimmen stehen als "kennung:1,kennung:0" in einem Feld - es sind hoechstens ein paar
 * Dutzend, und sie werden immer als Ganzes gelesen und geschrieben.
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
            votes = parseVotes(daten[VOTES].orEmpty()),
            expiresAt = daten[EXPIRES] ?: 0L,
        )
    }

    override suspend fun save(round: ActiveRound) {
        context.roundDataStore.edit {
            it[CODE] = round.code
            it[HOST] = round.isHost
            it[VOTES] = round.votes.joinToString(",") { vote -> "${vote.recipeId}:${if (vote.liked) 1 else 0}" }
            it[EXPIRES] = round.expiresAt
        }
    }

    override suspend fun clear() {
        context.roundDataStore.edit { it.clear() }
    }

    private fun parseVotes(text: String): List<SharedVote> = text
        .split(',')
        .mapNotNull { eintrag ->
            val (kennung, mag) = eintrag.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            kennung.toLongOrNull()?.let { SharedVote(recipeId = it, liked = mag == "1") }
        }

    private companion object {
        val CODE = stringPreferencesKey("code")
        val HOST = booleanPreferencesKey("gastgeber")
        val VOTES = stringPreferencesKey("stimmen")
        val EXPIRES = longPreferencesKey("verfaellt_am")
    }
}
