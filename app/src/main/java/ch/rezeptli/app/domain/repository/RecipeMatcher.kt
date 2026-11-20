package ch.rezeptli.app.domain.repository

import ch.rezeptli.app.domain.deck.TitleKey
import ch.rezeptli.app.domain.deck.UrlKey

/** Ein gespeichertes Rezept, soweit es zum Wiedererkennen noetig ist. */
data class StoredRecipe(
    val id: Long,
    val title: String,
    val sourceUrl: String?,
    val inCollection: Boolean,
)

/**
 * Entscheidet, ob es ein Rezept schon gibt.
 *
 * An einer Stelle, weil die Frage an vielen gestellt wird - beim Speichern aus dem Web, beim
 * Uebernehmen gemeinsamer Treffer, beim Wochenplan, beim Einlesen einer Exportdatei - und
 * ueberall dieselbe Antwort braucht. Vorher verglich jede Stelle die Adresse Zeichen fuer
 * Zeichen. Zwei Telefone kennen dieselbe Seite aber oft leicht verschieden geschrieben
 * ("www.", "http", Schraegstrich am Ende, Parameter vom Teilen), und so landete dasselbe
 * Rezept beim Uebernehmen doppelt in der Sammlung.
 *
 * Mit Adresse zaehlt die Adresse ([UrlKey]). Ein selbst erfasstes Rezept hat keine; es wird
 * am Titel wiedererkannt ([TitleKey]) - und nur unter Rezepten, die ebenfalls keine Adresse
 * haben: "Rösti" von einer Rezeptseite und die eigene "Rösti" sind zwei Rezepte.
 */
object RecipeMatcher {
    fun byUrl(stored: List<StoredRecipe>, url: String): Long? {
        val key = UrlKey.of(url)
        if (key.isEmpty()) return null
        return stored.filter { it.sourceUrl != null && UrlKey.of(it.sourceUrl) == key }.preferred()
    }

    fun byTitle(stored: List<StoredRecipe>, title: String): Long? {
        val key = TitleKey.of(title)
        if (key.isEmpty()) return null
        return stored.filter { it.sourceUrl == null && TitleKey.of(it.title) == key }.preferred()
    }

    /**
     * Gibt es mehrere, zaehlt das aus der Sammlung vor einem, das nur fuer den Wochenplan
     * gespeichert ist - und unter gleichen das aelteste.
     */
    private fun List<StoredRecipe>.preferred(): Long? =
        sortedWith(compareByDescending<StoredRecipe> { it.inCollection }.thenBy { it.id }).firstOrNull()?.id
}
