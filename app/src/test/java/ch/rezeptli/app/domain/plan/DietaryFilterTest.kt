package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Intolerance
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DietaryFilterTest {
    private fun traits(diet: DietClass, vararg allergens: Intolerance) = RecipeTraits(
        diet = diet,
        allergens = allergens.toSet(),
        moods = emptySet(),
        requiredAppliances = emptySet(),
        lowCarb = false,
    )

    @Test
    fun `ohne Angaben ist der Filter aus und laesst alles durch`() {
        val filter = DietaryFilter()

        assertFalse(filter.isActive)
        assertTrue(filter.allows(traits(DietClass.FLEISCH, Intolerance.NUESSE)))
    }

    @Test
    fun `Allergien schliessen aus`() {
        val filter = DietaryFilter(intolerances = setOf(Intolerance.NUESSE))

        assertFalse(filter.allows(traits(DietClass.VEGAN, Intolerance.NUESSE)))
        assertTrue(filter.allows(traits(DietClass.VEGAN, Intolerance.GLUTEN)))
    }

    @Test
    fun `vegetarisch laesst vegan durch, aber keinen Fisch`() {
        val filter = DietaryFilter(diets = setOf(Diet.VEGETARISCH))

        assertTrue(filter.allows(traits(DietClass.VEGAN)))
        assertFalse(filter.allows(traits(DietClass.FISCH)))
    }
}
