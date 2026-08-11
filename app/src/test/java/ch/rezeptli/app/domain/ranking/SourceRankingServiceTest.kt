package ch.rezeptli.app.domain.ranking

import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.domain.profile.UserProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SourceRankingServiceTest {
    private val ranking = SourceRankingService()

    private val schweiz = SourceOrigin("swissmilk", Country.SCHWEIZ)
    private val deutschland = SourceOrigin("einfachkochen", Country.DEUTSCHLAND)
    private val oesterreich = SourceOrigin("ichkoche", Country.OESTERREICH)
    private val frankreich = SourceOrigin("cuisineaz", Country.FRANKREICH)
    private val italien = SourceOrigin("cookaround", Country.ITALIEN)

    private val alle = listOf(frankreich, deutschland, italien, schweiz, oesterreich)

    private fun idsFor(profile: UserProfile) = ranking.rank(alle, profile).map { it.sourceId }

    @Test
    fun `wer in der Schweiz kocht, sieht Schweizer Quellen zuerst`() {
        val ids = idsFor(UserProfile(country = Country.SCHWEIZ))

        assertEquals("swissmilk", ids.first())
        // Danach der gleiche Sprachraum, erst dann die uebrigen.
        assertEquals(listOf("einfachkochen", "ichkoche"), ids.subList(1, 3))
        assertEquals(setOf("cuisineaz", "cookaround"), ids.subList(3, 5).toSet())
    }

    @Test
    fun `wer in Deutschland kocht, sieht deutsche Quellen zuerst, dann AT und CH`() {
        val ids = idsFor(UserProfile(country = Country.DEUTSCHLAND))

        assertEquals("einfachkochen", ids.first())
        assertEquals(setOf("ichkoche", "swissmilk"), ids.subList(1, 3).toSet())
    }

    @Test
    fun `wer in Oesterreich kocht, sieht oesterreichische Quellen zuerst`() {
        val ids = idsFor(UserProfile(country = Country.OESTERREICH))

        assertEquals("ichkoche", ids.first())
        assertEquals(setOf("einfachkochen", "swissmilk"), ids.subList(1, 3).toSet())
    }

    @Test
    fun `franzoesische und italienische Quellen laufen auch ohne Interesse mit`() {
        val ids = idsFor(UserProfile(country = Country.SCHWEIZ))

        // Sie stehen nicht zuoberst, verschwinden aber auch nicht.
        assertEquals(5, ids.size)
    }

    @Test
    fun `angegebenes Interesse an italienischer Kueche holt italienische Quellen nach vorn`() {
        val profile = UserProfile(
            country = Country.SCHWEIZ,
            cuisines = setOf(Cuisine.ITALIENISCH),
        )
        val ids = idsFor(profile)

        assertEquals("swissmilk", ids.first())
        // Vor den gleichen Sprachraum, aber nicht vor das eigene Land.
        assertEquals("cookaround", ids[1])
    }

    @Test
    fun `angegebenes Interesse an franzoesischer Kueche wirkt genauso`() {
        val profile = UserProfile(
            country = Country.DEUTSCHLAND,
            cuisines = setOf(Cuisine.FRANZOESISCH),
        )
        val ids = idsFor(profile)

        assertEquals("einfachkochen", ids.first())
        assertEquals("cuisineaz", ids[1])
    }

    @Test
    fun `Interesse an der eigenen Sprachregion verschiebt nichts`() {
        val profile = UserProfile(
            country = Country.SCHWEIZ,
            cuisines = setOf(Cuisine.SCHWEIZER_KLASSIKER, Cuisine.DEUTSCH_OESTERREICHISCH),
        )
        val ids = idsFor(profile)

        assertEquals("swissmilk", ids.first())
        assertEquals(listOf("einfachkochen", "ichkoche"), ids.subList(1, 3))
    }

    @Test
    fun `ohne Angaben bleibt die Reihenfolge des Katalogs erhalten`() {
        val ids = idsFor(UserProfile.EMPTY)

        assertEquals(alle.map { it.sourceId }, ids)
    }

    @Test
    fun `Kuechen ohne Sprachraum aendern die Reihenfolge nicht`() {
        val profile = UserProfile(country = Country.SCHWEIZ, cuisines = setOf(Cuisine.ASIATISCH))

        assertEquals(idsFor(UserProfile(country = Country.SCHWEIZ)), idsFor(profile))
    }

    @Test
    fun `bei gleicher Bewertung bleibt die urspruengliche Reihenfolge stehen`() {
        val profile = UserProfile(country = Country.SCHWEIZ)
        val einmal = ranking.rank(alle, profile)
        val nochmal = ranking.rank(alle, profile)

        assertEquals(einmal, nochmal)
    }

    @Test
    fun `Eintraege ohne bekannte Herkunft landen hinten`() {
        val items = listOf("unbekannt", "schweiz", "frankreich")
        val origins = mapOf("schweiz" to schweiz, "frankreich" to frankreich)

        val sorted = ranking.rankItems(items, UserProfile(country = Country.SCHWEIZ)) { origins[it] }

        assertEquals(listOf("schweiz", "frankreich", "unbekannt"), sorted)
    }

    @Test
    fun `Anderswo als Wohnland bevorzugt den deutschen Sprachraum, ohne die uebrigen zu verlieren`() {
        val ids = idsFor(UserProfile(country = Country.ANDERSWO))

        assertEquals(setOf("einfachkochen", "ichkoche", "swissmilk"), ids.take(3).toSet())
        assertEquals(5, ids.size)
    }
}
