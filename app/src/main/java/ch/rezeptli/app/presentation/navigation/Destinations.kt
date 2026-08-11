package ch.rezeptli.app.presentation.navigation

import android.net.Uri
import ch.rezeptli.app.domain.model.RecipeFilter

/**
 * Alle Ziele der App als String-Routen.
 *
 * Der Filter wird als Query-Parameter mitgegeben, damit eine Swipe-Session nach einem
 * Prozess-Neustart mit demselben Rezept-Pool weiterlaufen kann.
 */
object Destinations {
    const val ARG_RECIPE_ID = "recipeId"
    const val ARG_SESSION_ID = "sessionId"
    const val ARG_QUERY = "query"
    const val ARG_TAGS = "tags"
    const val ARG_MAX_PREP_TIME = "maxPrepTime"
    const val ARG_URL = "url"

    /** Trennzeichen der Tag-Liste in der Route. */
    const val TAG_SEPARATOR = "|"

    const val RECIPE_LIST = "recipes"
    const val RECIPE_DETAIL = "recipes/{$ARG_RECIPE_ID}"
    const val RECIPE_EDIT = "recipes/edit?$ARG_RECIPE_ID={$ARG_RECIPE_ID}"
    const val RECIPE_IMPORT = "import?$ARG_URL={$ARG_URL}"
    const val WEB_SEARCH = "websearch"
    const val SWIPE_SETUP = "swipe/setup"
    const val SWIPE =
        "swipe/session?$ARG_QUERY={$ARG_QUERY}&$ARG_TAGS={$ARG_TAGS}&$ARG_MAX_PREP_TIME={$ARG_MAX_PREP_TIME}"
    const val RESULTS = "results/{$ARG_SESSION_ID}"
    const val SHOPPING_LIST = "shopping"
    const val ONBOARDING = "onboarding"
    const val SETTINGS = "einstellungen"
    const val COOKING = "kochen/{$ARG_RECIPE_ID}"

    fun recipeDetail(recipeId: Long): String = "recipes/$recipeId"

    fun cooking(recipeId: Long): String = "kochen/$recipeId"

    fun recipeEdit(recipeId: Long? = null): String = "recipes/edit?$ARG_RECIPE_ID=${recipeId ?: 0L}"

    fun results(sessionId: Long): String = "results/$sessionId"

    fun recipeImport(url: String? = null): String =
        if (url.isNullOrBlank()) "import?$ARG_URL=" else "import?$ARG_URL=" + Uri.encode(url)

    fun swipe(filter: RecipeFilter): String = buildString {
        append("swipe/session")
        append("?$ARG_QUERY=" + Uri.encode(filter.query))
        append("&$ARG_TAGS=" + Uri.encode(filter.tags.joinToString(TAG_SEPARATOR)))
        append("&$ARG_MAX_PREP_TIME=" + (filter.maxPrepTimeMinutes ?: 0))
    }

    /** Baut den Filter aus den Routen-Argumenten zurueck. */
    fun filterFrom(query: String?, tags: String?, maxPrepTime: Int?): RecipeFilter = RecipeFilter(
        query = query.orEmpty(),
        tags = tags
            ?.split(TAG_SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty(),
        maxPrepTimeMinutes = maxPrepTime?.takeIf { it > 0 },
    )
}
