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
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.UserProfile
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
        }.map { preferences ->
            UserProfile(
                firstName = preferences[FIRST_NAME].orEmpty(),
                country = Country.fromCode(preferences[COUNTRY]),
                cuisines = preferences[CUISINES].orEmpty().mapNotNullTo(linkedSetOf(), Cuisine::fromName),
                diets = preferences[DIETS].orEmpty().mapNotNullTo(linkedSetOf(), Diet::fromName),
                intolerances = preferences[INTOLERANCES]
                    .orEmpty()
                    .mapNotNullTo(linkedSetOf(), Intolerance::fromName),
                householdSize = preferences[HOUSEHOLD_SIZE],
                onboardingCompleted = preferences[ONBOARDING_DONE] ?: false,
            )
        }

    override suspend fun update(transform: (UserProfile) -> UserProfile) {
        context.profileDataStore.edit { preferences ->
            val current = UserProfile(
                firstName = preferences[FIRST_NAME].orEmpty(),
                country = Country.fromCode(preferences[COUNTRY]),
                cuisines = preferences[CUISINES].orEmpty().mapNotNullTo(linkedSetOf(), Cuisine::fromName),
                diets = preferences[DIETS].orEmpty().mapNotNullTo(linkedSetOf(), Diet::fromName),
                intolerances = preferences[INTOLERANCES]
                    .orEmpty()
                    .mapNotNullTo(linkedSetOf(), Intolerance::fromName),
                householdSize = preferences[HOUSEHOLD_SIZE],
                onboardingCompleted = preferences[ONBOARDING_DONE] ?: false,
            )
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
            preferences[ONBOARDING_DONE] = updated.onboardingCompleted
        }
    }

    override suspend fun clear() {
        context.profileDataStore.edit { it.clear() }
    }

    private companion object {
        val FIRST_NAME = stringPreferencesKey("vorname")
        val COUNTRY = stringPreferencesKey("land")
        val CUISINES = stringSetPreferencesKey("kuechen")
        val DIETS = stringSetPreferencesKey("ernaehrung")
        val INTOLERANCES = stringSetPreferencesKey("unvertraeglichkeiten")
        val HOUSEHOLD_SIZE = intPreferencesKey("haushaltsgroesse")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_erledigt")
    }
}
