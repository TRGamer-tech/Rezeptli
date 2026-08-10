package ch.rezeptli.app.presentation.common.theme

import androidx.compose.ui.graphics.Color

/**
 * Die Farbwelt von Rezeptli: Kraeutergruen als Leitfarbe, warmes Orange als Akzent und
 * ein cremiger, leicht warmer Hintergrund. Das soll nach Kueche und frischem Gemuese
 * aussehen und nicht nach Standard-Compose-Demo.
 *
 * Alle Kombinationen aus Text- und Hintergrundfarbe erfuellen mindestens ein
 * Kontrastverhaeltnis von 4.5:1 gemaess den Material-Design-Richtlinien zur
 * Barrierefreiheit.
 */

// Primaer: Kraeutergruen
internal val HerbGreen40 = Color(0xFF2E6B4F)
internal val HerbGreen80 = Color(0xFF8FD6AE)
internal val HerbGreen10 = Color(0xFF00210F)
internal val HerbGreen30 = Color(0xFF13543A)
internal val HerbGreen90 = Color(0xFFAAF2C8)
internal val HerbGreen20 = Color(0xFF003921)

// Sekundaer: gedaempftes Salbeigruen
internal val Sage40 = Color(0xFF4F6354)
internal val Sage80 = Color(0xFFB6CCB9)
internal val Sage10 = Color(0xFF0C1F14)
internal val Sage30 = Color(0xFF374B3D)
internal val Sage90 = Color(0xFFD2E8D5)
internal val Sage20 = Color(0xFF213528)

// Akzent: warmes Karotten-Orange
internal val Carrot40 = Color(0xFF8B5000)
internal val Carrot80 = Color(0xFFFFB865)
internal val Carrot10 = Color(0xFF2C1600)
internal val Carrot30 = Color(0xFF693C00)
internal val Carrot90 = Color(0xFFFFDCBB)
internal val Carrot20 = Color(0xFF4A2800)

// Fehlerfarben
internal val Tomato40 = Color(0xFFBA1A1A)
internal val Tomato80 = Color(0xFFFFB4AB)
internal val Tomato10 = Color(0xFF410002)
internal val Tomato30 = Color(0xFF93000A)
internal val Tomato90 = Color(0xFFFFDAD6)
internal val Tomato20 = Color(0xFF690005)

// Neutrale Flaechen - leicht waermer als reines Grau
internal val Cream99 = Color(0xFFFFF8F0)
internal val Cream95 = Color(0xFFF3EDE4)
internal val Cream90 = Color(0xFFE4DED5)
internal val Charcoal10 = Color(0xFF12140F)
internal val Charcoal20 = Color(0xFF272A24)
internal val Charcoal30 = Color(0xFF3D4139)
internal val Charcoal90 = Color(0xFFE3E3DB)
internal val Charcoal95 = Color(0xFFF1F1E9)
internal val Stone30 = Color(0xFF44483E)
internal val Stone50 = Color(0xFF74796C)
internal val Stone80 = Color(0xFFC5C8BB)

/** Rueckmeldung waehrend der Wisch-Geste: gruen fuer Ja, rot fuer Nein. */
internal val SwipeYes = Color(0xFF2E7D4F)
internal val SwipeNo = Color(0xFFC0392B)
