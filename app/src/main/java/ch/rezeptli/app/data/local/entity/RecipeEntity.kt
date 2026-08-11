package ch.rezeptli.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import ch.rezeptli.app.domain.model.IngredientCategory
import ch.rezeptli.app.domain.model.IngredientUnit

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val instructions: String,
    val prepTimeMinutes: Int?,
    val photoUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastCookedAt: Long?,
)

@Entity(
    tableName = "ingredients",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId")],
)
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val recipeId: Long,
    val name: String,
    val amount: Double?,
    val unit: IngredientUnit,
    val note: String?,
    val canonicalName: String?,
    val position: Int,
)

/**
 * Tags liegen bewusst normalisiert in einer eigenen Tabelle statt als Textspalte:
 * so laesst sich nach mehreren Tags gleichzeitig filtern und die Tag-Liste fuer die
 * Filter-Chips direkt aus der Datenbank lesen.
 */
@Entity(
    tableName = "recipe_tags",
    primaryKeys = ["recipeId", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tag")],
)
data class RecipeTagEntity(
    val recipeId: Long,
    val tag: String,
)

/** Rezept mit allen abhaengigen Datensaetzen - fuer Detail- und Bearbeitungsansicht. */
data class RecipeWithDetails(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<IngredientEntity>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val tags: List<RecipeTagEntity>,
)

/**
 * Schlanke Projektion fuer Listen: laedt bewusst weder Zubereitungstext noch Zutaten,
 * damit auch grosse Sammlungen seitenweise geladen werden koennen.
 */
data class RecipeSummaryProjection(
    val id: Long,
    val title: String,
    val photoUri: String?,
    val prepTimeMinutes: Int?,
    val lastCookedAt: Long?,
    @ColumnInfo(name = "tags")
    val tags: String?,
)

/**
 * Ein Posten der Einkaufsliste.
 *
 * Die Liste ist bewusst nicht an eine Swipe-Session gebunden: Man geht einmal
 * einkaufen, egal aus wie vielen Runden die Zutaten stammen.
 */
@Entity(
    tableName = "shopping_items",
    indices = [Index("matchKey")],
)
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val matchKey: String,
    val amount: Double?,
    val unit: IngredientUnit,
    val category: IngredientCategory,
    val isChecked: Boolean,
    val isManual: Boolean,
    val sourceNote: String?,
    val addedAt: Long,
)
