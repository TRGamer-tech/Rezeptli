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

private val LightColors = lightColorScheme(
    primary = HerbGreen40,
    onPrimary = Color.White,
    primaryContainer = HerbGreen90,
    onPrimaryContainer = HerbGreen10,
    secondary = Sage40,
    onSecondary = Color.White,
    secondaryContainer = Sage90,
    onSecondaryContainer = Sage10,
    tertiary = Carrot40,
    onTertiary = Color.White,
    tertiaryContainer = Carrot90,
    onTertiaryContainer = Carrot10,
    error = Tomato40,
    onError = Color.White,
    errorContainer = Tomato90,
    onErrorContainer = Tomato10,
    background = Cream99,
    onBackground = Charcoal10,
    surface = Cream99,
    onSurface = Charcoal10,
    surfaceVariant = Cream90,
    onSurfaceVariant = Stone30,
    outline = Stone50,
    outlineVariant = Stone80,
    surfaceContainer = Cream95,
    surfaceContainerHigh = Cream90,
)

private val DarkColors = darkColorScheme(
    primary = HerbGreen80,
    onPrimary = HerbGreen20,
    primaryContainer = HerbGreen30,
    onPrimaryContainer = HerbGreen90,
    secondary = Sage80,
    onSecondary = Sage20,
    secondaryContainer = Sage30,
    onSecondaryContainer = Sage90,
    tertiary = Carrot80,
    onTertiary = Carrot20,
    tertiaryContainer = Carrot30,
    onTertiaryContainer = Carrot90,
    error = Tomato80,
    onError = Tomato20,
    errorContainer = Tomato30,
    onErrorContainer = Tomato90,
    background = Charcoal10,
    onBackground = Charcoal90,
    surface = Charcoal10,
    onSurface = Charcoal90,
    surfaceVariant = Charcoal30,
    onSurfaceVariant = Stone80,
    outline = Stone50,
    outlineVariant = Charcoal30,
    surfaceContainer = Charcoal20,
    surfaceContainerHigh = Charcoal30,
)

/** Farben, die es im Material-Schema nicht gibt, die Rezeptli aber braucht. */
data class RezeptliAccentColors(
    val swipeYes: Color,
    val swipeNo: Color,
)

private val LightAccents = RezeptliAccentColors(swipeYes = SwipeYes, swipeNo = SwipeNo)

/** Im Dunkelmodus etwas heller, damit die Rueckmeldung auf dunklem Grund lesbar bleibt. */
private val DarkAccents = RezeptliAccentColors(
    swipeYes = Color(0xFF6BD69B),
    swipeNo = Color(0xFFFF8A7A),
)

private val LocalAccentColors = staticCompositionLocalOf { LightAccents }

object RezeptliTheme {
    val accents: RezeptliAccentColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAccentColors.current
}

/**
 * Das Theme der App.
 *
 * Dynamic Color (Material You) wird bewusst nicht verwendet: Die Farbwelt aus Gruen und
 * Orange ist Teil der Identitaet der App, und die Wisch-Rueckmeldung braucht verlaessliche
 * Gruen- und Rottoene, die sich nicht mit dem Hintergrundbild des Geraets aendern.
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
