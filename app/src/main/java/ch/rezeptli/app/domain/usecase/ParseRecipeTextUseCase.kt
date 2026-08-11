package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.parser.ParsedRecipe
import ch.rezeptli.app.domain.parser.RecipeTextParser
import javax.inject.Inject

/**
 * Wandelt einen kopierten Rezepttext in einen Vorschlag um.
 *
 * Das Ergebnis wird nie direkt gespeichert - die Import-UI zeigt es zur Kontrolle an.
 */
class ParseRecipeTextUseCase @Inject constructor(
    private val parser: RecipeTextParser,
) {
    operator fun invoke(text: String): ParsedRecipe = parser.parse(text)
}
