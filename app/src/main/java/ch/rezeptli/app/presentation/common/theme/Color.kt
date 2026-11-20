package ch.rezeptli.app.presentation.common.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/*
 * Die Bedeutung der Farben - welcher Rohwert aus [Tokens] welche Rolle spielt.
 *
 * Alle Paare sind nachgerechnet, nicht geschaetzt. Schrift erreicht mindestens 4.5:1
 * gegen ihren Hintergrund (WCAG AA), Bedienelemente wie Rahmen mindestens 3:1:
 *
 *   hell   Tinte auf Creme 13.9:1, gedaempfte Schrift 6.0:1, Kraeutergruen 5.8:1,
 *          Weiss auf Kraeutergruen 6.3:1, Salbei 6.0:1, Tomate 5.3:1
 *   dunkel Nebel auf Waldnacht 15.0:1, gedaempfte Schrift 9.4:1, Minze 9.9:1,
 *          Salbei 10.1:1, Koralle 8.4:1
 *
 * Rollen: Kraeutergruen (primary) fuer Aktionen, Salbei (secondary) fuer Ausgewaehltes -
 * Material faerbt damit Chips, die aktive Leiste und Nebenknoepfe ruhig gruen statt
 * grell -, Tomate (tertiary) fuer Akzente. Butter hat keine Material-Rolle; sie steht
 * in [RezeptliTheme.accents].
 *
 * Die leuchtenden Fassungen (Tomate, Butter) tragen nie Schrift - sie sind fuer das
 * Herz, den Dampf im Logo und das Konfetti da.
 */

internal val LightColors = lightColorScheme(
    primary = Tokens.Herb.Base,
    onPrimary = Tokens.Neutral.Paper,
    primaryContainer = Tokens.Herb.Pale,
    onPrimaryContainer = Tokens.Herb.Deep,
    inversePrimary = Tokens.Herb.Mint,
    secondary = Tokens.Sage.Base,
    onSecondary = Tokens.Neutral.Paper,
    secondaryContainer = Tokens.Sage.Container,
    onSecondaryContainer = Tokens.Sage.OnContainer,
    tertiary = Tokens.Tomato.Deep,
    onTertiary = Tokens.Neutral.Paper,
    tertiaryContainer = Tokens.Tomato.Pale,
    onTertiaryContainer = Tokens.Tomato.Ink,
    error = Tokens.Alert.Light,
    onError = Tokens.Neutral.Paper,
    errorContainer = Tokens.Alert.LightContainer,
    onErrorContainer = Tokens.Alert.LightOnContainer,
    background = Tokens.Neutral.Cream,
    onBackground = Tokens.Neutral.Ink,
    surface = Tokens.Neutral.Cream,
    onSurface = Tokens.Neutral.Ink,
    surfaceVariant = Tokens.Neutral.CreamHigh,
    onSurfaceVariant = Tokens.Neutral.InkMuted,
    surfaceTint = Tokens.Herb.Base,
    inverseSurface = Tokens.Neutral.Ink,
    inverseOnSurface = Tokens.Neutral.Cream,
    outline = Tokens.Neutral.Pebble,
    outlineVariant = Tokens.Neutral.Crumb,
    scrim = Tokens.Neutral.Shade,
    surfaceBright = Tokens.Neutral.Paper,
    surfaceDim = Tokens.Neutral.CreamHighest,
    surfaceContainerLowest = Tokens.Neutral.Paper,
    surfaceContainerLow = Tokens.Neutral.CreamLow,
    surfaceContainer = Tokens.Neutral.CreamMid,
    surfaceContainerHigh = Tokens.Neutral.CreamHigh,
    surfaceContainerHighest = Tokens.Neutral.CreamHighest,
)

internal val DarkColors = darkColorScheme(
    primary = Tokens.Herb.Mint,
    onPrimary = Tokens.Herb.OnMint,
    primaryContainer = Tokens.Herb.Night,
    onPrimaryContainer = Tokens.Herb.MintPale,
    inversePrimary = Tokens.Herb.Base,
    secondary = Tokens.Sage.Night,
    onSecondary = Tokens.Sage.OnNight,
    secondaryContainer = Tokens.Sage.NightContainer,
    onSecondaryContainer = Tokens.Sage.OnNightContainer,
    tertiary = Tokens.Tomato.Coral,
    onTertiary = Tokens.Tomato.OnCoral,
    tertiaryContainer = Tokens.Tomato.Night,
    onTertiaryContainer = Tokens.Tomato.Pale,
    error = Tokens.Alert.Dark,
    onError = Tokens.Alert.DarkOn,
    errorContainer = Tokens.Alert.DarkContainer,
    onErrorContainer = Tokens.Alert.DarkOnContainer,
    background = Tokens.Neutral.Forest,
    onBackground = Tokens.Neutral.Mist,
    surface = Tokens.Neutral.Forest,
    onSurface = Tokens.Neutral.Mist,
    surfaceVariant = Tokens.Neutral.ForestHigh,
    onSurfaceVariant = Tokens.Neutral.MistMuted,
    surfaceTint = Tokens.Herb.Mint,
    inverseSurface = Tokens.Neutral.Mist,
    inverseOnSurface = Tokens.Neutral.Forest,
    outline = Tokens.Neutral.Lichen,
    outlineVariant = Tokens.Neutral.Moss,
    scrim = Tokens.Neutral.Shade,
    surfaceBright = Tokens.Neutral.ForestHighest,
    surfaceDim = Tokens.Neutral.ForestLowest,
    surfaceContainerLowest = Tokens.Neutral.ForestLowest,
    surfaceContainerLow = Tokens.Neutral.ForestLow,
    surfaceContainer = Tokens.Neutral.ForestMid,
    surfaceContainerHigh = Tokens.Neutral.ForestHigh,
    surfaceContainerHighest = Tokens.Neutral.ForestHighest,
)
