package ch.rezeptli.app.presentation.common.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Die wiederkehrenden Oberflaechen-Effekte des Designs.
 *
 * Sie liegen hier als Modifier vor, damit "Glas" und "Leuchten" im Bildschirmcode
 * benannt auftauchen statt als Ansammlung von Rand-, Schatten- und Hintergrundwerten.
 */

/**
 * Eine schwebende Flaeche - Filterleiste, untere Leiste, Dialogkopf.
 *
 * Anmerkung zur Umsetzung: Das UI-Kit beschreibt Glas als Weichzeichnung dessen, was
 * *hinter* der Flaeche liegt. Android kann das erst ab API 31, die App laeuft ab API 26.
 * Statt den Effekt auf neueren Geraeten anders aussehen zu lassen als auf aelteren, wird
 * er hier einheitlich als halbtransparente Flaeche mit Haarlinie umgesetzt. Das ist der
 * gebraeuchliche Ersatz und bleibt auf jedem Geraet gleich.
 */
fun Modifier.glassPanel(
    shape: Shape,
    borderColor: Color? = null,
): Modifier = composed {
    val accents = RezeptliTheme.accents
    this
        .clip(shape)
        .background(accents.glassFill)
        .border(width = 1.dp, color = borderColor ?: accents.hairline, shape = shape)
}

/**
 * Der leuchtende Rand einer Karte.
 *
 * [intensity] zwischen 0 und 1 steuert, wie stark die Karte leuchtet - damit laesst sich
 * das Leuchten an eine Geste koppeln, etwa waehrend des Wischens.
 *
 * [glowColor] ueberschreibt die Akzentfarbe, etwa mit Gruen oder Rot als Rueckmeldung.
 */
fun Modifier.glowRing(
    shape: Shape,
    intensity: Float = 1f,
    glowColor: Color? = null,
    radius: Dp = Tokens.Effect.GlowRadius,
): Modifier = composed {
    val accents = RezeptliTheme.accents
    val colour = glowColor ?: accents.glow
    val clamped = intensity.coerceIn(0f, 1f)

    this
        .shadow(
            elevation = radius * clamped,
            shape = shape,
            clip = false,
            ambientColor = colour,
            spotColor = colour,
        ).border(
            width = 1.dp,
            color = colour.copy(alpha = 0.30f + 0.45f * clamped),
            shape = shape,
        )
}

/** Kartenflaeche mit ruhiger Haarlinie - der Zustand ohne Interaktion. */
fun Modifier.cardSurface(shape: Shape): Modifier = composed {
    val accents = RezeptliTheme.accents
    this
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainer)
        .border(width = 1.dp, color = accents.hairline, shape = shape)
}

/** Nur fuer Vorschauen und Tests sinnvoll: die Mesh-Farbe eines Listenplatzes. */
@Composable
internal fun meshAccentFor(index: Int): Color = Tokens.Mesh.All[index.mod(Tokens.Mesh.All.size)]
