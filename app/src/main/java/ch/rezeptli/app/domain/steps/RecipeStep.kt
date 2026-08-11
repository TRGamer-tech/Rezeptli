package ch.rezeptli.app.domain.steps

/**
 * Ein einzelner Arbeitsschritt eines Rezepts.
 *
 * [timerMinutes] ist gesetzt, wenn im Text eine Wartezeit steht ("20 Minuten backen").
 * Der Kochmodus bietet dann einen Timer an - er startet ihn aber nie von selbst.
 */
data class RecipeStep(
    val id: Long = 0L,
    val recipeId: Long = 0L,
    val position: Int = 0,
    val text: String,
    val timerMinutes: Int? = null,
) {
    val hasTimer: Boolean get() = timerMinutes != null
}
