package ch.rezeptli.app.presentation.common

import java.util.Locale

/** Schweizer Schreibweise: Punkt als Dezimaltrenner, unabhaengig von der Geraetesprache. */
private val SWISS: Locale = Locale.forLanguageTag("de-CH")

/** "5.20" - zwei Nachkommastellen, wie auf dem Kassenzettel. */
fun formatChf(amount: Double): String = String.format(SWISS, "%.2f", amount)
