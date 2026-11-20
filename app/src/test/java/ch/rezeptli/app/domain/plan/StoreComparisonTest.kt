package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.StoreChain
import ch.rezeptli.app.domain.profile.UserProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StoreComparisonTest {
    private fun profil(vararg laeden: StoreChain, land: Country = Country.SCHWEIZ) =
        UserProfile(country = land, stores = laeden.toList())

    @Test
    fun `die anderen Laeden stehen zum Vergleich da - in der Reihenfolge der Auswahl`() {
        val vergleich = StoreComparison.of(profil(StoreChain.COOP, StoreChain.ALDI, StoreChain.MIGROS), cost = 103.0)

        assertEquals(listOf(StoreChain.ALDI, StoreChain.MIGROS), vergleich.map { it.store })
        // Coop liegt 3 % ueber Migros, Aldi 18 % darunter.
        assertEquals(82.0, vergleich[0].cost, 0.001)
        assertEquals(100.0, vergleich[1].cost, 0.001)
    }

    @Test
    fun `der Hauptladen selbst taucht im Vergleich nicht auf`() {
        val vergleich = StoreComparison.of(profil(StoreChain.ALDI, StoreChain.COOP), cost = 82.0)

        assertEquals(listOf(StoreChain.COOP), vergleich.map { it.store })
        assertEquals(103.0, vergleich.single().cost, 0.001)
    }

    @Test
    fun `mit nur einem Laden oder ganz ohne gibt es nichts zu vergleichen`() {
        assertTrue(StoreComparison.of(profil(StoreChain.MIGROS), cost = 90.0).isEmpty())
        assertTrue(StoreComparison.of(profil(), cost = 90.0).isEmpty())
    }

    @Test
    fun `ohne Einkauf gibt es keinen Vergleich`() {
        assertTrue(StoreComparison.of(profil(StoreChain.COOP, StoreChain.ALDI), cost = 0.0).isEmpty())
    }

    @Test
    fun `im Ausland wird zwischen den Laeden des Landes verglichen`() {
        val vergleich = StoreComparison.of(
            profil(StoreChain.DE_REWE, StoreChain.DE_ALDI, land = Country.DEUTSCHLAND),
            cost = 70.0,
        )

        // Beide rechnen mit demselben Preisniveau des Landes - es kuerzt sich heraus.
        assertEquals(61.6, vergleich.single().cost, 0.001)
    }
}
