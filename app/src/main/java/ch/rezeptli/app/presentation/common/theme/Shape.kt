package ch.rezeptli.app.presentation.common.theme

import androidx.compose.material3.Shapes

/**
 * Die Rundungen von Material auf die Radien des Designs abgebildet.
 *
 * Knoepfe und Chips verwenden nicht diese Skala, sondern [Tokens.Radius.PillShape] -
 * an beiden Enden ganz rund, wie ein Etikett am Marktstand.
 */
internal val RezeptliShapes = Shapes(
    extraSmall = Tokens.Radius.SmShape,
    small = Tokens.Radius.MdShape,
    medium = Tokens.Radius.LgShape,
    large = Tokens.Radius.XlShape,
    extraLarge = Tokens.Radius.XxlShape,
)
