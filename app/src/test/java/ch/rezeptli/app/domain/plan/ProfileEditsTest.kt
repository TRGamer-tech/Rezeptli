package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Mood
import ch.rezeptli.app.domain.profile.ProfileEdits
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.domain.profile.mainDiet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProfileEditsTest {
    @Test
    fun `hoechstens drei Stimmungen`() {
        var profil = UserProfile()
        listOf(Mood.SCHNELL, Mood.LEICHT, Mood.PROTEIN, Mood.FAKEAWAY).forEach {
            profil = ProfileEdits.toggleMood(profil, it)
        }

        assertEquals(setOf(Mood.SCHNELL, Mood.LEICHT, Mood.PROTEIN), profil.moods)
    }

    @Test
    fun `eine gewaehlte Stimmung laesst sich wieder abwaehlen`() {
        val profil = ProfileEdits.toggleMood(ProfileEdits.toggleMood(UserProfile(), Mood.SCHNELL), Mood.SCHNELL)

        assertTrue(profil.moods.isEmpty())
    }

    @Test
    fun `Ernaehrungsformen schliessen sich aus, kohlenhydratarm bleibt`() {
        var profil = ProfileEdits.toggleDiet(UserProfile(), Diet.KOHLENHYDRATARM)
        profil = ProfileEdits.mainDiet(profil, Diet.VEGAN)
        profil = ProfileEdits.mainDiet(profil, Diet.PESCETARISCH)

        assertEquals(setOf(Diet.KOHLENHYDRATARM, Diet.PESCETARISCH), profil.diets)
        assertEquals(Diet.PESCETARISCH, profil.mainDiet)
        assertNull(ProfileEdits.mainDiet(profil, null).mainDiet)
    }

    @Test
    fun `vierstellige Postleitzahl ergibt die Schweiz, aber ueberschreibt keine Wahl`() {
        assertEquals(Country.SCHWEIZ, ProfileEdits.zip(UserProfile(), "8001").country)
        assertEquals(Country.ITALIEN, ProfileEdits.zip(UserProfile(country = Country.ITALIEN), "8001").country)
    }

    @Test
    fun `Postleitzahl nimmt nur Ziffern`() {
        assertEquals("801", ProfileEdits.zip(UserProfile(), "8a0-1").zip)
        assertEquals("12345", ProfileEdits.zip(UserProfile(), "1234567").zip)
    }

    @Test
    fun `Personenzahl bleibt zwischen 1 und dem Hoechstwert`() {
        assertEquals(1, ProfileEdits.persons(UserProfile(householdSize = 1), -1).householdSize)
        assertEquals(
            UserProfile.MAX_HOUSEHOLD_SIZE,
            ProfileEdits.persons(UserProfile(householdSize = UserProfile.MAX_HOUSEHOLD_SIZE), 1).householdSize,
        )
        assertEquals(3, ProfileEdits.persons(UserProfile(), 1).householdSize)
    }

    @Test
    fun `Kochtage lassen sich wechseln, ohne Auswahl gelten fuenf`() {
        val profil = ProfileEdits.toggleDay(UserProfile(), Weekday.SAMSTAG)

        assertEquals(1, profil.mealsPerWeek)
        assertEquals(UserProfile.DEFAULT_COOK_DAYS, UserProfile().mealsPerWeek)
    }

    @Test
    fun `Budget wird begrenzt`() {
        assertEquals(UserProfile.MAX_BUDGET, ProfileEdits.budget(UserProfile(), 9999).weeklyBudget)
        assertEquals(UserProfile.MIN_BUDGET, ProfileEdits.budget(UserProfile(), 1).weeklyBudget)
    }
}
