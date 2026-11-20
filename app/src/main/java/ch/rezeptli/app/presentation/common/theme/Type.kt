package ch.rezeptli.app.presentation.common.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ch.rezeptli.app.R

/*
 * Zwei Schriften, zwei Aufgaben.
 *
 * Fraunces traegt die Titel: eine weiche Antiqua, wie man sie aus Kochbuechern und
 * Foodmagazinen kennt. Sie ist variabel - Staerke, optische Groesse und "Weichheit"
 * (SOFT) sind stufenlos einstellbar. Die App setzt sie ganz weich und ohne die
 * verspielten Wackel-Buchstaben (WONK = 0), damit lange Rezeptnamen ruhig bleiben.
 *
 * Figtree traegt alles andere: Fliesstext, Knoepfe, Zahlen. Eine freundliche,
 * geometrische Grotesk mit gleich breiten Ziffern (tnum) fuer Mengen und Preise.
 *
 * Beide stehen unter der SIL Open Font License; Herkunft und Bearbeitung beschreibt
 * `licenses/README.md`.
 */

/** Ganz weich - die Ecken der Serifen sind gerundet. */
private const val SOFTNESS = 100f

/** Ohne die wackligen Sonderformen von n, m und h. */
private const val NO_WONK = 0f

@OptIn(ExperimentalTextApi::class)
private fun fraunces(weight: FontWeight, opticalSize: Float) = Font(
    resId = R.font.fraunces,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.opticalSizing(opticalSize.sp),
        FontVariation.Setting("SOFT", SOFTNESS),
        FontVariation.Setting("WONK", NO_WONK),
    ),
)

/**
 * Fraunces ist fuer verschiedene Groessen unterschiedlich gezeichnet (optische Groesse):
 * Grosse Titel duerfen feiner und kontrastreicher sein, kleine brauchen kraeftigere
 * Striche. Deshalb drei Familien statt einer.
 */
internal val DisplayFontFamily = FontFamily(
    fraunces(FontWeight.Medium, OPTICAL_DISPLAY),
    fraunces(FontWeight.SemiBold, OPTICAL_DISPLAY),
    fraunces(FontWeight.Bold, OPTICAL_DISPLAY),
)

internal val HeadlineFontFamily = FontFamily(
    fraunces(FontWeight.Medium, OPTICAL_HEADLINE),
    fraunces(FontWeight.SemiBold, OPTICAL_HEADLINE),
    fraunces(FontWeight.Bold, OPTICAL_HEADLINE),
)

internal val TitleFontFamily = FontFamily(
    fraunces(FontWeight.Medium, OPTICAL_TITLE),
    fraunces(FontWeight.SemiBold, OPTICAL_TITLE),
    fraunces(FontWeight.Bold, OPTICAL_TITLE),
)

/** Fuer Bedienelemente, Fliesstext und Zahlen. */
internal val TextFontFamily = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
    Font(R.font.figtree_extrabold, FontWeight.ExtraBold),
)

private const val OPTICAL_DISPLAY = 72f
private const val OPTICAL_HEADLINE = 32f
private const val OPTICAL_TITLE = 20f

/** Ziffern gleicher Breite - Mengen und Preise stehen in Listen sauber untereinander. */
private const val TABULAR_FIGURES = "tnum"

internal val RezeptliTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 48.sp,
        lineHeight = 54.sp,
        letterSpacing = (-0.8).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.6).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = HeadlineFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 31.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = HeadlineFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = HeadlineFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 23.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = TitleFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = TextFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = TextFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = TextFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = TextFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = TextFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.15.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = TextFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = TextFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = TextFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp,
    ),
)

/** Mengenangaben, etwa "2 dl" auf einer Zutatenzeile. */
internal val AmountTextStyle = TextStyle(
    fontFamily = TextFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = TABULAR_FIGURES,
)
