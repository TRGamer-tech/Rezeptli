package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.IngredientNameNormalizer

/**
 * Ein Name, wie er in einem Rezept steht - eine Zutat, eine Notiz dazu oder der Titel -,
 * zerlegt in Woerter.
 *
 * Dazu kommen die Wortpaare: Zwei aufeinanderfolgende Woerter gelten auch als eines. So
 * heisst "Ziegen-Frischkäse" dasselbe wie "Ziegenfrischkäse", "Crème fraîche" laesst sich
 * als ein Begriff erkennen, und "Mu-Err" ebenso.
 */
internal class Phrase(text: String) {
    val words: List<String> = IngredientNameNormalizer.normalize(text).split(' ').filter { it.length >= MIN_LENGTH }

    val pairs: List<String> = words.zipWithNext { first, second -> first + second }

    private companion object {
        const val MIN_LENGTH = 2
    }
}

/**
 * Eine Liste von Stichwoertern, an der eine Einstufung haengt - samt Ausnahmen.
 *
 * Deutsche Zutaten sind Zusammensetzungen, und nicht jede meint, was in ihr steckt: In
 * "Flammkuchen" steckt "Lamm", in "Zitronenrinde" das "Rind", in "garnieren" die "Niere" und
 * in "Sauerteigansatz" die "Gans". Frueher galt jedes Stichwort irgendwo im Wort - an echten
 * Rezeptseiten gemessen machte das aus Flammkuchen ein Lammgericht und aus gehackten Tomaten
 * Hackfleisch. Darum sagt jedes Stichwort, wie es passen muss:
 *
 * - `=wort` nur als ganzes Wort ("=ente" passt nicht auf "al dente"),
 * - `wort*` am Wortanfang ("lamm*" passt auf "Lammkotelett", nicht auf "Flammkuchen"),
 * - `*wort` am Wortende ("*leber" passt auf "Kalbsleber"),
 * - sonst irgendwo im Wort ("champignon" passt auf "Champignonsuppe").
 *
 * Geschrieben werden die Stichwoerter, wie man sie liest - mit Umlauten; verglichen wird in
 * der geglaetteten Form der Zutatennamen.
 */
internal class WordSet(include: String, except: String = "") {
    private val wanted = patternsOf(include)
    private val unwanted = patternsOf(except)

    /** Trifft die Liste dieses eine Wort? */
    fun matches(word: String): Boolean = wanted.any { it.matches(word) } && unwanted.none { it.matches(word) }

    /** Kommt eines der Stichwoerter in [phrase] vor? */
    fun isIn(phrase: Phrase): Boolean = hitIn(phrase) != null

    /**
     * Das Wort, an dem die Liste in [phrase] anschlaegt - oder `null`.
     *
     * Eine Ausnahme, die auf ein Wortpaar passt, nimmt beide Woerter aus: In "Oliven-Öl"
     * zaehlt "Oliven" nicht, und ein "Eierschwammerl-Gulasch" ist kein Gulasch.
     */
    fun hitIn(phrase: Phrase): String? {
        val words = phrase.words
        // Ein Wort, auf das eine Ausnahme passt, zaehlt nicht - auch nicht als Teil eines Paars.
        val excepted = BooleanArray(words.size) { index -> unwanted.any { it.matches(words[index]) } }
        phrase.pairs.forEachIndexed { index, pair ->
            if (unwanted.any { it.matches(pair) }) {
                excepted[index] = true
                excepted[index + 1] = true
            }
        }
        words.forEachIndexed { index, word ->
            if (!excepted[index] && wanted.any { it.matches(word) }) return word
        }
        phrase.pairs.forEachIndexed { index, pair ->
            if (!excepted[index] && !excepted[index + 1] && wanted.any { it.matches(pair) }) return pair
        }
        return null
    }

    private class Pattern(spec: String) {
        private val mode: Mode = when {
            spec.startsWith("=") -> Mode.WHOLE
            spec.endsWith("*") -> Mode.START
            spec.startsWith("*") -> Mode.END
            else -> Mode.ANYWHERE
        }
        private val text: String = IngredientNameNormalizer.normalize(spec.trim('=', '*')).replace(" ", "")

        val isEmpty: Boolean get() = text.isEmpty()

        fun matches(word: String): Boolean = when (mode) {
            Mode.WHOLE -> word == text
            Mode.START -> word.startsWith(text)
            Mode.END -> word.endsWith(text)
            Mode.ANYWHERE -> word.contains(text)
        }
    }

    private enum class Mode { WHOLE, START, END, ANYWHERE }

    private companion object {
        fun patternsOf(list: String): List<Pattern> = list
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map(::Pattern)
            .filterNot { it.isEmpty }
    }
}
