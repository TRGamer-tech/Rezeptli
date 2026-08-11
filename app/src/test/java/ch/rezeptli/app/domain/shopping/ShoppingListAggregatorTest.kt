package ch.rezeptli.app.domain.shopping

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientCategory
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.SwissIngredientSynonyms
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val DELTA = 0.001

class ShoppingListAggregatorTest {
    private val aggregator = ShoppingListAggregator()

    private fun ingredient(
        name: String,
        amount: Double? = null,
        unit: IngredientUnit = IngredientUnit.NONE,
    ) = Ingredient(
        name = name,
        amount = amount,
        unit = unit,
        canonicalName = SwissIngredientSynonyms.canonicalFor(name),
    )

    private fun recipe(title: String, vararg ingredients: Ingredient) =
        Recipe(title = title, ingredients = ingredients.toList())

    @Test
    fun `zaehlt dieselbe Zutat aus mehreren Rezepten zusammen`() {
        val list = aggregator.aggregate(
            listOf(
                recipe("Rösti", ingredient("Butter", 20.0, IngredientUnit.GRAMM)),
                recipe("Wähe", ingredient("Butter", 130.0, IngredientUnit.GRAMM)),
            ),
        )

        val butter = list.single { it.name == "Butter" }
        assertEquals(150.0, butter.amount!!, DELTA)
        assertEquals(IngredientUnit.GRAMM, butter.unit)
        assertEquals("Rösti, Wähe", butter.sourceNote)
    }

    @Test
    fun `rechnet in die Einheit um die man aufschreiben wuerde`() {
        val list = aggregator.aggregate(
            listOf(
                recipe("A", ingredient("Mehl", 800.0, IngredientUnit.GRAMM)),
                recipe("B", ingredient("Mehl", 700.0, IngredientUnit.GRAMM)),
            ),
        )

        val mehl = list.single()
        assertEquals(1.5, mehl.amount!!, DELTA)
        assertEquals(IngredientUnit.KILOGRAMM, mehl.unit)
    }

    @Test
    fun `fasst Milliliter und Deziliter zusammen`() {
        val list = aggregator.aggregate(
            listOf(
                recipe("A", ingredient("Milch", 2.0, IngredientUnit.DEZILITER)),
                recipe("B", ingredient("Milch", 50.0, IngredientUnit.MILLILITER)),
            ),
        )

        val milch = list.single()
        assertEquals(2.5, milch.amount!!, DELTA)
        assertEquals(IngredientUnit.DEZILITER, milch.unit)
    }

    @Test
    fun `erkennt Schweizer und hochdeutsche Schreibweise als dieselbe Zutat`() {
        val list = aggregator.aggregate(
            listOf(
                recipe("Eintopf", ingredient("Rüebli", 300.0, IngredientUnit.GRAMM)),
                recipe("Salat", ingredient("Karotten", 200.0, IngredientUnit.GRAMM)),
            ),
        )

        val karotten = list.single()
        assertEquals(500.0, karotten.amount!!, DELTA)
        assertEquals("Rüebli", karotten.name, "Angezeigt wird die zuerst erfasste Schreibweise")
    }

    @Test
    fun `haelt unvereinbare Einheiten auseinander statt falsch zu addieren`() {
        val list = aggregator.aggregate(
            listOf(
                recipe("A", ingredient("Olivenöl", 2.0, IngredientUnit.ESSLOEFFEL)),
                recipe("B", ingredient("Olivenöl", 1.0, IngredientUnit.DEZILITER)),
            ),
        )

        assertEquals(2, list.size, "Löffel und Deziliter duerfen nicht vermischt werden")
        assertTrue(list.all { it.name == "Olivenöl" })
    }

    @Test
    fun `laesst Zutaten ohne Menge ohne Menge`() {
        val list = aggregator.aggregate(
            listOf(
                recipe("A", ingredient("Salz")),
                recipe("B", ingredient("Salz")),
            ),
        )

        assertNull(list.single().amount)
    }

    @Test
    fun `uebernimmt die Menge wenn nur ein Rezept eine angibt`() {
        val list = aggregator.aggregate(
            listOf(
                recipe("A", ingredient("Salz")),
                recipe("B", ingredient("Salz", 1.0, IngredientUnit.TEELOEFFEL)),
            ),
        )

        val salz = list.single()
        assertEquals(1.0, salz.amount!!, DELTA)
        assertEquals(IngredientUnit.TEELOEFFEL, salz.unit)
    }

    @Test
    fun `macht aus drei Teeloeffeln einen Essloeffel`() {
        val list = aggregator.aggregate(
            listOf(
                recipe("A", ingredient("Zucker", 2.0, IngredientUnit.TEELOEFFEL)),
                recipe("B", ingredient("Zucker", 1.0, IngredientUnit.TEELOEFFEL)),
            ),
        )

        val zucker = list.single()
        assertEquals(1.0, zucker.amount!!, DELTA)
        assertEquals(IngredientUnit.ESSLOEFFEL, zucker.unit)
    }

    @Test
    fun `bleibt bei Teeloeffeln wenn es nicht glatt aufgeht`() {
        val list = aggregator.aggregate(
            listOf(
                recipe("A", ingredient("Zucker", 2.0, IngredientUnit.TEELOEFFEL)),
                recipe("B", ingredient("Zucker", 2.0, IngredientUnit.TEELOEFFEL)),
            ),
        )

        assertEquals(4.0, list.single().amount!!, DELTA)
        assertEquals(IngredientUnit.TEELOEFFEL, list.single().unit)
    }

    @Test
    fun `sortiert nach Warengruppe`() {
        val list = aggregator.aggregate(
            listOf(
                recipe(
                    "Znacht",
                    ingredient("Salz"),
                    ingredient("Rüebli", 2.0),
                    ingredient("Butter", 50.0, IngredientUnit.GRAMM),
                ),
            ),
        )

        assertEquals(
            listOf(IngredientCategory.GEMUESE, IngredientCategory.MILCHPRODUKTE, IngredientCategory.GEWUERZE_KRAEUTER),
            list.map { it.category },
        )
    }

    @Test
    fun `fuegt neue Posten in eine bestehende Liste ein und oeffnet abgehakte wieder`() {
        val existing = aggregator
            .aggregate(listOf(recipe("A", ingredient("Butter", 100.0, IngredientUnit.GRAMM))))
            .map { it.copy(isChecked = true) }
        val additions = aggregator
            .aggregate(listOf(recipe("B", ingredient("Butter", 50.0, IngredientUnit.GRAMM))))

        val merged = aggregator.merge(existing, additions)

        val butter = merged.single()
        assertEquals(150.0, butter.amount!!, DELTA)
        assertFalse(butter.isChecked, "Wer mehr braucht, darf den Haken nicht behalten")
        assertEquals("A, B", butter.sourceNote)
    }

    @Test
    fun `haengt unbekannte Posten einfach an`() {
        val existing = aggregator.aggregate(listOf(recipe("A", ingredient("Butter", 100.0, IngredientUnit.GRAMM))))
        val additions = aggregator.aggregate(listOf(recipe("B", ingredient("Mehl", 500.0, IngredientUnit.GRAMM))))

        val merged = aggregator.merge(existing, additions)

        assertEquals(setOf("Butter", "Mehl"), merged.map { it.name }.toSet())
    }

    @Test
    fun `ignoriert Zutaten ohne Namen`() {
        val list = aggregator.aggregate(listOf(recipe("A", ingredient("  "), ingredient("Mehl", 100.0))))

        assertEquals(listOf("Mehl"), list.map { it.name })
    }

    @Test
    fun `liefert fuer eine leere Auswahl eine leere Liste`() {
        assertTrue(aggregator.aggregate(emptyList()).isEmpty())
    }
}
