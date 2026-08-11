package ch.rezeptli.app.domain.repository

import ch.rezeptli.app.domain.model.WebRecipe
import ch.rezeptli.app.domain.model.WebSearchResult

/** Warum ein Import nicht geklappt hat - in Kategorien, die die UI erklaeren kann. */
enum class WebImportError {
    NO_CONNECTION,

    /** Die Seite weist automatisierte Zugriffe ab. */
    REJECTED,

    /** Die robots.txt der Seite untersagt genau diesen Abruf. */
    DISALLOWED,

    NOT_FOUND,

    /** Die Seite war erreichbar, enthaelt aber kein maschinenlesbares Rezept. */
    NO_RECIPE_FOUND,

    UNKNOWN,
}

sealed interface WebRecipeResult {
    data class Loaded(val recipe: WebRecipe) : WebRecipeResult

    data class Failed(val error: WebImportError) : WebRecipeResult
}

/** Zugriff auf Rezepte im Web. */
interface WebRecipeRepository {
    /** Durchsucht die Verzeichnisse der angegebenen Quellen. */
    suspend fun search(query: String, sourceIds: Set<String>): List<WebSearchResult>

    /** Laedt ein einzelnes Rezept von seiner Adresse. */
    suspend fun loadRecipe(url: String): WebRecipeResult
}
