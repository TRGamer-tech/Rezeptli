package ch.rezeptli.app.presentation.common.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

private val DarkColors = darkColorScheme(
    primary = AccentPrimary,
    onPrimary = OnAccent,
    primaryContainer = AccentPressed,
    onPrimaryContainer = AccentLight,
    secondary = AccentSoft,
    onSecondary = OnAccent,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = AccentLight,
    tertiary = Tokens.Mesh.Teal,
    onTertiary = Tokens.Neutral.Charcoal900,
    tertiaryContainer = SurfaceElevated,
    onTertiaryContainer = TextPrimary,
    error = ErrorColor,
    onError = OnErrorColor,
    errorContainer = ErrorSurface,
    onErrorContainer = Color(0xFFFFDAD6),
    background = SurfaceBackground,
    onBackground = TextPrimary,
    surface = SurfaceBackground,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceLine,
    outlineVariant = Tokens.Border.Hairline,
    surfaceContainer = SurfaceCard,
    surfaceContainerHigh = SurfaceElevated,
    surfaceContainerHighest = SurfaceElevated,
    surfaceContainerLow = SurfaceRaised,
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
    swipeYes = SwipeYes,
    swipeNo = SwipeNo,
    glow = Tokens.Purple.Vivid,
    // Glas ist eine Flaeche, durch die der Hintergrund schimmert - deshalb halbtransparent.
    glassFill = Tokens.Neutral.Charcoal850.copy(alpha = 0.72f),
    hairline = Tokens.Border.Hairline,
    accentBorder = Tokens.Border.Accent,
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
 * Die App ist dunkel angelegt und folgt nicht der Hell-Dunkel-Einstellung des Geraets:
 * Die Farbwelt des UI-Kits ist als dunkles Design definiert, und eine daraus abgeleitete
 * helle Variante waere geraten, nicht vorgegeben.
 *
 * Dynamic Color (Material You) bleibt bewusst aus: Die Wisch-Rueckmeldung braucht
 * verlaessliche Gruen- und Rottoene, die sich nicht mit dem Hintergrundbild des Geraets
 * aendern.
 */
@Composable
fun RezeptliTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAccentColors provides DarkAccents) {
        MaterialTheme(
            colorScheme = DarkColors,
            typography = RezeptliTypography,
            shapes = RezeptliShapes,
            content = content,
        )
    }
}
