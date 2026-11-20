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
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.Weekday

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
    val sourceUrl: String? = null,
    val sourceName: String? = null,
    val servings: Int? = null,
    /** Kalorien (kcal) einer Portion, wie die Quelle sie nennt - `null`, wenn sie nichts dazu sagt. */
    val kcalPerServing: Int? = null,
    /** Eiweiss (g) einer Portion laut Quelle. */
    val proteinPerServing: Double? = null,
    /**
     * Gehoert das Rezept zur Sammlung? Ein Rezept, das nur fuer den Wochenplan geholt
     * wurde, steht hier mit 0 - gespeichert, aber nicht in "Meine Rezepte".
     */
    @ColumnInfo(defaultValue = "1")
    val inCollection: Boolean = true,
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

/**
 * Ein Arbeitsschritt eines Rezepts.
 *
 * Die Schritte liegen strukturiert vor, statt sie beim Anzeigen aus dem Fliesstext zu
 * raten: Bei importierten Rezepten liefert die Quelle sie bereits gegliedert, und diese
 * Gliederung soll nicht verloren gehen. Fuer selbst erfasste Rezepte schlaegt der
 * InstructionSplitter eine Aufteilung vor, die sich vor dem Speichern korrigieren laesst.
 */
@Entity(
    tableName = "recipe_steps",
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
data class RecipeStepEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val recipeId: Long,
    val position: Int,
    val text: String,
    val timerMinutes: Int?,
)

/** Rezept mit allen abhaengigen Datensaetzen - fuer Detail- und Bearbeitungsansicht. */
data class RecipeWithDetails(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<IngredientEntity>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val tags: List<RecipeTagEntity>,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val steps: List<RecipeStepEntity>,
)

/** Woran ein Rezept wiedererkannt wird - ohne es ganz zu laden. */
data class RecipeIdentity(
    val id: Long,
    val title: String,
    val sourceUrl: String?,
    val inCollection: Boolean,
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
    val packText: String? = null,
    val price: Double? = null,
    @ColumnInfo(defaultValue = "0")
    val fromPlan: Boolean = false,
)

/**
 * Eine Mahlzeit im Wochenplan.
 *
 * Pro Tag und Tageszeit genau eine: Mittag- und Abendessen. Das Rezept ist ein
 * gespeichertes Rezept, das nicht zur Sammlung gehoeren muss; der Plan verweist nur
 * darauf und merkt sich, fuer wie viele gekocht wird, was die Mahlzeit anteilig kostet
 * und ob sie schon gekocht ist.
 */
@Entity(
    tableName = "plan_entries",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId"), Index(value = ["day", "slot"], unique = true)],
)
data class PlanEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val day: Weekday,
    val recipeId: Long,
    val servings: Int,
    val cost: Double,
    val label: String,
    /** Plaene von vor der Einfuehrung des Mittagessens waren Abendessen. */
    @ColumnInfo(defaultValue = "ABEND")
    val slot: MealSlot = MealSlot.ABEND,
    val cookedAt: Long? = null,
)

/** Ein Planeintrag zusammen mit dem, was die Karte zeigt. */
data class PlanEntryRow(
    val day: Weekday,
    val slot: MealSlot,
    val recipeId: Long,
    val servings: Int,
    val cost: Double,
    val label: String,
    val cookedAt: Long?,
    val title: String,
    val photoUri: String?,
    val prepTimeMinutes: Int?,
)
