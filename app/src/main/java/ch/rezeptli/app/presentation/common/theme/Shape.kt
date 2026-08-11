package ch.rezeptli.app.presentation.common.theme

import androidx.compose.material3.Shapes

/**
 * Die Rundungen von Material auf die Radien des UI-Kits abgebildet.
 *
 * Knoepfe und Chips verwenden nicht diese Skala, sondern [Tokens.Radius.PillShape] -
 * Material bezieht die Form von Knoepfen aus `small`, was hier zu wenig waere.
 */
internal val RezeptliShapes = Shapes(
    extraSmall = Tokens.Radius.SmShape,
    small = Tokens.Radius.MdShape,
    medium = Tokens.Radius.LgShape,
    large = Tokens.Radius.XlShape,
    extraLarge = Tokens.Radius.XxlShape,
)
