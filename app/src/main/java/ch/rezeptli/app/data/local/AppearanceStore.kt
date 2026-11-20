package ch.rezeptli.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import ch.rezeptli.app.domain.settings.AppearanceRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(name = "darstellung")

/** Legt die Darstellungs-Einstellungen in einer eigenen kleinen Datei ab. */
@Singleton
class AppearanceStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppearanceRepository {
    override val reduceMotion: Flow<Boolean> = context.appearanceDataStore.data
        // Eine kaputte Datei darf die App nicht am Start hindern - dann eben volle Bewegung.
        .catch { emit(emptyPreferences()) }
        .map { it[REDUCE_MOTION] ?: false }

    override suspend fun setReduceMotion(reduce: Boolean) {
        context.appearanceDataStore.edit { it[REDUCE_MOTION] = reduce }
    }

    private companion object {
        val REDUCE_MOTION = booleanPreferencesKey("bewegung_reduzieren")
    }
}
