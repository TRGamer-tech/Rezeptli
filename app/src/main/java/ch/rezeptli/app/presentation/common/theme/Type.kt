package ch.rezeptli.app.presentation.common.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Die drei Schriftrollen des UI-Kits.
 *
 * Das Kit nennt "Roboto Flex" fuer grosse Titel und "Google Sans" fuer Bedienelemente.
 * Beide liegen dem Projekt nicht bei: Google Sans ist eine geschuetzte Hausschrift und
 * darf in fremden Apps nicht verwendet werden, Roboto Flex waere zwar frei lizenziert,
 * wuerde die App aber um rund 1.7 MB vergroessern oder eine Abhaengigkeit zu den
 * Play-Diensten verlangen.
 *
 * Bis das entschieden ist, zeigen alle drei Rollen auf die Systemschrift. Die Rollen
 * existieren aber bereits als eigene Werte, sodass ein Wechsel spaeter genau hier
 * stattfindet und nicht in jedem Bildschirm.
 */
internal val DisplayFontFamily = FontFamily.SansSerif
internal val LabelFontFamily = FontFamily.SansSerif

/** Fuer Mengenangaben - Ziffern gleicher Breite lassen Zutatenlisten ruhiger wirken. */
internal val MonoFontFamily = FontFamily.Monospace

/**
 * Grosse Titel sind bewusst kraeftig und eng gefuehrt; sobald eine variable Schrift
 * eingebunden ist, uebernehmen deren Achsen fuer Staerke und Breite diese Rolle.
 */
internal val RezeptliTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 27.sp,
        lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = LabelFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = LabelFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = LabelFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = LabelFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = LabelFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

/** Mengenangaben, etwa "2 dl" auf einer Zutatenzeile. */
internal val AmountTextStyle = TextStyle(
    fontFamily = MonoFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)
