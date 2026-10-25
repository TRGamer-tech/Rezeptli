package ch.rezeptli.app.domain.plan

/**
 * Liest die Portionsangabe einer Rezeptseite.
 *
 * Die Quellen schreiben alles Moegliche: "4", "4 Portionen", "Fuer 4 Personen", "6-8 Stueck".
 * Bei einem Bereich zaehlt die untere Zahl - lieber etwas zu viel einkaufen als zu wenig
 * ist hier nicht die Absicht, sondern eine verlaessliche, ganze Zahl.
 */
object ServingsParser {
    private val NUMBER = Regex("""\d{1,3}""")

    fun parse(text: String?): Int? =
        text
            ?.let { NUMBER.find(it)?.value?.toIntOrNull() }
            ?.takeIf { it in 1..MAX_SERVINGS }

    /** Mehr als das ist eher eine Stueckzahl ("24 Kekse") als eine Portionsangabe. */
    const val MAX_SERVINGS = 24
}
