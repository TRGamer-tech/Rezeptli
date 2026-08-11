package ch.rezeptli.app.domain.model

/**
 * Ein Rezept, wie es von einer Website gelesen wurde - noch unverarbeitet.
 *
 * Die Zutaten sind bewusst noch reiner Text ("250 g Mehl"): Sie laufen anschliessend
 * durch denselben Parser wie ein von Hand eingefuegtes Rezept, damit Import und
 * Copy-Paste zum selben Ergebnis fuehren.
 */
data class WebRecipe(
    val title: String,
    val ingredientLines: List<String> = emptyList(),
    val instructions: String = "",
    val imageUrl: String? = null,
    val totalMinutes: Int? = null,
    val servings: String? = null,
    val keywords: List<String> = emptyList(),
    val sourceUrl: String,
    val sourceName: String,
) {
    val isUsable: Boolean
        get() = title.isNotBlank() && (ingredientLines.isNotEmpty() || instructions.isNotBlank())
}

/** Ein Suchtreffer aus einer Web-Quelle, bevor das Rezept geladen wurde. */
data class WebSearchResult(
    val title: String,
    val url: String,
    val sourceId: String,
    val sourceName: String,
)
