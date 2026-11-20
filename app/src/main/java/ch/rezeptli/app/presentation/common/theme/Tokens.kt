package ch.rezeptli.app.presentation.common.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Die Rohwerte des Designs "Marktfrisch" - eine einzige Quelle der Wahrheit.
 *
 * Hier stehen ausschliesslich Werte, keine Bedeutung: `Herb` ist eine Farbe, nicht "die
 * Primaerfarbe". Welche Rolle ein Wert im UI spielt, legen [Color.kt] und [Theme.kt]
 * fest. So laesst sich die Farbwelt an genau einer Stelle austauschen, ohne dass im
 * Bildschirmcode ein Farbwert hart notiert werden muss.
 *
 * Die Idee dahinter: ein Wochenmarkt. Papier statt Bildschirmgrau, Kraeuter, Tomaten
 * und Butter als Akzente. Die dunkle Fassung ist kein Schwarz, sondern eine Waldnacht -
 * dieselben Farben, nur nach Sonnenuntergang.
 */
object Tokens {
    /** Papier, Tinte und Waldnacht - die Flaechen und Schriften. */
    object Neutral {
        // Hell: Papier und Tinte
        val Cream = Color(0xFFFBF6EC)
        val CreamLow = Color(0xFFF6EFE2)
        val CreamMid = Color(0xFFF2EADB)
        val CreamHigh = Color(0xFFECE3D2)
        val CreamHighest = Color(0xFFE6DCC9)
        val Paper = Color(0xFFFFFFFF)
        val Ink = Color(0xFF1D2A22)
        val InkMuted = Color(0xFF55615A)
        val Pebble = Color(0xFF7C877F)
        val Crumb = Color(0xFFE0D6C3)

        // Dunkel: Waldnacht
        val Forest = Color(0xFF0F1E18)
        val ForestLowest = Color(0xFF0B1813)
        val ForestLow = Color(0xFF14261E)
        val ForestMid = Color(0xFF192C23)
        val ForestHigh = Color(0xFF20352B)
        val ForestHighest = Color(0xFF284034)
        val Moss = Color(0xFF2E463A)
        val Lichen = Color(0xFF7F9688)
        val Mist = Color(0xFFE9F1EA)
        val MistMuted = Color(0xFFB1C4B7)

        /** Abdunklung ueber Fotos - damit weisse Schrift auf jedem Bild lesbar bleibt. */
        val Shade = Color(0xFF0B1410)
    }

    /** Kraeutergruen - die Farbe fuer alles, was man tun kann. */
    object Herb {
        val Base = Color(0xFF2F6B4F)
        val Deep = Color(0xFF0D3B26)
        val Pale = Color(0xFFCFEBD9)
        val Mint = Color(0xFF8FD3A8)
        val MintPale = Color(0xFFBDEFD0)
        val Night = Color(0xFF1E5139)
        val OnMint = Color(0xFF00391F)

        /** Gruen fuer "Ja" auf hellem Grund - 4.7:1 auf Creme. */
        val Yes = Color(0xFF2E7D4F)
    }

    /** Salbei - ruhiges Gruen fuer Ausgewaehltes: Chips, die aktive Leiste, Nebenknoepfe. */
    object Sage {
        val Base = Color(0xFF4F6354)
        val Container = Color(0xFFD8E9DA)
        val OnContainer = Color(0xFF0F2A1B)
        val Night = Color(0xFFB6CCB9)
        val OnNight = Color(0xFF213528)
        val NightContainer = Color(0xFF2A4434)
        val OnNightContainer = Color(0xFFD2E8D5)
    }

    /** Tomate - Herz, Treffer und alles, was Waerme braucht. */
    object Tomato {
        /** Die leuchtende Fassung, nur fuer Flaechen ohne Schrift (Herz, Konfetti). */
        val Vivid = Color(0xFFE4572E)
        val Deep = Color(0xFFB5401C)
        val Pale = Color(0xFFFFDBCD)
        val Ink = Color(0xFF3D0E00)
        val Coral = Color(0xFFFF9B79)
        val CoralSoft = Color(0xFFFF8A65)
        val OnCoral = Color(0xFF5A1800)
        val Night = Color(0xFF7A2D12)

        /** Rot fuer "Nein" auf hellem Grund - 4.7:1 auf Creme. */
        val No = Color(0xFFC2451E)
    }

    /** Butter - Hervorhebungen, Sterne, der Dampf im Logo. */
    object Butter {
        val Base = Color(0xFFF2C14E)
        val Deep = Color(0xFF765800)
        val Pale = Color(0xFFFCE5A6)
        val Ink = Color(0xFF3A2A00)
        val Night = Color(0xFF5A4300)
        val Glow = Color(0xFFFFE08A)
        val OnBase = Color(0xFF3D2E00)
    }

    /** Warnungen und Fehler. */
    object Alert {
        val Light = Color(0xFFB3261E)
        val LightContainer = Color(0xFFF9DEDC)
        val LightOnContainer = Color(0xFF410E0B)
        val Dark = Color(0xFFFFB4AB)
        val DarkOn = Color(0xFF690005)
        val DarkContainer = Color(0xFF93000A)
        val DarkOnContainer = Color(0xFFFFDAD6)
    }

    /**
     * Die Farbpaare fuer Schlagworte (Hintergrund, Schrift), je hell und dunkel.
     *
     * Jedes Paar erreicht mindestens 5.8:1 - auch kleine Schrift auf einem Chip bleibt
     * damit gut lesbar.
     */
    object Tag {
        val HerbLight = Color(0xFFD6EEDD) to Color(0xFF1E5A3B)
        val HerbDark = Color(0xFF1E4A35) to Color(0xFFA8E3BD)
        val TomatoLight = Color(0xFFFFE0D4) to Color(0xFF9A3412)
        val TomatoDark = Color(0xFF5C2412) to Color(0xFFFFB59C)
        val ButterLight = Color(0xFFFCEBB2) to Color(0xFF664A00)
        val ButterDark = Color(0xFF4D3B00) to Color(0xFFFFD970)
        val LakeLight = Color(0xFFD5E8F2) to Color(0xFF1C4D65)
        val LakeDark = Color(0xFF173C4E) to Color(0xFFA6D8F0)
        val BerryLight = Color(0xFFF9D8E1) to Color(0xFF8A2347)
        val BerryDark = Color(0xFF5A1A30) to Color(0xFFFFB1C8)
        val ApricotLight = Color(0xFFFFE4C7) to Color(0xFF844700)
        val ApricotDark = Color(0xFF57340A) to Color(0xFFFFC285)
        val SageLight = Color(0xFFE2EADD) to Color(0xFF3E5142)
        val SageDark = Color(0xFF2C3A30) to Color(0xFFC3D3C4)
    }

    /** Eckenradien. Weich und freundlich - nichts hat eine spitze Ecke. */
    object Radius {
        val Sm: Dp = 8.dp
        val Md: Dp = 12.dp
        val Lg: Dp = 20.dp
        val Xl: Dp = 24.dp
        val Xxl: Dp = 28.dp

        val SmShape = RoundedCornerShape(Sm)
        val MdShape = RoundedCornerShape(Md)
        val LgShape = RoundedCornerShape(Lg)
        val XlShape = RoundedCornerShape(Xl)
        val XxlShape = RoundedCornerShape(Xxl)

        /** Knoepfe und Chips - an beiden Enden ganz rund. */
        val PillShape = RoundedCornerShape(percent = 50)
        val CircleShape = RoundedCornerShape(percent = 50)
    }

    /** Hoehe der Schatten. Das Design setzt Tiefe sparsam ein: Karten heben sich nur leicht ab. */
    object Elevation {
        val Card: Dp = 3.dp
        val Floating: Dp = 10.dp

        /** Reichweite des farbigen Scheins um eine hervorgehobene Karte. */
        val Highlight: Dp = 22.dp
    }

    /** Dauer von Bewegungen in Millisekunden. */
    object Duration {
        /** Bei reduzierter Bewegung: Wechsel werden zur kurzen Blende statt zur Bewegung. */
        const val Instant = 120
        const val Fast = 180
        const val Base = 280
        const val Slow = 480
        const val Loader = 1100

        /** Abstand zwischen zwei Eintraegen, die nacheinander hereinkommen. */
        const val Stagger = 45
    }

    /** Beschleunigungskurven. */
    object Ease {
        /** Fuer Blenden und Farbwechsel. */
        val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

        /** Fuer Dinge, die den Bildschirm verlassen - zuerst langsam, dann weg. */
        val Exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

        /** Schwung, der ueber das Ziel hinausgeht und zurueckkommt. */
        val Overshoot: Easing = CubicBezierEasing(0.175f, 0.885f, 0.32f, 1.275f)
    }

    /** Federn - die Grundlage fuer fast jede Bewegung in der App. */
    object Spring {
        /** Verspielt: schwingt sichtbar nach. Fuer Karten, Icons, Erfolge. */
        const val BouncyDamping = 0.52f
        const val BouncyStiffness = 420f

        /** Knackig: kaum Nachschwingen. Fuer Knoepfe, Leisten, Wechsel. */
        const val SnappyDamping = 0.72f
        const val SnappyStiffness = 700f

        /** Sanft: gleitet ohne Ueberschwingen. Fuer grosse Flaechen. */
        const val GentleDamping = 0.9f
        const val GentleStiffness = 240f
    }
}
