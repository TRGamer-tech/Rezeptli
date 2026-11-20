package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Currency
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Market
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.Mood
import ch.rezeptli.app.domain.profile.ProfileEdits
import ch.rezeptli.app.domain.profile.StoreChain
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
    fun `der Name wird gekuerzt, nicht abgelehnt`() {
        assertEquals("Mara", ProfileEdits.name(UserProfile(), "Mara").firstName)
        assertEquals(40, ProfileEdits.name(UserProfile(), "x".repeat(200)).firstName.length)
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
    fun `ohne Auswahl gelten fuenf Kochtage mit Mittag- und Abendessen`() {
        val profil = UserProfile()

        assertEquals(Weekday.entries.take(5), profil.days)
        assertEquals(listOf(MealSlot.MITTAG, MealSlot.ABEND), profil.slots)
        assertEquals(10, profil.mealsPerWeek)
    }

    @Test
    fun `ein Kochtag wird zu den geltenden dazugenommen - nicht statt ihrer`() {
        // Angezeigt sind Montag bis Freitag. Wer den Samstag antippt, will sechs Tage, nicht einen.
        val profil = ProfileEdits.toggleDay(UserProfile(), Weekday.SAMSTAG)

        assertEquals(Weekday.entries.take(6), profil.days)
        assertEquals(
            Weekday.entries.take(5) - Weekday.MITTWOCH,
            ProfileEdits.toggleDay(UserProfile(), Weekday.MITTWOCH).days,
        )
    }

    @Test
    fun `der letzte Kochtag laesst sich nicht abwaehlen`() {
        val nurMontag = UserProfile(cookDays = setOf(Weekday.MONTAG))

        assertEquals(listOf(Weekday.MONTAG), ProfileEdits.toggleDay(nurMontag, Weekday.MONTAG).days)
    }

    @Test
    fun `Mittag oder Abend laesst sich abwaehlen, aber nie beides`() {
        val nurAbends = ProfileEdits.toggleSlot(UserProfile(), MealSlot.MITTAG)

        assertEquals(listOf(MealSlot.ABEND), nurAbends.slots)
        assertEquals(5, nurAbends.mealsPerWeek)
        // Die letzte Mahlzeit bleibt - ein Plan ohne Mahlzeiten waere keiner.
        assertEquals(listOf(MealSlot.ABEND), ProfileEdits.toggleSlot(nurAbends, MealSlot.ABEND).slots)
        assertEquals(listOf(MealSlot.MITTAG, MealSlot.ABEND), ProfileEdits.toggleSlot(nurAbends, MealSlot.MITTAG).slots)
    }

    @Test
    fun `Budget wird begrenzt`() {
        assertEquals(UserProfile.MAX_BUDGET, ProfileEdits.budget(UserProfile(), 9999).weeklyBudget)
        assertEquals(UserProfile.MIN_BUDGET, ProfileEdits.budget(UserProfile(), 1).weeklyBudget)
    }

    @Test
    fun `hoechstens drei Laeden, und der zuerst gewaehlte ist der Hauptladen`() {
        var profil = UserProfile()
        listOf(StoreChain.COOP, StoreChain.ALDI, StoreChain.MIGROS, StoreChain.DENNER).forEach {
            profil = ProfileEdits.toggleStore(profil, it)
        }

        assertEquals(listOf(StoreChain.COOP, StoreChain.ALDI, StoreChain.MIGROS), profil.stores)
        assertEquals(StoreChain.COOP, profil.store)
    }

    @Test
    fun `wird der Hauptladen abgewaehlt, rueckt der naechste nach`() {
        val profil = UserProfile(stores = listOf(StoreChain.COOP, StoreChain.ALDI))

        assertEquals(StoreChain.ALDI, ProfileEdits.toggleStore(profil, StoreChain.COOP).store)
        assertNull(ProfileEdits.toggleStore(ProfileEdits.toggleStore(profil, StoreChain.COOP), StoreChain.ALDI).store)
    }

    @Test
    fun `ein gewaehlter Laden laesst sich zum Hauptladen machen, ohne die anderen zu verlieren`() {
        val profil = UserProfile(stores = listOf(StoreChain.COOP, StoreChain.ALDI, StoreChain.MIGROS))

        val umgestellt = ProfileEdits.mainStore(profil, StoreChain.MIGROS)

        assertEquals(listOf(StoreChain.MIGROS, StoreChain.COOP, StoreChain.ALDI), umgestellt.stores)
        // Ein Laden, der gar nicht gewaehlt ist, wird dadurch nicht heimlich aufgenommen.
        assertEquals(profil, ProfileEdits.mainStore(profil, StoreChain.VOLG))
    }

    @Test
    fun `mit dem Hauptladen rechnet die App - ohne Laden mit dem ueblichen Supermarkt des Landes`() {
        assertEquals(1.0, UserProfile().priceFactor)
        assertEquals(
            StoreChain.ALDI.priceFactor,
            UserProfile(stores = listOf(StoreChain.ALDI, StoreChain.COOP)).priceFactor,
        )
        assertEquals(Market.DEUTSCHLAND.priceLevel, UserProfile(country = Country.DEUTSCHLAND).priceFactor)
        assertEquals(
            Market.DEUTSCHLAND.priceLevel * StoreChain.DE_LIDL.priceFactor,
            UserProfile(country = Country.DEUTSCHLAND, stores = listOf(StoreChain.DE_LIDL)).priceFactor,
        )
    }

    @Test
    fun `das Land bestimmt Laeden, Waehrung und das uebliche Budget`() {
        val schweiz = UserProfile(country = Country.SCHWEIZ)
        val deutschland = UserProfile(country = Country.DEUTSCHLAND)

        assertTrue(StoreChain.MIGROS in schweiz.market.stores)
        assertTrue(StoreChain.DE_REWE in deutschland.market.stores)
        assertTrue(StoreChain.MIGROS !in deutschland.market.stores)
        assertEquals(Currency.CHF, schweiz.market.currency)
        assertEquals(Currency.EUR, deutschland.market.currency)
        assertEquals(Market.DEUTSCHLAND.defaultBudget, deutschland.budget)
        // Ohne Landesangabe gilt die Schweiz - dort ist die App zu Hause.
        assertEquals(Market.SCHWEIZ, UserProfile().market)
        // Jedes Land hat Laeden zur Auswahl - auch "Anderswo".
        Country.entries.forEach { land -> assertTrue(Market.of(land).stores.size >= 3, land.name) }
    }

    @Test
    fun `wer das Land wechselt, behaelt keine Laeden, die es dort nicht gibt`() {
        val schweiz = UserProfile(country = Country.SCHWEIZ, stores = listOf(StoreChain.MIGROS, StoreChain.COOP))

        val umgezogen = ProfileEdits.country(schweiz, Country.OESTERREICH)

        assertEquals(Country.OESTERREICH, umgezogen.country)
        assertTrue(umgezogen.stores.isEmpty())
        // Ein Laden aus einem anderen Land laesst sich auch nicht nachtraeglich waehlen.
        assertTrue(ProfileEdits.toggleStore(umgezogen, StoreChain.MIGROS).stores.isEmpty())
        assertEquals(listOf(StoreChain.AT_HOFER), ProfileEdits.toggleStore(umgezogen, StoreChain.AT_HOFER).stores)
    }

    @Test
    fun `wechselt mit dem Land die Waehrung, gilt wieder das uebliche Budget`() {
        val schweiz = ProfileEdits.budget(UserProfile(country = Country.SCHWEIZ), 150)

        // 150 Franken sind nicht 150 Euro.
        val deutschland = ProfileEdits.country(schweiz, Country.DEUTSCHLAND)
        assertNull(deutschland.weeklyBudget)
        assertEquals(Market.DEUTSCHLAND.defaultBudget, deutschland.budget)

        // Von Euro zu Euro bleibt, was jemand eingestellt hat.
        val mitBudget = ProfileEdits.budget(deutschland, 110)
        assertEquals(110, ProfileEdits.country(mitBudget, Country.OESTERREICH).budget)
    }

    @Test
    fun `ein zweiter Tipp auf dasselbe Land hebt die Wahl auf`() {
        val profil = ProfileEdits.country(UserProfile(), Country.ITALIEN)

        assertNull(ProfileEdits.country(profil, Country.ITALIEN).country)
    }

    @Test
    fun `Abneigungen lassen sich an- und abwaehlen`() {
        val profil = ProfileEdits.toggleDislike(ProfileEdits.toggleDislike(UserProfile(), Dislike.FISCH), Dislike.PILZE)

        assertEquals(setOf(Dislike.FISCH, Dislike.PILZE), profil.dislikes)
        assertEquals(setOf(Dislike.PILZE), ProfileEdits.toggleDislike(profil, Dislike.FISCH).dislikes)
    }

    @Test
    fun `ein eigenes Stichwort wird gesaeubert und nur einmal aufgenommen`() {
        var profil = ProfileEdits.addCustomDislike(UserProfile(), "  Rosinen ")
        profil = ProfileEdits.addCustomDislike(profil, "rosinen")
        profil = ProfileEdits.addCustomDislike(profil, "Rote Bete")

        assertEquals(listOf("Rosinen", "Rote Bete"), profil.customDislikes)
        assertEquals(listOf("Rote Bete"), ProfileEdits.removeCustomDislike(profil, "Rosinen").customDislikes)
    }

    @Test
    fun `ein zu kurzes Stichwort wird nicht angenommen - es traefe zu viel`() {
        // "ei" steckt in "Reis", "Brei" und "Speise".
        assertTrue(ProfileEdits.addCustomDislike(UserProfile(), "Ei").customDislikes.isEmpty())
        assertTrue(ProfileEdits.addCustomDislike(UserProfile(), "   ").customDislikes.isEmpty())
    }

    @Test
    fun `die Zahl eigener Stichworte ist begrenzt`() {
        var profil = UserProfile()
        (1..30).forEach { profil = ProfileEdits.addCustomDislike(profil, "Zutat Nummer $it") }

        assertEquals(UserProfile.MAX_CUSTOM_DISLIKES, profil.customDislikes.size)
    }
}
