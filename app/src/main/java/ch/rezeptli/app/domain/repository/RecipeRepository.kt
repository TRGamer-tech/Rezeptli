package ch.rezeptli.app.domain.repository

import androidx.paging.PagingData
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.model.RecipeSummary
import kotlinx.coroutines.flow.Flow

/**
 * Zugriff auf die Rezeptsammlung.
 *
 * Die Schnittstelle gehoert bewusst zur Domain-Schicht; die Implementierung liegt in
 * der Data-Schicht. Fuer Listen wird [PagingData] verwendet, damit auch grosse
 * Sammlungen nur seitenweise geladen werden. `androidx.paging` ist dafuer die einzige
 * Ausnahme von der Regel "Domain ohne Framework-Abhaengigkeiten" - `paging-common`
 * enthaelt keinen Android-Code (siehe docs/adr/0002-architektur.md).
 */
interface RecipeRepository {
    fun pagedSummaries(filter: RecipeFilter): Flow<PagingData<RecipeSummary>>

    fun observeRecipe(id: Long): Flow<Recipe?>

    fun observeRecipeCount(): Flow<Int>

    fun observeAllTags(): Flow<List<String>>

    suspend fun getRecipe(id: Long): Recipe?

    /** Summaries zu [ids], in der Reihenfolge von [ids]. */
    suspend fun getSummaries(ids: List<Long>): List<RecipeSummary>

    /** IDs aller Rezepte, die zum Filter passen - in zufaelliger Reihenfolge. */
    suspend fun getFilteredRecipeIds(filter: RecipeFilter): List<Long>

    /** Legt ein Rezept an oder aktualisiert es und liefert dessen ID zurueck. */
    suspend fun saveRecipe(recipe: Recipe): Long

    /**
     * Die ID des Rezepts, das von dieser Adresse importiert wurde - damit es nicht doppelt
     * landet. Findet auch Rezepte, die nur fuer den Plan gespeichert sind; gibt es beides,
     * gewinnt das aus der Sammlung.
     *
     * Verglichen wird nicht Zeichen fuer Zeichen: Dieselbe Seite ist mit und ohne "www.",
     * mit "http" oder "https", mit oder ohne Schraegstrich am Ende dasselbe Rezept. Zwei
     * Telefone kennen dasselbe Rezept oft unter leicht verschiedenen Adressen - genau daran
     * entstanden beim Uebernehmen gemeinsamer Treffer Dubletten.
     */
    suspend fun findIdBySourceUrl(url: String): Long?

    /**
     * Die ID eines selbst erfassten Rezepts (ohne Adresse) mit diesem Titel - ohne Ruecksicht
     * auf Gross- und Kleinschreibung. Ein Rezept ohne Adresse laesst sich an nichts anderem
     * wiedererkennen.
     */
    suspend fun findHandwrittenIdByTitle(title: String): Long?

    /**
     * Das Rezept, als das eine Karte oder ein geholtes Rezept schon auf diesem Geraet liegt:
     * mit Adresse an der Adresse, ohne Adresse am Titel.
     */
    suspend fun findExisting(sourceUrl: String?, title: String): Long? =
        if (sourceUrl != null) findIdBySourceUrl(sourceUrl) else findHandwrittenIdByTitle(title)

    /** Die Adressen aller Rezepte der Sammlung - der Wischstapel zeigt sie nicht noch einmal. */
    suspend fun savedSourceUrls(): Set<String>

    /** Nimmt ein gespeichertes Rezept in die Sammlung auf oder heraus. */
    suspend fun setInCollection(id: Long, inCollection: Boolean)

    /** Haelt fest, fuer wie viele ein Rezept gedacht ist - wenn es das selbst nicht sagt. */
    suspend fun setServings(id: Long, servings: Int)

    suspend fun deleteRecipe(id: Long)

    /** Entfernt Rezepte, die nur fuer den Plan geholt wurden und dort nicht mehr stehen. */
    suspend fun deleteUnusedPlanRecipes()

    /** Haelt fest, wann ein Rezept zuletzt gekocht wurde - `null` nimmt den Vermerk zurueck. */
    suspend fun markCooked(id: Long, cookedAt: Long?)
}
