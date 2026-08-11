package ch.rezeptli.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.model.AmountFormatter
import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit

/**
 * Lokalisierte Bezeichnungen der Einheiten.
 *
 * Kurzformen wie "g" oder "dl" sind sprachneutral und kommen direkt aus dem Enum.
 * Ausgeschriebene Einheiten liegen in strings.xml, damit sie uebersetzt werden koennen.
 */
private fun IngredientUnit.labelRes(): Int? = when (this) {
    IngredientUnit.PRISE -> R.string.unit_prise
    IngredientUnit.STUECK -> R.string.unit_stueck
    IngredientUnit.BUND -> R.string.unit_bund
    IngredientUnit.PACKUNG -> R.string.unit_packung
    IngredientUnit.DOSE -> R.string.unit_dose
    IngredientUnit.ZEHE -> R.string.unit_zehe
    IngredientUnit.SCHEIBE -> R.string.unit_scheibe
    IngredientUnit.TASSE -> R.string.unit_tasse
    IngredientUnit.MESSERSPITZE -> R.string.unit_messerspitze
    else -> null
}

/** Kurzbezeichnung einer Einheit fuer Listen und Auswahlfelder. */
@Composable
fun IngredientUnit.label(): String {
    val res = labelRes()
    return when {
        this == IngredientUnit.NONE -> stringResource(R.string.unit_none)
        res != null -> stringResource(res)
        else -> abbreviation
    }
}

/**
 * Eine Zutat als eine Zeile: "200 g Mehl", "2 Eier", "Salz (nach Belieben)".
 */
@Composable
fun Ingredient.displayText(): String {
    val amountText = AmountFormatter.format(amount)
    val unitText = if (unit == IngredientUnit.NONE) "" else unit.label()
    val quantity = listOf(amountText, unitText).filter { it.isNotBlank() }.joinToString(" ")
    val base = listOf(quantity, name).filter { it.isNotBlank() }.joinToString(" ")
    return if (note.isNullOrBlank()) base else "$base ($note)"
}
