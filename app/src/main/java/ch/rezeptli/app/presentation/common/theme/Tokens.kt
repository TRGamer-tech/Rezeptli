package ch.rezeptli.app.presentation.common.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Die Rohwerte des Designs - eine einzige Quelle der Wahrheit.
 *
 * Hier stehen ausschliesslich Werte, keine Bedeutung: `Purple500` ist eine Farbe, nicht
 * "die Primaerfarbe". Welche Rolle ein Wert im UI spielt, legt [Theme.kt] fest. Dadurch
 * laesst sich die Farbwelt an genau einer Stelle austauschen, ohne dass irgendwo im
 * Bildschirmcode ein Farbwert hart notiert werden muss.
 *
 * Die Werte stammen aus dem UI-Kit und sind bewusst unveraendert uebernommen, damit sie
 * gegen die Vorlage geprueft werden koennen.
 */
object Tokens {
    /** Flaechen von Schwarz bis zur Trennlinie - die Basis des dunklen Designs. */
    object Neutral {
        val Black = Color(0xFF000000)

        /** Haupthintergrund. */
        val Charcoal900 = Color(0xFF1A1A1A)
        val Charcoal850 = Color(0xFF202020)

        /** Kartenhintergrund. */
        val Charcoal800 = Color(0xFF252525)
        val Charcoal750 = Color(0xFF2D2D2D)

        /** Haarlinien und Trennkanten. */
        val Charcoal700 = Color(0xFF333333)
        val White = Color(0xFFFFFFFF)
    }

    /** Die Akzentfarbe der App in ihren Abstufungen. */
    object Purple {
        val Purple100 = Color(0xFFF2E1FA)
        val Purple300 = Color(0xFFD498E6)

        /** Primaerfarbe: Aktionen, Links, Akzente. */
        val Purple500 = Color(0xFFBE70D6)
        val Purple600 = Color(0xFFA65ABF)

        /** Starker Akzent, etwa fuer das Leuchten einer aktiven Karte. */
        val Vivid = Color(0xFFC300FF)
    }

    /** Randfarben. Bewusst halbtransparent, damit sie auf jeder Flaeche funktionieren. */
    object Border {
        val Hairline = Color.White.copy(alpha = 0.15f)
        val Soft = Color.White.copy(alpha = 0.10f)
        val Subtle = Color.White.copy(alpha = 0.08f)
        val Accent = Purple.Purple500.copy(alpha = 0.30f)
    }

    /**
     * Farbverlaeufe fuer Hintergrundakzente und Illustrationen.
     *
     * Aus den HSL-Werten des UI-Kits in sRGB umgerechnet.
     */
    object Mesh {
        val Violet = Color(0xFFBE85FF)
        val Pink = Color(0xFFFF7A93)
        val Orange = Color(0xFFFF8D70)
        val Yellow = Color(0xFFFFE74D)
        val Mint = Color(0xFF8FFFAD)
        val Cyan = Color(0xFF47DAFF)
        val Teal = Color(0xFF7AF2FF)

        /** Reihenfolge fuer wechselnde Akzente, etwa pro Karte im Stapel. */
        val All = listOf(Violet, Pink, Orange, Yellow, Mint, Cyan, Teal)
    }

    /** Eckenradien. Keine Karte hat mehr spitze Ecken. */
    object Radius {
        val Sm: Dp = 5.dp
        val Md: Dp = 10.dp
        val Lg: Dp = 13.dp
        val Xl: Dp = 15.dp
        val Xxl: Dp = 16.dp

        /** Knoepfe und Chips. */
        val Pill: Dp = 30.dp

        val SmShape = RoundedCornerShape(Sm)
        val MdShape = RoundedCornerShape(Md)
        val LgShape = RoundedCornerShape(Lg)
        val XlShape = RoundedCornerShape(Xl)
        val XxlShape = RoundedCornerShape(Xxl)
        val PillShape = RoundedCornerShape(Pill)
        val CircleShape = RoundedCornerShape(percent = 50)
    }

    /** Weichzeichnung der Glas-Flaechen. */
    object Effect {
        val Glass: Dp = 10.dp

        /** Reichweite des farbigen Leuchtens an einer aktiven Karte. */
        val GlowRadius: Dp = 24.dp
    }

    /** Dauer von Bewegungen in Millisekunden. */
    object Duration {
        const val Fast = 200
        const val Base = 300
        const val Slow = 600
        const val Loader = 1000
    }

    /** Beschleunigungskurven. */
    object Ease {
        val OutQuad: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
        val Overshoot: Easing = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)

        /** Verspielt - etwa wenn eine Wischkarte zurueckfedert. */
        val Bounce: Easing = CubicBezierEasing(0.68f, -0.55f, 0.27f, 1.55f)
    }
}
