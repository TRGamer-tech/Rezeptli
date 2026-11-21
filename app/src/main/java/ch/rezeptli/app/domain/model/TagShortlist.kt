package ch.rezeptli.app.domain.model

/**
 * Welche Schlagworte ein Filter zeigt, solange er eingeklappt ist.
 *
 * Importierte Rezepte bringen die Stichworte ihrer Seite mit. Nach ein paar Dutzend Rezepten
 * stehen hunderte zur Wahl - eine Wand aus Chips, vor der niemand etwas findet. Eingeklappt
 * zeigt der Filter deshalb nur die meistbenutzten; der Rest kommt auf Wunsch dazu.
 *
 * Was angewaehlt ist, bleibt immer sichtbar: Ein Filter, der wirkt, ohne dass man ihn sieht,
 * waere schlimmer als die Wand.
 */
data class TagShortlist(
    /** Die Schlagworte, die gerade zu sehen sind. */
    val visible: List<String>,
    /** Wie viele es insgesamt gibt. */
    val total: Int,
    /** Laesst sich die Liste auf- und zuklappen? Nicht, wenn ohnehin alles Platz hat. */
    val canToggle: Boolean,
) {
    companion object {
        /** So viele Schlagworte stehen eingeklappt da. */
        const val COLLAPSED = 8

        /** Fuer so wenige mehr lohnt das Einklappen nicht - "2 weitere" waere nur ein Umweg. */
        private const val SLACK = 2

        /**
         * [byUse] sind alle Schlagworte, die meistbenutzten zuerst.
         *
         * Eingeklappt bleiben die ersten [limit] und alles Angewaehlte, in dieser Reihenfolge.
         * Aufgeklappt steht alles da, nach dem Alphabet - in einer langen Liste sucht man nach
         * dem Wort, nicht nach der Haeufigkeit.
         */
        fun of(
            byUse: List<String>,
            selected: Set<String>,
            expanded: Boolean,
            limit: Int = COLLAPSED,
        ): TagShortlist {
            if (byUse.size <= limit + SLACK) {
                return TagShortlist(visible = byUse, total = byUse.size, canToggle = false)
            }
            val visible = if (expanded) {
                byUse.sortedWith(String.CASE_INSENSITIVE_ORDER)
            } else {
                byUse.filterIndexed { index, tag -> index < limit || tag in selected }
            }
            return TagShortlist(visible = visible, total = byUse.size, canToggle = true)
        }
    }
}
