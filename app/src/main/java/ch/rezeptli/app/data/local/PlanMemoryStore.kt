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
 * Merkt sich als kleine Einstellungsdatei, was in dieser Woche verworfen und was in den
 * letzten Wochen gekocht wurde.
 *
 * Es sind hoechstens ein paar Dutzend Adressen und Titel ohne Beziehung zu etwas anderem -
 * dafuer braucht es keine Tabelle. Die gekochten Gerichte stehen bewusst hier und nicht nur
 * am Rezept: Ein Rezept, das bloss fuer den Plan geholt wurde, wird mit dem Plan wieder
 * weggeraeumt, und der Vermerk ginge mit ihm verloren.
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

    override suspend fun clearRejected() {
        context.planMemoryDataStore.edit { it.remove(REJECTED) }
    }

    override suspend fun cooked(): Map<String, Long> =
        CookedEntries.decode(runCatching { context.planMemoryDataStore.data.first()[COOKED] }.getOrNull())

    override suspend fun noteCooked(keys: Collection<String>, at: Long?) {
        context.planMemoryDataStore.edit { stored ->
            stored[COOKED] = CookedEntries.updated(stored[COOKED], keys, at, now = System.currentTimeMillis())
        }
    }

    private companion object {
        val REJECTED = stringSetPreferencesKey("verworfen")
        val COOKED = stringSetPreferencesKey("gekocht")
    }
}
