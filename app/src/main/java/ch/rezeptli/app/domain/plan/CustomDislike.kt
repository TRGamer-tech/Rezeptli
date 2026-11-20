package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.IngredientNameNormalizer
import ch.rezeptli.app.domain.model.SwissIngredientSynonyms
import ch.rezeptli.app.domain.profile.Dislike

/**
 * Ein eigenes Stichwort unter "Mag ich nicht" - etwa "Rosinen".
 *
 * Frueher traf ein Stichwort nur, wenn es Buchstabe fuer Buchstabe in einem Wort des Rezepts
 * stand: "Pilze" traf weder "Steinpilz" noch "Champignons", "Nüsse" nicht die "Haselnuss" und
 * "Rüebli" kein Rezept, das "Karotten" schreibt. Jetzt wird grosszuegiger verglichen:
 *
 * - Einzahl und Mehrzahl sind dasselbe ("Rosinen" trifft "Rosine", "Nüsse" die "Haselnuss").
 * - Schweizer und hochdeutsche Namen sind dasselbe ("Rüebli" trifft "Karotten").
 * - Wer eine ganze Gruppe eintippt, fuer die es einen Knopf gibt ("Pilze", "Fisch"), meint
 *   die Gruppe - samt Champignons, Schwammerl und Thon. Siehe [category].
 */
internal class CustomDislike(text: String) {
    private val key: String = IngredientNameNormalizer.normalize(text)

    /** Die Schreibweisen, unter denen das Stichwort gesucht wird - jede als Liste von Wortteilen. */
    private val variants: List<List<String>> = buildList {
        add(partsOf(key))
        SwissIngredientSynonyms.canonicalFor(text)?.let { add(partsOf(IngredientNameNormalizer.normalize(it))) }
    }.filter { it.isNotEmpty() }.distinct()

    val isEmpty: Boolean get() = variants.isEmpty()

    /** Die Abneigung, die gemeint ist, wenn das Stichwort eine ganze Gruppe nennt. */
    val category: Dislike? = CATEGORIES[stemOf(fold(key))]

    /**
     * Steht das Stichwort im Rezept? [words] sind die Woerter aus Titel, Zutaten und Notizen.
     *
     * Bei mehreren Woertern ("rote bete") muessen alle vorkommen.
     */
    fun isIn(words: Set<String>): Boolean {
        if (variants.isEmpty()) return false
        val folded = words.map(::fold)
        return variants.any { parts -> parts.all { part -> folded.any { it.contains(part) } } }
    }

    private fun partsOf(normalized: String): List<String> =
        normalized.split(' ').filter { it.isNotEmpty() }.map { stemOf(fold(it)) }

    internal companion object {
        /** Kuerzer wird kein Wortstamm - sonst traefe "Eier" jeden "Reis". */
        private const val MIN_STEM = 4

        /**
         * Die Endungen der Mehrzahl. "-er" fehlt mit Absicht: Es ist selten eine Mehrzahl und
         * meist Teil des Worts - aus "Butter" wuerde sonst "Butt", und der ist ein Fisch.
         */
        private val ENDINGS = listOf("en", "e", "n", "s")

        /**
         * Glaettet die Umlaute ganz weg: "nuesse" und "nuss" haben danach denselben Stamm.
         * Verglichen wird immer Geglaettetes mit Geglaettetem.
         */
        fun fold(word: String): String = word.replace("ae", "a").replace("oe", "o").replace("ue", "u")

        /** Der Wortstamm ohne Mehrzahl-Endung - soweit danach genug uebrig bleibt. */
        fun stemOf(word: String): String {
            val ending = ENDINGS.firstOrNull { word.endsWith(it) && word.length - it.length >= MIN_STEM }
            return if (ending == null) word else word.dropLast(ending.length)
        }

        /** Wer das eintippt, meint die ganze Gruppe - geschrieben als geglaetteter Wortstamm. */
        private val CATEGORIES: Map<String, Dislike> = mapOf(
            Dislike.PILZE to "Pilze, Pilz, Schwammerl, Schwämme",
            Dislike.FISCH to "Fisch, Fische",
            Dislike.MEERESFRUECHTE to "Meeresfrüchte, Schalentiere, Krustentiere",
            Dislike.POULET to "Geflügel, Poulet, Huhn, Hähnchen, Hühnchen, Hendl",
            Dislike.RIND to "Rind, Rindfleisch, Kalb, Kalbfleisch",
            Dislike.SCHWEIN to "Schwein, Schweinefleisch, Schweinsfleisch, Wurst, Würste",
            Dislike.LAMM to "Lamm, Lammfleisch",
            Dislike.INNEREIEN to "Innereien",
            Dislike.ZWIEBELN to "Zwiebel, Zwiebeln",
            Dislike.KNOBLAUCH to "Knoblauch, Knobli",
            Dislike.KORIANDER to "Koriander",
            Dislike.OLIVEN to "Olive, Oliven",
            Dislike.AUBERGINE to "Aubergine, Auberginen, Melanzane",
            Dislike.KOHL to "Kohl, Kabis",
            Dislike.SCHARF to "Scharf, Scharfes",
            Dislike.TOFU to "Tofu, Fleischersatz",
        ).flatMap { (dislike, names) ->
            names.split(',').map { stemOf(fold(IngredientNameNormalizer.normalize(it))) to dislike }
        }.toMap()
    }
}
