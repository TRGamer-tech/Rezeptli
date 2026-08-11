package ch.rezeptli.app.presentation.common.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ch.rezeptli.app.R

/**
 * Die Schriftrollen des UI-Kits.
 *
 * Grundlage ist Roboto Flex, eine variable Schrift: Staerke, Breite und optische
 * Groesse sind stufenlos einstellbar, statt fuer jede Staerke eine eigene Datei zu
 * brauchen. Genau das verlangt das Kit fuer grosse Titel.
 *
 * Die mitgelieferte Datei ist auf Latein reduziert und um die Achsen erleichtert, die
 * das Design nicht nutzt - 396 KB statt 1.79 MB. Einzelheiten und Lizenz stehen in
 * `licenses/README.md`.
 *
 * "Google Sans" aus dem Kit laesst sich nicht verwenden: Es ist Googles geschuetzte
 * Hausschrift und in fremden Apps nicht lizenziert. Bedienelemente nutzen deshalb
 * ebenfalls Roboto Flex, nur in einer schmaleren optischen Groesse.
 */
private fun robotoFlex(
    weight: FontWeight,
    width: Float = WIDTH_NORMAL,
    opticalSize: Float = OPTICAL_TEXT,
) = Font(
    resId = R.font.roboto_flex,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.width(width),
        FontVariation.opticalSizing(opticalSize.sp),
    ),
)

private const val WIDTH_NORMAL = 100f

/** Grosse Titel stehen etwas enger - das gibt langen Rezeptnamen mehr Platz. */
private const val WIDTH_DISPLAY = 94f

private const val OPTICAL_TEXT = 14f
private const val OPTICAL_DISPLAY = 40f

/** Fuer grosse Titel: kraeftig, eng gefuehrt, in grosser optischer Groesse gezeichnet. */
internal val DisplayFontFamily = FontFamily(
    robotoFlex(FontWeight.Normal, WIDTH_DISPLAY, OPTICAL_DISPLAY),
    robotoFlex(FontWeight.Medium, WIDTH_DISPLAY, OPTICAL_DISPLAY),
    robotoFlex(FontWeight.SemiBold, WIDTH_DISPLAY, OPTICAL_DISPLAY),
    robotoFlex(FontWeight.Bold, WIDTH_DISPLAY, OPTICAL_DISPLAY),
)

/** Fuer Bedienelemente und Fliesstext. */
internal val LabelFontFamily = FontFamily(
    robotoFlex(FontWeight.Normal),
    robotoFlex(FontWeight.Medium),
    robotoFlex(FontWeight.SemiBold),
    robotoFlex(FontWeight.Bold),
)

/** Fuer Mengenangaben - Ziffern gleicher Breite lassen Zutatenlisten ruhiger wirken. */
internal val MonoFontFamily = FontFamily.Monospace

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
