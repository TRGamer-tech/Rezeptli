package ch.rezeptli.app.domain.repository

import ch.rezeptli.app.domain.deck.DeckEntry
import ch.rezeptli.app.domain.model.WebRecipe
import ch.rezeptli.app.domain.model.WebSearchResult
import ch.rezeptli.app.domain.ranking.SourceOrigin
import kotlinx.coroutines.flow.Flow

/** Warum ein Import nicht geklappt hat - in Kategorien, die die UI erklaeren kann. */
enum class WebImportError {
    NO_CONNECTION,

    /** Die Seite weist automatisierte Zugriffe ab. */
    REJECTED,

    /** Die robots.txt der Seite untersagt genau diesen Abruf. */
    DISALLOWED,

    NOT_FOUND,

    /** Die Seite war erreichbar, enthaelt aber kein maschinenlesbares Rezept. */
    NO_RECIPE_FOUND,

    UNKNOWN,
}

sealed interface WebRecipeResult {
    data class Loaded(val recipe: WebRecipe) : WebRecipeResult

    data class Failed(val error: WebImportError) : WebRecipeResult
}

/**
 * Zwischenstand einer laufenden Suche.
 *
 * Die Quellen werden gleichzeitig befragt und melden sich unterschiedlich schnell.
 * Statt auf die langsamste zu warten, gibt die Suche nach jeder eingetroffenen Quelle
 * den bisherigen Stand heraus - die ersten Treffer stehen damit auf dem Bildschirm,
 * waehrend der Rest noch laedt.
 */
data class WebSearchUpdate(
    val results: List<WebSearchResult> = emptyList(),
    val finishedSources: Int = 0,
    val totalSources: Int = 0,
) {
    val isComplete: Boolean get() = totalSources > 0 && finishedSources >= totalSources

    val hasResults: Boolean get() = results.isNotEmpty()
}

/**
 * Der Vorrat, aus dem ein Wischstapel gezogen wird.
 *
 * Herkunft und Eintraege kommen zusammen heraus, weil der Stapel beides braucht: die
 * Eintraege zum Ziehen, die Herkunft zum Mischen nach Land und Sprachraum.
 */
data class DeckPool(
    val entriesBySource: Map<String, List<DeckEntry>> = emptyMap(),
    val origins: Map<String, SourceOrigin> = emptyMap(),
) {
    val isEmpty: Boolean get() = entriesBySource.values.all { it.isEmpty() }
}

/** Zugriff auf Rezepte im Web. */
interface WebRecipeRepository {
    /**
     * Durchsucht die Verzeichnisse der angegebenen Quellen.
     *
     * Liefert fortlaufend Zwischenstaende, bis alle Quellen geantwortet haben.
     */
    fun search(query: String, sourceIds: Set<String>): Flow<WebSearchUpdate>

    /** Laedt ein einzelnes Rezept von seiner Adresse. */
    suspend fun loadRecipe(url: String): WebRecipeResult

    /**
     * Laedt die Verzeichnisse im Hintergrund, damit die erste Suche nicht darauf wartet.
     *
     * Das war der Grund fuer die lange erste Suche: Das Verzeichnis einer Quelle wurde
     * beim ersten Treffer geholt - mitten in der Anfrage der Nutzerin.
     */
    suspend fun warmUp(sourceIds: Set<String>)

    /**
     * Die Verzeichnisse aller durchsuchbaren Quellen, als Vorrat fuer den Wischstapel.
     *
     * Kommt aus demselben taeglich gebauten Verzeichnis wie die Suche. Es steht damit
     * schon nach einem Abruf bereit - der Stapel wartet nicht auf die Rezeptseiten.
     */
    suspend fun deckPool(): DeckPool

    /**
     * Das Vorschaubild einer Rezeptseite, oder `null`.
     *
     * Nicht jede Quelle nennt in ihrem Verzeichnis ein Bild - Cookaround, Swissmilk und
     * ein paar andere tun es nicht. Fuer diese Karten wird das Bild nachgeholt, und
     * zwar nur fuer die naechsten paar: Der Stapel soll nicht auf Bilder warten, die
     * niemand zu Gesicht bekommt.
     */
    suspend fun cardImage(url: String): String?
}
