package ch.rezeptli.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ch.rezeptli.app.data.local.dao.RecipeDao
import ch.rezeptli.app.data.local.dao.ShoppingListDao
import ch.rezeptli.app.data.local.dao.SwipeSessionDao
import ch.rezeptli.app.data.local.entity.IngredientEntity
import ch.rezeptli.app.data.local.entity.RecipeEntity
import ch.rezeptli.app.data.local.entity.RecipeTagEntity
import ch.rezeptli.app.data.local.entity.ShoppingItemEntity
import ch.rezeptli.app.data.local.entity.SwipeResultEntity
import ch.rezeptli.app.data.local.entity.SwipeSessionEntity

/**
 * Die lokale Datenbank der App. Rezeptli speichert alles auf dem Geraet - es gibt
 * keinen Server, keinen Account und keine Synchronisation.
 *
 * Migrationsstrategie: Schemas werden nach `app/schemas` exportiert und Aenderungen
 * ueber echte [Migration]en abgebildet. `fallbackToDestructiveMigration` wird bewusst
 * nicht verwendet, damit Nutzerdaten bei einem Update nie verloren gehen.
 */
@Database(
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        RecipeTagEntity::class,
        SwipeSessionEntity::class,
        SwipeResultEntity::class,
        ShoppingItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RezeptliDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao

    abstract fun swipeSessionDao(): SwipeSessionDao

    abstract fun shoppingListDao(): ShoppingListDao

    companion object {
        const val DATABASE_NAME = "rezeptli.db"

        /**
         * Version 2 ergaenzt die Einkaufsliste. Bestehende Rezepte bleiben unangetastet -
         * es kommt nur eine Tabelle dazu.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shopping_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `matchKey` TEXT NOT NULL,
                        `amount` REAL,
                        `unit` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `isChecked` INTEGER NOT NULL,
                        `isManual` INTEGER NOT NULL,
                        `sourceNote` TEXT,
                        `addedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_shopping_items_matchKey` " +
                        "ON `shopping_items` (`matchKey`)",
                )
            }
        }

        /** Alle Migrationen in aufsteigender Reihenfolge. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
