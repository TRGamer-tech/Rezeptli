package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.UserProfile

/**
 * Was ein Rezept erfuellen muss, um gezeigt zu werden: Ernaehrung und Unvertraeglichkeiten.
 *
 * Gilt fuer den Wochenplan, den Wischstapel und die gemeinsame Runde gleich - damit
 * "vegetarisch" und "keine Nuesse" ueberall dasselbe heissen.
 */
data class DietaryFilter(
    val diets: Set<Diet> = emptySet(),
    val intolerances: Set<Intolerance> = emptySet(),
) {
    /** Ohne Angaben muss nichts geprueft werden - dann gibt es auch nichts zu laden. */
    val isActive: Boolean get() = diets.isNotEmpty() || intolerances.isNotEmpty()

    fun allows(traits: RecipeTraits): Boolean =
        diets.all { satisfies(traits, it) } && intolerances.none { it in traits.allergens }

    private fun satisfies(traits: RecipeTraits, diet: Diet): Boolean = when (diet) {
        Diet.VEGAN -> traits.diet == DietClass.VEGAN
        Diet.VEGETARISCH -> traits.diet == DietClass.VEGAN || traits.diet == DietClass.VEGETARISCH
        Diet.PESCETARISCH -> traits.diet != DietClass.FLEISCH
        Diet.KOHLENHYDRATARM -> traits.lowCarb
    }

    companion object {
        fun of(profile: UserProfile) = DietaryFilter(profile.diets, profile.intolerances)
    }
}
