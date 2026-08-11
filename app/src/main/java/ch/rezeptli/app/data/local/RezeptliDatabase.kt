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
import ch.rezeptli.app.data.local.entity.RecipeStepEntity
import ch.rezeptli.app.data.local.entity.RecipeTagEntity
import ch.rezeptli.app.data.local.entity.ShoppingItemEntity
import ch.rezeptli.app.data.local.entity.SwipeResultEntity
import ch.rezeptli.app.data.local.entity.SwipeSessionEntity
import ch.rezeptli.app.domain.steps.InstructionSplitter

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
        RecipeStepEntity::class,
    ],
    version = 4,
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

        /**
         * Version 3 haelt fest, woher ein importiertes Rezept stammt. Bestehende
         * Rezepte bekommen keine Quelle - sie sind selbst geschrieben.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recipes` ADD COLUMN `sourceUrl` TEXT")
                db.execSQL("ALTER TABLE `recipes` ADD COLUMN `sourceName` TEXT")
            }
        }

        /**
         * Version 4 speichert die Zubereitung zusaetzlich in einzelnen Schritten.
         *
         * Die Spalte `instructions` bleibt unveraendert bestehen: Sie ist weiterhin das,
         * was beim Bearbeiten im Textfeld steht. Die Schritte sind die gegliederte
         * Fassung davon - fuer den Kochmodus, der immer nur einen Schritt zeigt.
         *
         * Bestehende Rezepte werden hier einmalig aufgeteilt. Das geschieht mit
         * demselben InstructionSplitter, den auch die Bearbeitung verwendet, damit ein
         * altes und ein neues Rezept hinterher gleich aussehen.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recipe_steps` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `recipeId` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `timerMinutes` INTEGER,
                        FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recipe_steps_recipeId` " +
                        "ON `recipe_steps` (`recipeId`)",
                )

                val splitter = InstructionSplitter()
                val cursor = db.query("SELECT `id`, `instructions` FROM `recipes`")
                cursor.use {
                    while (it.moveToNext()) {
                        val recipeId = it.getLong(0)
                        val instructions = if (it.isNull(1)) "" else it.getString(1)

                        splitter.split(instructions).forEach { step ->
                            db.execSQL(
                                "INSERT INTO `recipe_steps` " +
                                    "(`recipeId`, `position`, `text`, `timerMinutes`) " +
                                    "VALUES (?, ?, ?, ?)",
                                arrayOf(recipeId, step.position, step.text, step.timerMinutes),
                            )
                        }
                    }
                }
            }
        }

        /** Alle Migrationen in aufsteigender Reihenfolge. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    }
}
