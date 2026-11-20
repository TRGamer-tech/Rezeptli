package ch.rezeptli.app.domain.deck

import java.net.URLDecoder

/**
 * Lesbare Titel fuer Karten, deren Titel nur aus der Adresse stammt.
 *
 * Die Verzeichnisse kennen keinen Rezepttitel, nur die Adresse; der Titel wird aus ihrem
 * letzten Abschnitt geraten. Bei manchen Quellen (Migusto) blieb davon nichts uebrig -
 * etwa wenn der letzte Abschnitt nur eine Nummer ist -, und dann stand die rohe Adresse
 * auf der Karte. Hier wird stattdessen ein frueherer Abschnitt genommen, und reicht auch
 * das nicht, ein neutraler Titel.
 */
object RecipeTitles {
    /** Laengere Titel sind fast immer zusammengeklebte Stichworte, kein Rezeptname. */
    const val MAX_WORDS = 8

    /** Ein Titel, der wie eine Adresse aussieht, wird aus ihr neu gebildet. */
    fun clean(title: String, url: String, fallback: String): String {
        val roh = title.trim()
        val gebildet = if (roh.isEmpty() || looksLikeUrl(roh)) fromUrl(url) else roh
        return gebildet?.let(::shorten) ?: fallback
    }

    /** Der Titel aus den Abschnitten der Adresse - oder `null`, wenn keiner taugt. */
    fun fromUrl(url: String): String? {
        val pfad = url
            .substringAfter("://", url)
            .substringAfter('/', "")
            .substringBefore('?')
            .substringBefore('#')
        return pfad
            .split('/')
            .reversed()
            .asSequence()
            .map(::decode)
            .map(::slugToWords)
            .firstOrNull { it.isNotBlank() }
    }

    private fun slugToWords(segment: String): String {
        val slug = segment
            .replace(PAGE_SUFFIX, "")
            .replace(TRAILING_ID, "")
            .replace(TRAILING_NOISE, "")
        val woerter = slug
            .split('-', '_', ' ', '+')
            .filter { it.isNotBlank() && !it.all { char -> char.isDigit() } }
        // Abschnitte wie "de" oder "rezepte" sind Rubriken, kein Rezeptname.
        if (woerter.size == 1 && woerter.single().lowercase() in RUBRIKEN) return ""
        return woerter.joinToString(" ") { wort -> wort.replaceFirstChar { it.uppercase() } }
    }

    private fun shorten(title: String): String {
        val woerter = title.split(Regex("\\s+"))
        return if (woerter.size <= MAX_WORDS) title else woerter.take(MAX_WORDS).joinToString(" ") + " …"
    }

    private fun looksLikeUrl(text: String): Boolean = text.contains("://") || text.startsWith("www.")

    private fun decode(segment: String): String = runCatching {
        URLDecoder.decode(
            segment,
            "UTF-8",
        )
    }.getOrDefault(segment)

    private val PAGE_SUFFIX = Regex("\\.(html?|aspx|php)$", RegexOption.IGNORE_CASE)
    private val TRAILING_ID = Regex("-\\d+$")
    private val TRAILING_NOISE = Regex("-(rezept|recipe|recette|ricetta|fid)$", RegexOption.IGNORE_CASE)
    private val RUBRIKEN = setOf(
        "de",
        "fr",
        "it",
        "en",
        "rezepte",
        "rezept",
        "recipes",
        "recipe",
        "recettes",
        "recette",
        "ricette",
        "ricetta",
        "kochen",
        "www",
    )
}
