package ch.rezeptli.app.domain.model

import java.net.URLEncoder

/**
 * Die Adresse, mit der Bring! ein Rezept importiert.
 *
 * Bring liest die Zutaten selbst von der Rezeptseite und rechnet sie auf die gewuenschte
 * Personenzahl um - die App reicht nur die Adresse und die beiden Portionszahlen weiter.
 * Dafuer braucht es weder Konto noch Passwort, und es geht immer nur ein Rezept auf einmal.
 */
object BringLink {
    private const val BASE = "https://api.getbring.com/rest/bringrecipes/deeplink"

    /** Wenn die Quelle keine Portionen nennt, rechnet Bring wie die meisten Rezepte mit vier. */
    private const val DEFAULT_SERVINGS = 4

    /** `null` fuer Rezepte ohne Quelladresse - von denen kann Bring nichts lesen. */
    fun forRecipe(sourceUrl: String?, servings: Int?, persons: Int): String? {
        val adresse = sourceUrl?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
        val basis = servings?.takeIf { it > 0 } ?: DEFAULT_SERVINGS
        return BASE +
            "?url=" + URLEncoder.encode(adresse, "UTF-8") +
            "&source=web" +
            "&baseQuantity=" + basis +
            "&requestedQuantity=" + persons.coerceAtLeast(1)
    }
}
