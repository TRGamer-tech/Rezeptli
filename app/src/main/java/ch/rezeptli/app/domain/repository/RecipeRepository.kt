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
     * landet. Findet auch Rezepte, die nur fuer den Plan gespeichert sind.
     */
    suspend fun findIdBySourceUrl(url: String): Long?

    /** Die Adressen aller Rezepte der Sammlung - der Wischstapel zeigt sie nicht noch einmal. */
    suspend fun savedSourceUrls(): Set<String>

    /** Nimmt ein gespeichertes Rezept in die Sammlung auf oder heraus. */
    suspend fun setInCollection(id: Long, inCollection: Boolean)

    suspend fun deleteRecipe(id: Long)

    /** Entfernt Rezepte, die nur fuer den Plan geholt wurden und dort nicht mehr stehen. */
    suspend fun deleteUnusedPlanRecipes()

    /** Haelt fest, wann ein Rezept zuletzt gekocht wurde - `null` nimmt den Vermerk zurueck. */
    suspend fun markCooked(id: Long, cookedAt: Long?)
}
