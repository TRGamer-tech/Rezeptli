package ch.rezeptli.app.presentation.common.theme

import androidx.compose.ui.graphics.Color

/*
 * Die Bedeutung der Farben - welcher Rohwert aus [Tokens] welche Rolle spielt.
 *
 * Das Design ist dunkel angelegt: anthrazitfarbene Flaechen, Violett als Akzent. Alle
 * hier gewaehlten Kombinationen aus Text und Untergrund erreichen mindestens ein
 * Kontrastverhaeltnis von 4.5:1 (Purple500 auf Charcoal900: 5.4:1, Purple100 auf
 * Charcoal800: 12.5:1, Charcoal900 auf Purple500: 5.4:1).
 */

// Flaechen
internal val SurfaceBackground = Tokens.Neutral.Charcoal900
internal val SurfaceRaised = Tokens.Neutral.Charcoal850
internal val SurfaceCard = Tokens.Neutral.Charcoal800
internal val SurfaceElevated = Tokens.Neutral.Charcoal750
internal val SurfaceLine = Tokens.Neutral.Charcoal700

// Schrift auf dunklem Grund
internal val TextPrimary = Color(0xFFF5F0F7)
internal val TextSecondary = Color(0xFFC4BCC8)

// Akzent
internal val AccentPrimary = Tokens.Purple.Purple500
internal val AccentPressed = Tokens.Purple.Purple600
internal val AccentSoft = Tokens.Purple.Purple300
internal val AccentLight = Tokens.Purple.Purple100

/** Schrift auf der Akzentflaeche - dunkel, weil Weiss auf Violett zu schwach kontrastiert. */
internal val OnAccent = Tokens.Neutral.Charcoal900

// Fehler
internal val ErrorColor = Color(0xFFFF8A80)
internal val OnErrorColor = Color(0xFF3B0906)
internal val ErrorSurface = Color(0xFF4E1512)

/**
 * Rueckmeldung waehrend der Wisch-Geste.
 *
 * Gruen fuer Ja, Rot fuer Nein bleibt erhalten - die Bedeutung ist eingeuebt und soll
 * sich durch den Neuanstrich nicht aendern. Die Toene stammen aber aus der Mesh-Palette
 * des UI-Kits, damit sie zum neuen Design passen.
 */
internal val SwipeYes = Tokens.Mesh.Mint
internal val SwipeNo = Tokens.Mesh.Pink
