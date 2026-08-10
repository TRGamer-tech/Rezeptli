package ch.rezeptli.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import ch.rezeptli.app.data.local.dao.RecipeDao
import ch.rezeptli.app.data.local.dao.SwipeSessionDao
import ch.rezeptli.app.data.local.entity.IngredientEntity
import ch.rezeptli.app.data.local.entity.RecipeEntity
import ch.rezeptli.app.data.local.entity.RecipeTagEntity
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
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RezeptliDatabase : RoomDatabase() {

    abstract fun recipeDao(): RecipeDao

    abstract fun swipeSessionDao(): SwipeSessionDao

    companion object {
        const val DATABASE_NAME = "rezeptli.db"

        /**
         * Alle Migrationen in aufsteigender Reihenfolge. Version 1 ist die erste
         * veroeffentlichte Fassung, daher ist die Liste noch leer.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
