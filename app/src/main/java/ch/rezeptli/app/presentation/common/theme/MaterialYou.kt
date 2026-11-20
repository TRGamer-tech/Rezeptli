package ch.rezeptli.app.presentation.common.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Ob das Geraet Farben aus dem Hintergrundbild anbietet - das gibt es ab Android 12. */
val supportsMaterialYou: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Das Farbschema aus dem Hintergrundbild - `null` auf Geraeten, die das nicht kennen. */
@Composable
internal fun materialYouScheme(dark: Boolean): ColorScheme? {
    if (!supportsMaterialYou) return null
    val context = LocalContext.current
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

/**
 * Passt die App-eigenen Farben an ein Schema aus dem Hintergrundbild an.
 *
 * Flaechen, Linien und Hervorhebungen folgen dem Schema - sonst laege gruenes Papier auf
 * einem blauen Hintergrund. Ja und Nein beim Wischen, Herz und Konfetti bleiben, wie sie
 * sind: Gruen heisst Ja, Rot heisst Nein, egal welches Bild auf dem Startbildschirm liegt.
 */
internal fun RezeptliAccentColors.fittedTo(scheme: ColorScheme, dark: Boolean): RezeptliAccentColors = copy(
    cardFill = if (dark) scheme.surfaceContainer else scheme.surfaceContainerLowest,
    hairline = scheme.outlineVariant,
    highlight = scheme.primary,
    herb = scheme.primary,
)
