package ch.rezeptli.app.presentation.common.theme

import androidx.compose.ui.graphics.Color

/*
 * Die Bedeutung der Farben - welcher Rohwert aus [Tokens] welche Rolle spielt.
 *
 * Das UI-Kit definiert ein dunkles Design. Die helle Fassung ist daraus abgeleitet:
 * gleiche Farbfamilie, aber Flaechen und Schrift getauscht. Weil Violett auf hellem
 * Grund heller wirkt, reichen Purple500 und Purple600 dort nicht aus - sie erreichen
 * nur 4.1:1 gegen den hellen Hintergrund. Die helle Fassung nutzt deshalb eine
 * dunklere Stufe derselben Farbe.
 *
 * Alle Werte sind nachgerechnet, nicht geschaetzt (Verhaeltnisse jeweils gegen den
 * Hintergrund der eigenen Fassung).
 */

// ---------------------------------------------------------------- dunkle Fassung

internal val DarkBackground = Tokens.Neutral.Charcoal900
internal val DarkSurfaceRaised = Tokens.Neutral.Charcoal850
internal val DarkSurfaceCard = Tokens.Neutral.Charcoal800
internal val DarkSurfaceElevated = Tokens.Neutral.Charcoal750
internal val DarkOutline = Tokens.Neutral.Charcoal700

/** 14.9:1 auf Charcoal900. */
internal val DarkTextPrimary = Color(0xFFF5F0F7)

/** 8.7:1 auf Charcoal900. */
internal val DarkTextSecondary = Color(0xFFC4BCC8)

/** 5.4:1 auf Charcoal900. */
internal val DarkAccent = Tokens.Purple.Purple500
internal val DarkAccentPressed = Tokens.Purple.Purple600
internal val DarkAccentSoft = Tokens.Purple.Purple300
internal val DarkAccentLight = Tokens.Purple.Purple100

/** Schrift auf der Akzentflaeche - dunkel, weil Weiss auf Violett nur 3.2:1 erreicht. */
internal val DarkOnAccent = Tokens.Neutral.Charcoal900

internal val DarkError = Color(0xFFFF8A80)
internal val DarkOnError = Color(0xFF3B0906)
internal val DarkErrorSurface = Color(0xFF4E1512)

// ----------------------------------------------------------------- helle Fassung

/** Nicht reines Weiss, sondern ein Hauch der Akzentfarbe darin. */
internal val LightBackground = Color(0xFFFBF8FC)
internal val LightSurfaceCard = Color(0xFFFFFFFF)
internal val LightSurfaceVariant = Color(0xFFF1E8F5)
internal val LightSurfaceElevated = Color(0xFFEDE3F2)
internal val LightOutline = Color(0xFFD5C7DC)

/** 15.9:1 auf dem hellen Hintergrund. */
internal val LightTextPrimary = Color(0xFF231B27)

/** 8.0:1 auf dem hellen Hintergrund. */
internal val LightTextSecondary = Color(0xFF544A59)

/** Dunklere Stufe der Kit-Farbe: 6.8:1 auf hellem Grund, Weiss darauf 7.2:1. */
internal val LightAccent = Color(0xFF7E3A96)
internal val LightAccentPressed = Color(0xFF6F3285)

/** Purple100 aus dem Kit - als helle Akzentflaeche unveraendert brauchbar. */
internal val LightAccentContainer = Tokens.Purple.Purple100
internal val LightOnAccentContainer = Color(0xFF3B1147)
internal val LightOnAccent = Color(0xFFFFFFFF)

internal val LightError = Color(0xFFB3261E)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorSurface = Color(0xFFF9DEDC)

// ------------------------------------------------------------- Wisch-Rueckmeldung

/*
 * Gruen fuer Ja, Rot fuer Nein bleibt erhalten - die Bedeutung ist eingeuebt und soll
 * sich durch den Neuanstrich nicht aendern. Im Dunkeln stammen die Toene aus der
 * Mesh-Palette des Kits; auf hellem Grund waeren sie mit 1.2:1 unlesbar, dort stehen
 * dunklere Fassungen derselben Farben.
 */

internal val SwipeYesDark = Tokens.Mesh.Mint
internal val SwipeNoDark = Tokens.Mesh.Pink

/** 5.1:1 auf hellem Grund. */
internal val SwipeYesLight = Color(0xFF1F7A45)

/** 5.8:1 auf hellem Grund. */
internal val SwipeNoLight = Color(0xFFB92844)
