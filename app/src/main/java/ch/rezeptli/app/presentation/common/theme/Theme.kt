package ch.rezeptli.app.presentation.common.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

private val DarkColors = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkOnAccent,
    primaryContainer = DarkAccentPressed,
    onPrimaryContainer = DarkAccentLight,
    secondary = DarkAccentSoft,
    onSecondary = DarkOnAccent,
    secondaryContainer = DarkSurfaceElevated,
    onSecondaryContainer = DarkAccentLight,
    tertiary = Tokens.Mesh.Teal,
    onTertiary = Tokens.Neutral.Charcoal900,
    tertiaryContainer = DarkSurfaceElevated,
    onTertiaryContainer = DarkTextPrimary,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorSurface,
    onErrorContainer = Color(0xFFFFDAD6),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkBackground,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = Tokens.Border.Hairline,
    surfaceContainerLow = DarkSurfaceRaised,
    surfaceContainer = DarkSurfaceCard,
    surfaceContainerHigh = DarkSurfaceElevated,
    surfaceContainerHighest = DarkSurfaceElevated,
    scrim = Tokens.Neutral.Black,
)

private val LightColors = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightOnAccent,
    primaryContainer = LightAccentContainer,
    onPrimaryContainer = LightOnAccentContainer,
    secondary = LightAccentPressed,
    onSecondary = LightOnAccent,
    secondaryContainer = LightSurfaceElevated,
    onSecondaryContainer = LightOnAccentContainer,
    tertiary = Color(0xFF00696E),
    onTertiary = LightOnAccent,
    tertiaryContainer = Color(0xFFCFF7F9),
    onTertiaryContainer = Color(0xFF002022),
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorSurface,
    onErrorContainer = Color(0xFF410E0B),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightBackground,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    outlineVariant = Color(0xFFE6DAEB),
    surfaceContainerLow = Color(0xFFF7F1F9),
    surfaceContainer = LightSurfaceCard,
    surfaceContainerHigh = LightSurfaceElevated,
    surfaceContainerHighest = LightSurfaceElevated,
    scrim = Tokens.Neutral.Black,
)

/**
 * Werte, die Material nicht kennt, die aber im ganzen Design gebraucht werden.
 *
 * Sie liegen bewusst neben dem Material-Farbschema statt darin: Material haette keinen
 * passenden Platz fuer sie, und ein eigener Zugriffspunkt macht im Bildschirmcode
 * sichtbar, dass hier eine App-eigene Entscheidung sichtbar wird.
 */
data class RezeptliAccentColors(
    val swipeYes: Color,
    val swipeNo: Color,
    val glow: Color,
    val glassFill: Color,
    val hairline: Color,
    val accentBorder: Color,
)

private val DarkAccents = RezeptliAccentColors(
    swipeYes = SwipeYesDark,
    swipeNo = SwipeNoDark,
    glow = Tokens.Purple.Vivid,
    // Glas ist eine Flaeche, durch die der Hintergrund schimmert - deshalb halbtransparent.
    glassFill = Tokens.Neutral.Charcoal850.copy(alpha = 0.72f),
    hairline = Tokens.Border.Hairline,
    accentBorder = Tokens.Border.Accent,
)

/**
 * Im Hellen traegt nicht Weiss die Raender, sondern Schwarz - sonst verschwinden sie.
 * Auch das Leuchten ist zurueckhaltender: Auf hellem Grund wirkt ein starker Schein
 * schnell schmutzig statt leuchtend.
 */
private val LightAccents = RezeptliAccentColors(
    swipeYes = SwipeYesLight,
    swipeNo = SwipeNoLight,
    glow = LightAccent,
    glassFill = Color.White.copy(alpha = 0.78f),
    hairline = Color.Black.copy(alpha = 0.12f),
    accentBorder = LightAccent.copy(alpha = 0.35f),
)

private val LocalAccentColors = staticCompositionLocalOf { DarkAccents }

object RezeptliTheme {
    val accents: RezeptliAccentColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAccentColors.current

    /** Mengenangaben in Ziffern gleicher Breite. */
    val amountStyle: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = AmountTextStyle
}

/**
 * Das Theme der App.
 *
 * Dynamic Color (Material You) bleibt bewusst aus: Die Wisch-Rueckmeldung braucht
 * verlaessliche Gruen- und Rottoene, die sich nicht mit dem Hintergrundbild des
 * Geraets aendern.
 */
@Composable
fun RezeptliTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAccentColors provides if (darkTheme) DarkAccents else LightAccents,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = RezeptliTypography,
            shapes = RezeptliShapes,
            content = content,
        )
    }
}
