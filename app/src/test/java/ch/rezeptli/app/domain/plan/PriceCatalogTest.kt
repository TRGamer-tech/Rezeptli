package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.IngredientCategory
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.profile.StoreChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PriceCatalogTest {
    private fun item(
        name: String,
        amount: Double?,
        unit: IngredientUnit,
        category: IngredientCategory = IngredientCategory.SONSTIGES,
    ) = ShoppingItem(name = name, matchKey = name.lowercase(), amount = amount, unit = unit, category = category)

    @Test
    fun `rundet auf ganze Packungen auf`() {
        val wahl = PriceCatalog.choose(item("Nudeln", 750.0, IngredientUnit.GRAMM))

        assertEquals(2, wahl.packs)
        assertEquals("2 × 500 g", wahl.text)
    }

    @Test
    fun `eine Packung genuegt fuer kleine Mengen`() {
        val wahl = PriceCatalog.choose(item("Milch", 2.0, IngredientUnit.DEZILITER))

        assertEquals(1, wahl.packs)
        assertEquals("1 l", wahl.text)
    }

    @Test
    fun `ohne Menge wird eine Packung gekauft`() {
        assertEquals(1, PriceCatalog.choose(item("Salz", null, IngredientUnit.NONE)).packs)
    }

    @Test
    fun `kurze Stichwoerter passen nur auf ganze Woerter`() {
        val reis = PriceCatalog.choose(item("Reis", 200.0, IngredientUnit.GRAMM))
        val eier = PriceCatalog.choose(item("Eier", 8.0, IngredientUnit.STUECK))

        assertEquals("1 kg", reis.pack.label)
        assertEquals(2, eier.packs)
    }

    @Test
    fun `Knoblauchzehen werden zu Knollen`() {
        val wahl = PriceCatalog.choose(item("Knoblauch", 12.0, IngredientUnit.ZEHE))

        assertEquals(2, wahl.packs)
        assertEquals("Knolle", wahl.pack.label)
    }

    @Test
    fun `Aldi ist guenstiger als Coop`() {
        val posten = item("Nudeln", 500.0, IngredientUnit.GRAMM)

        val aldi = PriceCatalog.choose(posten, StoreChain.ALDI.priceMultiplier).total
        val coop = PriceCatalog.choose(posten, StoreChain.COOP.priceMultiplier).total

        assertTrue(aldi < coop)
    }

    @Test
    fun `im Laden eines anderen Landes zaehlt auch dessen Preisniveau`() {
        val posten = item("Nudeln", 500.0, IngredientUnit.GRAMM)

        val migros = PriceCatalog.choose(posten, StoreChain.MIGROS.priceMultiplier).total
        val rewe = PriceCatalog.choose(posten, StoreChain.DE_REWE.priceMultiplier).total
        val lidlDeutschland = PriceCatalog.choose(posten, StoreChain.DE_LIDL.priceMultiplier).total

        // Rewe ist in Deutschland, was Migros in der Schweiz ist - nur auf deutschem Preisniveau.
        assertTrue(rewe < migros)
        assertTrue(lidlDeutschland < rewe)
        // Die Menge aendert sich dadurch nicht, nur der Preis.
        assertEquals(
            PriceCatalog.choose(posten, StoreChain.MIGROS.priceMultiplier).packs,
            PriceCatalog.choose(posten, StoreChain.DE_REWE.priceMultiplier).packs,
        )
    }

    @Test
    fun `eine Mahlzeit bezahlt nur ihren Anteil an der Packung`() {
        val anteil = PriceCatalog.fractionalCost(item("Nudeln", 125.0, IngredientUnit.GRAMM))
        val packung = PriceCatalog.choose(item("Nudeln", 125.0, IngredientUnit.GRAMM)).total

        assertTrue(anteil < packung)
        assertTrue(anteil > 0.0)
    }
}
