package ch.rezeptli.app.domain.model

/**
 * Ein vollstaendiges Rezept inklusive Zutaten.
 *
 * [id] ist 0, solange das Rezept noch nicht gespeichert wurde.
 */
data class Recipe(
    val id: Long = 0L,
    val title: String,
    val instructions: String = "",
    val ingredients: List<Ingredient> = emptyList(),
    val tags: List<String> = emptyList(),
    val prepTimeMinutes: Int? = null,
    val photoUri: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastCookedAt: Long? = null,
    /** Herkunft eines importierten Rezepts. Eigene Rezepte haben keine. */
    val sourceUrl: String? = null,
    val sourceName: String? = null,
)

/**
 * Eine einzelne Zutat eines Rezepts.
 *
 * [amount] ist `null`, wenn keine Menge angegeben ist ("Salz nach Belieben").
 * [canonicalName] haelt - falls bekannt - die hochdeutsche Normalform eines
 * regionalen Begriffs fest (z. B. "Rüebli" -> "Karotte"). Damit lassen sich
 * spaeter Vorrats-Abgleich und Einkaufsliste ueber Synonyme hinweg zusammenfuehren,
 * ohne dass die vom Nutzer eingegebene Schreibweise verloren geht.
 */
data class Ingredient(
    val id: Long = 0L,
    val recipeId: Long = 0L,
    val name: String,
    val amount: Double? = null,
    val unit: IngredientUnit = IngredientUnit.NONE,
    val note: String? = null,
    val canonicalName: String? = null,
    val position: Int = 0,
) {
    /** Name, unter dem diese Zutat mit anderen Zutaten verglichen wird. */
    val matchKey: String
        get() = IngredientNameNormalizer.normalize(canonicalName ?: name)
}

/**
 * Schlanke Projektion fuer Listen- und Swipe-Ansichten. Bewusst ohne Zubereitungstext
 * und ohne Zutaten, damit auch grosse Sammlungen seitenweise geladen werden koennen.
 */
data class RecipeSummary(
    val id: Long,
    val title: String,
    val photoUri: String? = null,
    val prepTimeMinutes: Int? = null,
    val tags: List<String> = emptyList(),
    val lastCookedAt: Long? = null,
)

/** Filter fuer Rezeptliste und Swipe-Session. */
data class RecipeFilter(
    val query: String = "",
    val tags: Set<String> = emptySet(),
    val maxPrepTimeMinutes: Int? = null,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || tags.isNotEmpty() || maxPrepTimeMinutes != null

    companion object {
        val NONE = RecipeFilter()
    }
}

/**
 * Tags, die die App beim Import automatisch vorschlaegt. Nutzer sind nicht darauf
 * beschraenkt - Tags sind freier Text.
 */
object SuggestedTags {
    const val VEGETARISCH = "vegetarisch"
    const val VEGAN = "vegan"
    const val SCHNELL = "schnell"
    const val RESTEVERWERTUNG = "Resteverwertung"
    const val DESSERT = "Dessert"
    const val BACKEN = "Backen"
    const val SUPPE = "Suppe"
    const val SALAT = "Salat"
    const val GUETZLI = "Guetzli"

    val ALL: List<String> =
        listOf(VEGETARISCH, VEGAN, SCHNELL, RESTEVERWERTUNG, DESSERT, BACKEN, SUPPE, SALAT, GUETZLI)
}
