package ch.rezeptli.app.presentation.common

import ch.rezeptli.app.domain.profile.Currency
import java.util.Locale

/** Schweizer Schreibweise: Punkt als Dezimaltrenner, unabhaengig von der Geraetesprache. */
private val SWISS: Locale = Locale.forLanguageTag("de-CH")

/** Schreibweise im Euroraum: Komma als Dezimaltrenner, das Zeichen hinter der Zahl. */
private val EURO: Locale = Locale.GERMANY

/**
 * Ein Betrag, wie er im jeweiligen Land an der Kasse steht: "CHF 5.20" oder "5,20 €".
 *
 * Die Schreibweise haengt an der Waehrung, nicht an der Geraetesprache - ein auf Englisch
 * gestelltes Telefon in Bern soll trotzdem Franken zeigen, wie man sie dort schreibt.
 */
fun formatMoney(amount: Double, currency: Currency): String = when (currency) {
    Currency.CHF -> "CHF " + String.format(SWISS, "%.2f", amount)
    Currency.EUR -> String.format(EURO, "%.2f", amount) + " €"
}

/** Ein runder Betrag ohne Rappen oder Cent - fuer Budgets und Vergleiche. */
fun formatMoney(amount: Int, currency: Currency): String = when (currency) {
    Currency.CHF -> "CHF $amount"
    Currency.EUR -> "$amount €"
}
