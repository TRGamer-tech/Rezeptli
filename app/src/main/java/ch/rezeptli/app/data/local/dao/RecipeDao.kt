package ch.rezeptli.app.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ch.rezeptli.app.data.local.entity.IngredientEntity
import ch.rezeptli.app.data.local.entity.RecipeEntity
import ch.rezeptli.app.data.local.entity.RecipeStepEntity
import ch.rezeptli.app.data.local.entity.RecipeSummaryProjection
import ch.rezeptli.app.data.local.entity.RecipeTagEntity
import ch.rezeptli.app.data.local.entity.RecipeWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RecipeDao {
    /**
     * Seitenweise Liste fuer die Rezeptuebersicht. Die Suche beruecksichtigt Titel,
     * Zubereitung und Zutatennamen; Tags werden mit UND-Semantik gefiltert.
     *
     * Gezeigt wird nur die Sammlung - Rezepte, die bloss fuer den Wochenplan gespeichert
     * sind, gehoeren nicht dazu. Mit [cooked] laesst sich auf schon Gekochtes (`true`) oder
     * noch nie Gekochtes (`false`) einschraenken.
     */
    @Query(
        """
        SELECT
            r.id AS id,
            r.title AS title,
            r.photoUri AS photoUri,
            r.prepTimeMinutes AS prepTimeMinutes,
            r.lastCookedAt AS lastCookedAt,
            (SELECT group_concat(rt.tag, '|') FROM recipe_tags rt WHERE rt.recipeId = r.id) AS tags
        FROM recipes r
        WHERE
            r.inCollection = 1
            AND (:query = ''
            OR r.title LIKE '%' || :query || '%'
            OR r.instructions LIKE '%' || :query || '%'
            OR EXISTS (SELECT 1 FROM ingredients i WHERE i.recipeId = r.id AND i.name LIKE '%' || :query || '%'))
            AND (:maxPrepTimeMinutes IS NULL
            OR (r.prepTimeMinutes IS NOT NULL AND r.prepTimeMinutes <= :maxPrepTimeMinutes))
            AND (:tagCount = 0
            OR (SELECT COUNT(DISTINCT t.tag) FROM recipe_tags t
            WHERE t.recipeId = r.id AND t.tag IN (:tags)) = :tagCount)
            AND (:cooked IS NULL
            OR (:cooked = 1 AND r.lastCookedAt IS NOT NULL)
            OR (:cooked = 0 AND r.lastCookedAt IS NULL))
        ORDER BY r.title COLLATE NOCASE ASC
        """,
    )
    abstract fun pagedSummaries(
        query: String,
        tags: List<String>,
        tagCount: Int,
        maxPrepTimeMinutes: Int?,
        cooked: Boolean?,
    ): PagingSource<Int, RecipeSummaryProjection>

    /**
     * Die IDs aller Rezepte, die zum Filter passen - in zufaelliger Reihenfolge, damit
     * jede Swipe-Session anders aussieht. Es werden bewusst nur IDs geladen; die Karten
     * holt der Swipe-Screen erst, wenn er sie braucht.
     */
    @Query(
        """
        SELECT r.id
        FROM recipes r
        WHERE
            r.inCollection = 1
            AND (:query = ''
            OR r.title LIKE '%' || :query || '%'
            OR r.instructions LIKE '%' || :query || '%'
            OR EXISTS (SELECT 1 FROM ingredients i WHERE i.recipeId = r.id AND i.name LIKE '%' || :query || '%'))
            AND (:maxPrepTimeMinutes IS NULL
            OR (r.prepTimeMinutes IS NOT NULL AND r.prepTimeMinutes <= :maxPrepTimeMinutes))
            AND (:tagCount = 0
            OR (SELECT COUNT(DISTINCT t.tag) FROM recipe_tags t
            WHERE t.recipeId = r.id AND t.tag IN (:tags)) = :tagCount)
            AND (:cooked IS NULL
            OR (:cooked = 1 AND r.lastCookedAt IS NOT NULL)
            OR (:cooked = 0 AND r.lastCookedAt IS NULL))
        ORDER BY RANDOM()
        """,
    )
    abstract suspend fun filteredRecipeIds(
        query: String,
        tags: List<String>,
        tagCount: Int,
        maxPrepTimeMinutes: Int?,
        cooked: Boolean?,
    ): List<Long>

    @Query(
        """
        SELECT
            r.id AS id,
            r.title AS title,
            r.photoUri AS photoUri,
            r.prepTimeMinutes AS prepTimeMinutes,
            r.lastCookedAt AS lastCookedAt,
            (SELECT group_concat(rt.tag, '|') FROM recipe_tags rt WHERE rt.recipeId = r.id) AS tags
        FROM recipes r
        WHERE r.id IN (:ids)
        """,
    )
    abstract suspend fun summariesByIds(ids: List<Long>): List<RecipeSummaryProjection>

    @Query("SELECT COUNT(*) FROM recipes WHERE inCollection = 1")
    abstract fun observeRecipeCount(): Flow<Int>

    @Query(
        """
        SELECT DISTINCT t.tag FROM recipe_tags t
        JOIN recipes r ON r.id = t.recipeId
        WHERE r.inCollection = 1
        ORDER BY t.tag COLLATE NOCASE ASC
        """,
    )
    abstract fun observeAllTags(): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    abstract fun observeRecipeWithDetails(id: Long): Flow<RecipeWithDetails?>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    abstract suspend fun recipeWithDetails(id: Long): RecipeWithDetails?

    @Insert
    abstract suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Update
    abstract suspend fun updateRecipe(recipe: RecipeEntity)

    /** Das Rezept zu einer Adresse - bevorzugt eines aus der Sammlung, sonst eines aus dem Plan. */
    @Query("SELECT id FROM recipes WHERE sourceUrl = :url ORDER BY inCollection DESC, id ASC LIMIT 1")
    abstract suspend fun idBySourceUrl(url: String): Long?

    @Query("SELECT sourceUrl FROM recipes WHERE inCollection = 1 AND sourceUrl IS NOT NULL")
    abstract suspend fun savedSourceUrls(): List<String>

    @Query("UPDATE recipes SET inCollection = :inCollection, updatedAt = :now WHERE id = :id")
    abstract suspend fun setInCollection(id: Long, inCollection: Boolean, now: Long)

    @Query("DELETE FROM recipes WHERE id = :id")
    abstract suspend fun deleteRecipe(id: Long)

    /**
     * Raeumt Rezepte weg, die nur fuer den Plan gespeichert wurden und dort nicht mehr
     * stehen. Was zur Sammlung gehoert, bleibt unberuehrt.
     */
    @Query("DELETE FROM recipes WHERE inCollection = 0 AND id NOT IN (SELECT recipeId FROM plan_entries)")
    abstract suspend fun deleteUnusedPlanRecipes()

    @Query("UPDATE recipes SET lastCookedAt = :cookedAt WHERE id = :id")
    abstract suspend fun updateLastCookedAt(id: Long, cookedAt: Long?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertIngredients(ingredients: List<IngredientEntity>)

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    abstract suspend fun deleteIngredientsOf(recipeId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTags(tags: List<RecipeTagEntity>)

    @Query("DELETE FROM recipe_tags WHERE recipeId = :recipeId")
    abstract suspend fun deleteTagsOf(recipeId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSteps(steps: List<RecipeStepEntity>)

    @Query("DELETE FROM recipe_steps WHERE recipeId = :recipeId")
    abstract suspend fun deleteStepsOf(recipeId: Long)

    /**
     * Legt ein Rezept an oder aktualisiert es samt Zutaten, Tags und Schritten - in
     * einer Transaktion, damit nie ein halb gespeicherter Zustand entstehen kann.
     */
    @Transaction
    open suspend fun upsertRecipe(
        recipe: RecipeEntity,
        ingredients: List<IngredientEntity>,
        tags: List<String>,
        steps: List<RecipeStepEntity> = emptyList(),
    ): Long {
        val recipeId = if (recipe.id == 0L) {
            insertRecipe(recipe)
        } else {
            updateRecipe(recipe)
            recipe.id
        }

        deleteIngredientsOf(recipeId)
        deleteTagsOf(recipeId)
        deleteStepsOf(recipeId)

        if (ingredients.isNotEmpty()) {
            insertIngredients(
                ingredients.mapIndexed { index, ingredient ->
                    ingredient.copy(id = 0L, recipeId = recipeId, position = index)
                },
            )
        }
        if (tags.isNotEmpty()) {
            insertTags(tags.map { RecipeTagEntity(recipeId = recipeId, tag = it) })
        }
        if (steps.isNotEmpty()) {
            insertSteps(
                steps.mapIndexed { index, step ->
                    step.copy(id = 0L, recipeId = recipeId, position = index)
                },
            )
        }

        return recipeId
    }
}
