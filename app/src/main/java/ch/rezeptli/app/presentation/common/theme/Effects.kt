package ch.rezeptli.app.presentation.common.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Die wiederkehrenden Oberflaechen des Designs.
 *
 * Sie liegen hier als Modifier vor, damit "Karte" und "schwebende Flaeche" im
 * Bildschirmcode benannt auftauchen statt als Ansammlung von Rand-, Schatten- und
 * Hintergrundwerten.
 */

/**
 * Eine Karte: im Hellen ein Blatt Papier auf Creme mit weichem, warmem Schatten, im
 * Dunkeln eine leicht hellere Flaeche mit Haarlinie.
 *
 * Der Schatten ist bewusst nicht grau, sondern ein warmes Braun mit wenig Deckkraft -
 * graue Schatten wirken auf Creme schmutzig.
 */
fun Modifier.cardSurface(
    shape: Shape,
    elevation: Dp = Tokens.Elevation.Card,
): Modifier = composed {
    val accents = RezeptliTheme.accents
    val shadowColor = accents.cardShadow.copy(alpha = if (RezeptliTheme.isDark) 0.6f else 0.22f)
    this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = shadowColor,
            spotColor = shadowColor,
        ).clip(shape)
        .background(accents.cardFill)
        .border(width = 1.dp, color = accents.hairline, shape = shape)
}

/**
 * Eine schwebende Flaeche - etwa die Angaben auf einem Foto oder die Steuerung im
 * Kochmodus. Deckend statt durchscheinend: Schrift auf einem halb durchsichtigen Grund
 * ist auf einem bunten Foto nie verlaesslich lesbar.
 */
fun Modifier.floatingPanel(
    shape: Shape,
    borderColor: Color? = null,
): Modifier = composed {
    val accents = RezeptliTheme.accents
    val shadowColor = accents.cardShadow.copy(alpha = if (RezeptliTheme.isDark) 0.7f else 0.28f)
    this
        .shadow(
            elevation = Tokens.Elevation.Floating,
            shape = shape,
            clip = false,
            ambientColor = shadowColor,
            spotColor = shadowColor,
        ).clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = PANEL_OPACITY))
        .border(width = 1.dp, color = borderColor ?: accents.hairline, shape = shape)
}

/**
 * Ein farbiger Rand mit weichem Schein - fuer die aktive Karte oder den laufenden Schritt.
 *
 * [intensity] zwischen 0 und 1 steuert, wie deutlich die Hervorhebung ist. Damit laesst
 * sie sich an eine Geste koppeln, etwa waehrend des Wischens.
 *
 * [color] ueberschreibt die Akzentfarbe, etwa mit Gruen oder Rot als Rueckmeldung.
 */
fun Modifier.highlightRing(
    shape: Shape,
    intensity: Float = 1f,
    color: Color? = null,
    radius: Dp = Tokens.Elevation.Highlight,
): Modifier = composed {
    val accents = RezeptliTheme.accents
    val ringColor = color ?: accents.highlight
    val clamped = intensity.coerceIn(0f, 1f)

    this
        .shadow(
            elevation = radius * clamped,
            shape = shape,
            clip = false,
            ambientColor = ringColor,
            spotColor = ringColor,
        ).border(
            width = 1.dp + 1.dp * clamped,
            color = ringColor.copy(alpha = 0.25f + 0.65f * clamped),
            shape = shape,
        )
}

/** Fast deckend - genug, damit ein Foto darunter nur noch ahnbar ist. */
private const val PANEL_OPACITY = 0.94f
