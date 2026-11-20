package ch.rezeptli.app.domain.plan

/**
 * Liest die Portionsangabe einer Rezeptseite.
 *
 * Die Quellen schreiben alles Moegliche: "4", "4 Portionen", "Fuer 4 Personen", "6-8 Portionen".
 * Bei einem Bereich zaehlt die untere Zahl - lieber etwas zu viel einkaufen als zu wenig
 * ist hier nicht die Absicht, sondern eine verlaessliche, ganze Zahl.
 *
 * Nicht jede Zahl meint Esser: "1 Blech", "12 Stueck" oder "4 Glaeser" sagen, was
 * herauskommt, nicht fuer wie viele es reicht. Solche Angaben gelten als unbekannt - sonst
 * rechnete die App ein Blechkuchen-Rezept fuer vier Personen auf das Vierfache hoch.
 */
object ServingsParser {
    private val NUMBER = Regex("""\d{1,3}""")

    private val NOT_PEOPLE = Regex(
        """\b(st(ue|ü)cke?|stk|bleche?|springform|form|kuchen|torten?|brote?|br(oe|ö)tchen|glas|gl(ae|ä)ser|""" +
            """flaschen?|liter|muffins?|kekse?|guetzli|pl(ae|ä)tzchen|cookies?|waffeln?|laibe?|rollen?|scheiben)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String?): Int? {
        val value = text?.trim().orEmpty()
        if (value.isEmpty() || NOT_PEOPLE.containsMatchIn(value)) return null
        return NUMBER
            .find(value)
            ?.value
            ?.toIntOrNull()
            ?.takeIf { it in 1..MAX_SERVINGS }
    }

    /** Mehr als das ist eher eine Stueckzahl ("24 Kekse") als eine Portionsangabe. */
    const val MAX_SERVINGS = 24
}
