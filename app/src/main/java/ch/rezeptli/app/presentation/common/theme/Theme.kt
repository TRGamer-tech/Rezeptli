package ch.rezeptli.app.presentation.common.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * Werte, die Material nicht kennt, die aber im ganzen Design gebraucht werden.
 *
 * Sie liegen bewusst neben dem Material-Farbschema statt darin: Material haette keinen
 * passenden Platz fuer sie, und ein eigener Zugriffspunkt macht im Bildschirmcode
 * sichtbar, dass hier eine App-eigene Entscheidung steht.
 */
@Immutable
data class RezeptliAccentColors(
    val swipeYes: Color,
    val swipeNo: Color,
    /** Fuellung von Karten - im Hellen weisses Papier auf Creme. */
    val cardFill: Color,
    /** Schattenfarbe der Karten: warm statt grau, damit nichts schmutzig wirkt. */
    val cardShadow: Color,
    val hairline: Color,
    /** Farbe fuer hervorgehobene Raender - die aktive Karte, der laufende Schritt. */
    val highlight: Color,
    /** Die leuchtenden Akzente fuer Flaechen ohne Schrift: Herz, Dampf, Konfetti. */
    val tomato: Color,
    val butter: Color,
    val herb: Color,
    /** Abdunklung unter Schrift auf Fotos. */
    val photoShade: Color,
)

private val LightAccents = RezeptliAccentColors(
    swipeYes = Tokens.Herb.Yes,
    swipeNo = Tokens.Tomato.No,
    cardFill = Tokens.Neutral.Paper,
    cardShadow = Color(0xFF6B4A1E),
    hairline = Tokens.Neutral.Crumb,
    highlight = Tokens.Herb.Base,
    tomato = Tokens.Tomato.Vivid,
    butter = Tokens.Butter.Base,
    herb = Tokens.Herb.Base,
    photoShade = Tokens.Neutral.Shade,
)

/**
 * Im Dunkeln traegt kein Schatten die Tiefe - er waere unsichtbar. Karten heben sich
 * dort ueber ihre hellere Flaeche und eine Haarlinie ab.
 */
private val DarkAccents = RezeptliAccentColors(
    swipeYes = Tokens.Herb.Mint,
    swipeNo = Tokens.Tomato.CoralSoft,
    cardFill = Tokens.Neutral.ForestMid,
    cardShadow = Color(0xFF000000),
    hairline = Tokens.Neutral.Moss,
    highlight = Tokens.Herb.Mint,
    tomato = Tokens.Tomato.CoralSoft,
    butter = Tokens.Butter.Base,
    herb = Tokens.Herb.Mint,
    photoShade = Tokens.Neutral.Shade,
)

/** Ein Farbpaar fuer ein Schlagwort: Flaeche und Schrift. */
@Immutable
data class TagColors(val container: Color, val content: Color)

/** Die Schlagwortfarben - jede in einer hellen und einer dunklen Fassung. */
@Immutable
data class RezeptliTagPalette(
    val herb: TagColors,
    val tomato: TagColors,
    val butter: TagColors,
    val lake: TagColors,
    val berry: TagColors,
    val apricot: TagColors,
    val sage: TagColors,
) {
    /** Reihenfolge fuer wechselnde Farben, etwa fuer Rezepte ohne Foto. */
    val all: List<TagColors> get() = listOf(herb, tomato, butter, lake, berry, apricot, sage)
}

private fun Pair<Color, Color>.tag() = TagColors(container = first, content = second)

private val LightTags = RezeptliTagPalette(
    herb = Tokens.Tag.HerbLight.tag(),
    tomato = Tokens.Tag.TomatoLight.tag(),
    butter = Tokens.Tag.ButterLight.tag(),
    lake = Tokens.Tag.LakeLight.tag(),
    berry = Tokens.Tag.BerryLight.tag(),
    apricot = Tokens.Tag.ApricotLight.tag(),
    sage = Tokens.Tag.SageLight.tag(),
)

private val DarkTags = RezeptliTagPalette(
    herb = Tokens.Tag.HerbDark.tag(),
    tomato = Tokens.Tag.TomatoDark.tag(),
    butter = Tokens.Tag.ButterDark.tag(),
    lake = Tokens.Tag.LakeDark.tag(),
    berry = Tokens.Tag.BerryDark.tag(),
    apricot = Tokens.Tag.ApricotDark.tag(),
    sage = Tokens.Tag.SageDark.tag(),
)

private val LocalAccentColors = staticCompositionLocalOf { LightAccents }
private val LocalTagPalette = staticCompositionLocalOf { LightTags }
private val LocalIsDark = staticCompositionLocalOf { false }

object RezeptliTheme {
    val accents: RezeptliAccentColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAccentColors.current

    val tags: RezeptliTagPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalTagPalette.current

    /** Wie sich die App bewegen darf - siehe [MotionScheme]. */
    val motion: MotionScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalMotion.current

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalIsDark.current

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
 * Geraets aendern - und die Marktfarben sind das Gesicht der App.
 *
 * [reduceMotion] kommt aus den App-Einstellungen. Die Android-Einstellung "Animationen
 * entfernen" gilt immer, auch wenn die App-Einstellung aus ist.
 */
@Composable
fun RezeptliTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemReduced = rememberSystemReducedMotion()
    val motion = if (reduceMotion || systemReduced) MotionScheme.Reduced else MotionScheme.Full

    CompositionLocalProvider(
        LocalAccentColors provides if (darkTheme) DarkAccents else LightAccents,
        LocalTagPalette provides if (darkTheme) DarkTags else LightTags,
        LocalIsDark provides darkTheme,
        LocalMotion provides motion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = RezeptliTypography,
            shapes = RezeptliShapes,
            content = content,
        )
    }
}
