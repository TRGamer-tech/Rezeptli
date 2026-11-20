package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.IngredientNameNormalizer
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.UserProfile

/**
 * Was ein Rezept erfuellen muss, um gezeigt zu werden: Ernaehrung, Unvertraeglichkeiten
 * und Abneigungen.
 *
 * Gilt fuer den Wochenplan, den Wischstapel und die gemeinsame Runde gleich - damit
 * "vegetarisch", "keine Nuesse" und "kein Fisch" ueberall dasselbe heissen.
 *
 * Die drei Teile bedeuten Verschiedenes, und das muss auch die Oberflaeche zeigen:
 * [diets] ist eine Bedingung ("nur vegan"), [intolerances] und [dislikes] sind Ausschluesse
 * ("ohne Gluten", "kein Fisch"). Wer das unter einer gemeinsamen Ueberschrift mischt,
 * bekommt bei "Vegan" das Gegenteil von dem, was gemeint war.
 */
data class DietaryFilter(
    val diets: Set<Diet> = emptySet(),
    val intolerances: Set<Intolerance> = emptySet(),
    val dislikes: Set<Dislike> = emptySet(),
    /** Eigene Stichworte, etwa "Rosinen". */
    val customDislikes: List<String> = emptyList(),
) {
    /** Ohne Angaben muss nichts geprueft werden - dann gibt es auch nichts zu laden. */
    val isActive: Boolean
        get() = diets.isNotEmpty() || intolerances.isNotEmpty() || dislikes.isNotEmpty() || customDislikes.isNotEmpty()

    fun allows(traits: RecipeTraits): Boolean =
        diets.all { satisfies(traits, it) } &&
            intolerances.none { it in traits.allergens } &&
            dislikes.none { it in traits.dislikes } &&
            customKeys.none { key -> matches(traits.words, key) }

    /**
     * Beide Wuensche zusammen - fuer die gemeinsame Runde.
     *
     * Alles wird vereinigt: Was eine Person nicht isst, kommt fuer beide nicht auf den
     * Tisch. Bei der Ernaehrung gewinnt damit von selbst die strengere Form, denn ein
     * Rezept muss jede genannte erfuellen.
     */
    operator fun plus(other: DietaryFilter): DietaryFilter = DietaryFilter(
        diets = diets + other.diets,
        intolerances = intolerances + other.intolerances,
        dislikes = dislikes + other.dislikes,
        customDislikes = (customDislikes + other.customDislikes).distinctBy(IngredientNameNormalizer::normalize),
    )

    private val customKeys: List<String>
        get() = customDislikes.map(IngredientNameNormalizer::normalize).filter { it.isNotEmpty() }

    private fun satisfies(traits: RecipeTraits, diet: Diet): Boolean = when (diet) {
        Diet.VEGAN -> traits.diet == DietClass.VEGAN
        Diet.VEGETARISCH -> traits.diet == DietClass.VEGAN || traits.diet == DietClass.VEGETARISCH
        Diet.PESCETARISCH -> traits.diet != DietClass.FLEISCH
        Diet.KOHLENHYDRATARM -> traits.lowCarb
    }

    /**
     * Ein Stichwort trifft, wenn es in einem Wort des Rezepts vorkommt: "rosinen" trifft
     * "rosinenbroetchen". Bei mehreren Woertern ("rote bete") muessen alle vorkommen.
     */
    private fun matches(words: Set<String>, key: String): Boolean =
        key.split(' ').all { part -> words.any { it.contains(part) } }

    companion object {
        fun of(profile: UserProfile) = DietaryFilter(
            diets = profile.diets,
            intolerances = profile.intolerances,
            dislikes = profile.dislikes,
            customDislikes = profile.customDislikes,
        )
    }
}
