package ch.rezeptli.app.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class IngredientNameNormalizerTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(
        "Rüebli, rueebli",
        "RUEEBLI, rueebli",
        "Grieß, griess",
        "Griess, griess",
        "Weisse Bohnen, weisse bohnen",
        "'  Mehl  ', mehl",
        "Crème fraîche, creme fraiche",
        "Rote-Bete, rote bete",
    )
    fun `normalisiert Schreibvarianten`(input: String, expected: String) {
        assertEquals(expected, IngredientNameNormalizer.normalize(input))
    }

    @Test
    fun `erkennt Schweizer Begriffe und liefert die hochdeutsche Normalform`() {
        assertEquals("Karotte", SwissIngredientSynonyms.canonicalFor("Rüebli"))
        assertEquals("Paprika", SwissIngredientSynonyms.canonicalFor("Peperoni"))
        assertEquals("Zucchini", SwissIngredientSynonyms.canonicalFor("Zucchetti"))
        assertEquals("Sahne", SwissIngredientSynonyms.canonicalFor("Rahm"))
        assertEquals("Petersilie", SwissIngredientSynonyms.canonicalFor("Peterli"))
    }

    @Test
    fun `loest auch mehrwortige Angaben ueber das Grundwort auf`() {
        assertEquals("Karotte", SwissIngredientSynonyms.canonicalFor("frische Rüebli"))
    }

    @Test
    fun `liefert null wenn kein Synonym hinterlegt ist`() {
        assertNull(SwissIngredientSynonyms.canonicalFor("Mehl"))
        assertNull(SwissIngredientSynonyms.canonicalFor(""))
    }

    @Test
    fun `zwei Schreibweisen derselben Zutat haben denselben Vergleichsschluessel`() {
        val swiss = Ingredient(name = "Rüebli", canonicalName = SwissIngredientSynonyms.canonicalFor("Rüebli"))
        val german = Ingredient(name = "Karotte")

        assertEquals(german.matchKey, swiss.matchKey)
    }
}
