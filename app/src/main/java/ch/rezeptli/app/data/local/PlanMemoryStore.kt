package ch.rezeptli.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ch.rezeptli.app.domain.plan.PlanMemory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.planMemoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "plan")

/**
 * Merkt sich die in dieser Woche verworfenen Vorschlaege als kleine Einstellungsdatei.
 *
 * Es sind hoechstens ein paar Dutzend Adressen, sie gelten bis zum naechsten neu gebauten
 * Plan und haben keine Beziehung zu etwas anderem - dafuer braucht es keine Tabelle.
 */
@Singleton
class PlanMemoryStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlanMemory {
    override suspend fun rejected(): Set<String> =
        runCatching { context.planMemoryDataStore.data.first()[REJECTED] }.getOrNull().orEmpty()

    override suspend fun reject(key: String) {
        context.planMemoryDataStore.edit { it[REJECTED] = it[REJECTED].orEmpty() + key }
    }

    override suspend fun clear() {
        context.planMemoryDataStore.edit { it.remove(REJECTED) }
    }

    private companion object {
        val REJECTED = stringSetPreferencesKey("verworfen")
    }
}
