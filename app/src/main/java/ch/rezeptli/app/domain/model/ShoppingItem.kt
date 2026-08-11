package ch.rezeptli.app.domain.model

/**
 * Ein Posten auf der Einkaufsliste.
 *
 * [matchKey] ist der normalisierte Vergleichsname, unter dem gleiche Zutaten aus
 * verschiedenen Rezepten zusammengefasst werden. [sourceNote] haelt fest, aus welchen
 * Rezepten der Posten stammt - beim Einkaufen ist das die Antwort auf "wofür war das
 * nochmal?".
 */
data class ShoppingItem(
    val id: Long = 0L,
    val name: String,
    val matchKey: String,
    val amount: Double? = null,
    val unit: IngredientUnit = IngredientUnit.NONE,
    val category: IngredientCategory = IngredientCategory.SONSTIGES,
    val isChecked: Boolean = false,
    val isManual: Boolean = false,
    val sourceNote: String? = null,
    val addedAt: Long = 0L,
)
