package ch.rezeptli.app.domain.shopping

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientCategory
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.ShoppingItem
import ch.rezeptli.app.domain.shopping.UnitConverter.dimension
import javax.inject.Inject

/**
 * Macht aus den Zutaten mehrerer Rezepte eine Einkaufsliste.
 *
 * Gleiche Zutaten werden zusammengefasst - auch ueber Schreibweisen hinweg, weil der
 * Vergleich ueber den normalisierten Namen laeuft ("Rüebli" und "Karotte" sind derselbe
 * Posten). Zusammengezaehlt wird nur innerhalb derselben Groessenart; wo das nicht
 * moeglich ist, bleiben zwei Zeilen stehen statt einer falschen.
 */
class ShoppingListAggregator @Inject constructor() {

    /** Fasst die Zutaten aller [recipes] zu Einkaufsposten zusammen. */
    fun aggregate(recipes: List<Recipe>): List<ShoppingItem> {
        val entries = recipes.flatMap { recipe ->
            recipe.ingredients
                .filter { it.name.isNotBlank() }
                .map { ingredient -> ingredient to recipe.title }
        }

        // "Salz" ohne alles traegt keine Information und darf keine zweite Zeile neben
        // "1 TL Salz" erzeugen. Solche Eintraege werden deshalb erst zugeordnet,
        // wenn feststeht, welche Posten es ueberhaupt gibt.
        val (unspecified, specified) = entries.partition { (ingredient, _) -> ingredient.isUnspecified }

        val items = specified
            .groupBy { (ingredient, _) -> groupKey(ingredient) }
            .map { (_, group) -> toShoppingItem(group) }
            .toMutableList()

        unspecified.forEach { entry ->
            val (ingredient, recipeTitle) = entry
            val index = items.indexOfFirst { it.matchKey == ingredient.matchKey }
            if (index == -1) {
                items.add(toShoppingItem(listOf(entry)))
            } else {
                items[index] = items[index].copy(
                    sourceNote = mergeNotes(items[index].sourceNote, recipeTitle),
                )
            }
        }

        return items.sortedWith(compareBy({ it.category.ordinal }, { it.name.lowercase() }))
    }

    /** Weder Menge noch Einheit - etwa "Salz" oder "Pfeffer". */
    private val Ingredient.isUnspecified: Boolean
        get() = amount == null && unit == IngredientUnit.NONE

    /**
     * Fuegt [additions] in eine bestehende Liste ein.
     *
     * Ein bereits abgehakter Posten wird wieder geoeffnet, sobald er erneut gebraucht
     * wird - sonst steht man im Laden vor einem Haken und hat die Zutat trotzdem nicht.
     */
    fun merge(existing: List<ShoppingItem>, additions: List<ShoppingItem>): List<ShoppingItem> {
        val result = existing.toMutableList()

        additions.forEach { addition ->
            val index = result.indexOfFirst { it.matchesForMerge(addition) }
            if (index == -1) {
                result.add(addition)
            } else {
                result[index] = combine(result[index], addition)
            }
        }

        return result.sortedWith(compareBy({ it.category.ordinal }, { it.name.lowercase() }))
    }

    private fun ShoppingItem.matchesForMerge(other: ShoppingItem): Boolean {
        if (matchKey != other.matchKey) return false
        // Ein Posten ohne Menge und Einheit passt zu jedem gleichnamigen Posten.
        if (isUnspecified || other.isUnspecified) return true
        val sameDimension = unit.dimension == other.unit.dimension
        return if (unit.dimension == UnitDimension.DISCRETE) {
            sameDimension && unit == other.unit
        } else {
            sameDimension
        }
    }

    private val ShoppingItem.isUnspecified: Boolean
        get() = amount == null && unit == IngredientUnit.NONE

    private fun combine(existing: ShoppingItem, addition: ShoppingItem): ShoppingItem {
        // Die Einheit kommt von der Seite, die ueberhaupt eine mitbringt.
        val leading = if (existing.isUnspecified) addition else existing

        val amount = sumAmounts(
            listOfNotNull(
                existing.amount?.let { UnitConverter.toBase(it, existing.unit) },
                addition.amount?.let { UnitConverter.toBase(it, addition.unit) },
            ),
        )

        val (displayAmount, displayUnit) = amount
            ?.let { UnitConverter.fromBase(it, leading.unit.dimension, leading.unit) }
            ?: (null to leading.unit)

        return existing.copy(
            amount = displayAmount,
            unit = displayUnit,
            isChecked = false,
            sourceNote = mergeNotes(existing.sourceNote, addition.sourceNote),
        )
    }

    private fun groupKey(ingredient: Ingredient): String {
        val dimension = ingredient.unit.dimension
        val unitPart = if (dimension == UnitDimension.DISCRETE) ingredient.unit.name else dimension.name
        return "${ingredient.matchKey}|$unitPart"
    }

    private fun toShoppingItem(group: List<Pair<Ingredient, String>>): ShoppingItem {
        val ingredients = group.map { it.first }
        val first = ingredients.first()

        val baseTotal = sumAmounts(
            ingredients.mapNotNull { it.amount?.let { amount -> UnitConverter.toBase(amount, it.unit) } },
        )
        val (amount, unit) = baseTotal
            ?.let { UnitConverter.fromBase(it, first.unit.dimension, first.unit) }
            ?: (null to first.unit)

        val displayName = first.name.trim()

        return ShoppingItem(
            name = displayName,
            matchKey = first.matchKey,
            amount = amount,
            unit = unit,
            category = IngredientCategory.forIngredient(first.canonicalName ?: displayName),
            sourceNote = group.map { it.second }.distinct().filter { it.isNotBlank() }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", "),
        )
    }

    /** `null`, wenn keine einzige Menge angegeben war ("Salz nach Belieben"). */
    private fun sumAmounts(baseAmounts: List<Double>): Double? =
        baseAmounts.takeIf { it.isNotEmpty() }?.sum()

    private fun mergeNotes(first: String?, second: String?): String? {
        val parts = (first.orEmpty().split(", ") + second.orEmpty().split(", "))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }
}

/** Zutat ohne Menge, damit ein manuell erfasster Posten dieselbe Struktur nutzt. */
fun manualShoppingItem(name: String, addedAt: Long): ShoppingItem {
    val cleaned = name.trim()
    return ShoppingItem(
        name = cleaned,
        matchKey = Ingredient(name = cleaned).matchKey,
        unit = IngredientUnit.NONE,
        category = IngredientCategory.forIngredient(cleaned),
        isManual = true,
        addedAt = addedAt,
    )
}
