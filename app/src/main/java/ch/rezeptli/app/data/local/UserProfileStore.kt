package ch.rezeptli.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ch.rezeptli.app.domain.profile.Appliance
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.Mood
import ch.rezeptli.app.domain.profile.StoreChain
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.domain.repository.UserProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "profil")

/**
 * Legt das Profil als Einstellungsdatei ab.
 *
 * Bewusst kein weiterer Tabellentyp in der Datenbank: Es sind eine Handvoll Werte ohne
 * Beziehungen, die vollstaendig gelesen und geschrieben werden. Aufzaehlungen werden
 * ueber ihren Namen abgelegt, nicht ueber ihre Position - sonst wuerde das Einfuegen
 * einer neuen Kueche alle bereits gespeicherten Antworten verschieben.
 */
@Singleton
class UserProfileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserProfileRepository {
    override val profile: Flow<UserProfile> = context.profileDataStore.data
        .catch { cause ->
            // Eine beschaedigte Datei darf die App nicht am Start hindern.
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }.map { preferences -> preferences.toProfile() }

    override suspend fun update(transform: (UserProfile) -> UserProfile) {
        context.profileDataStore.edit { preferences ->
            val current = preferences.toProfile()
            val updated = transform(current)

            preferences[FIRST_NAME] = updated.firstName.trim()
            updated.country?.let { preferences[COUNTRY] = it.code } ?: preferences.remove(COUNTRY)
            preferences[CUISINES] = updated.cuisines.mapTo(mutableSetOf()) { it.name }
            preferences[DIETS] = updated.diets.mapTo(mutableSetOf()) { it.name }
            preferences[INTOLERANCES] = updated.intolerances.mapTo(mutableSetOf()) { it.name }
            updated.householdSize
                ?.coerceIn(1, UserProfile.MAX_HOUSEHOLD_SIZE)
                ?.let { preferences[HOUSEHOLD_SIZE] = it }
                ?: preferences.remove(HOUSEHOLD_SIZE)
            preferences[ZIP] = updated.zip.trim()
            updated.store?.let { preferences[STORE] = it.name } ?: preferences.remove(STORE)
            preferences[COOK_DAYS] = updated.cookDays.mapTo(mutableSetOf()) { it.name }
            updated.weeklyBudget?.let { preferences[WEEKLY_BUDGET] = it } ?: preferences.remove(WEEKLY_BUDGET)
            preferences[MOODS] = updated.moods.mapTo(mutableSetOf()) { it.name }
            preferences[APPLIANCES] = updated.appliances.mapTo(mutableSetOf()) { it.name }
            preferences[ONBOARDING_DONE] = updated.onboardingCompleted
        }
    }

    override suspend fun clear() {
        context.profileDataStore.edit { it.clear() }
    }

    private fun Preferences.toProfile() = UserProfile(
        firstName = this[FIRST_NAME].orEmpty(),
        country = Country.fromCode(this[COUNTRY]),
        cuisines = this[CUISINES].orEmpty().mapNotNullTo(linkedSetOf(), Cuisine::fromName),
        diets = this[DIETS].orEmpty().mapNotNullTo(linkedSetOf(), Diet::fromName),
        intolerances = this[INTOLERANCES].orEmpty().mapNotNullTo(linkedSetOf(), Intolerance::fromName),
        householdSize = this[HOUSEHOLD_SIZE],
        zip = this[ZIP].orEmpty(),
        store = StoreChain.fromName(this[STORE]),
        cookDays = this[COOK_DAYS].orEmpty().mapNotNullTo(linkedSetOf(), Weekday::fromName),
        weeklyBudget = this[WEEKLY_BUDGET],
        moods = this[MOODS].orEmpty().mapNotNullTo(linkedSetOf(), Mood::fromName),
        appliances = this[APPLIANCES].orEmpty().mapNotNullTo(linkedSetOf(), Appliance::fromName),
        onboardingCompleted = this[ONBOARDING_DONE] ?: false,
    )

    private companion object {
        val FIRST_NAME = stringPreferencesKey("vorname")
        val COUNTRY = stringPreferencesKey("land")
        val CUISINES = stringSetPreferencesKey("kuechen")
        val DIETS = stringSetPreferencesKey("ernaehrung")
        val INTOLERANCES = stringSetPreferencesKey("unvertraeglichkeiten")
        val HOUSEHOLD_SIZE = intPreferencesKey("haushaltsgroesse")
        val ZIP = stringPreferencesKey("plz")
        val STORE = stringPreferencesKey("laden")
        val COOK_DAYS = stringSetPreferencesKey("kochtage")
        val WEEKLY_BUDGET = intPreferencesKey("wochenbudget")
        val MOODS = stringSetPreferencesKey("stimmungen")
        val APPLIANCES = stringSetPreferencesKey("geraete")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_erledigt")
    }
}
