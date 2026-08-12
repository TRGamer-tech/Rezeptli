package ch.rezeptli.app.domain.model

import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.ranking.SourceOrigin
import ch.rezeptli.app.domain.translate.SourceLanguage

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
    /**
     * Die Schritte, wie die Quelle sie selbst gegliedert hat.
     *
     * Leer, wenn die Seite die Zubereitung als einen Block liefert - dann teilt sie
     * spaeter der InstructionSplitter auf.
     */
    val instructionSteps: List<String> = emptyList(),
    val imageUrl: String? = null,
    val totalMinutes: Int? = null,
    val servings: String? = null,
    val keywords: List<String> = emptyList(),
    val sourceUrl: String,
    val sourceName: String,
    /**
     * Die Sprache der Quelle, sofern sie nicht Deutsch ist.
     *
     * Steht hier etwas, kann die App das Rezept beim Import uebersetzen.
     */
    val sourceLanguage: SourceLanguage? = null,
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
    /** Woher die Quelle stammt - Grundlage der Reihenfolge in der Trefferliste. */
    val country: Country,
) {
    val origin: SourceOrigin get() = SourceOrigin(sourceId = sourceId, country = country)
}
