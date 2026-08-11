package ch.rezeptli.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ch.rezeptli.app.data.local.RezeptliDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prueft, dass ein Update die Rezepte der Nutzerin nicht wegwirft.
 *
 * `runMigrationsAndValidate` vergleicht das Ergebnis der Migration mit dem von Room
 * erzeugten Schema - eine vergessene Spalte faellt hier auf und nicht erst auf dem
 * Geraet.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RezeptliDatabase::class.java,
    )

    @Test
    fun migriertVonDerErstenFassungBisHeuteUndBehaeltRezepte() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO recipes
                    (title, instructions, prepTimeMinutes, photoUri, createdAt, updatedAt, lastCookedAt)
                VALUES (
                    'Rösti',
                    '1. Kartoffeln raffeln.
2. 20 Minuten braten.',
                    30, NULL, 1, 1, NULL
                )
                """.trimIndent(),
            )
        }

        // Geprueft wird bis zur aktuellen Version: Room exportiert nur das Schema der
        // jeweils aktuellen Fassung, und ein Update springt ohnehin bis ganz nach vorne.
        val db = helper.runMigrationsAndValidate(TEST_DB, CURRENT_VERSION, true, *RezeptliDatabase.MIGRATIONS)

        db.query("SELECT title FROM recipes").use { cursor ->
            assertTrue("Das Rezept muss die Migration ueberleben", cursor.moveToFirst())
            assertEquals("Rösti", cursor.getString(0))
        }
        db.query("SELECT COUNT(*) FROM shopping_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Die Einkaufsliste startet leer", 0, cursor.getInt(0))
        }
        db.query("SELECT sourceUrl FROM recipes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue("Selbst erfasste Rezepte haben keine Quelle", cursor.isNull(0))
        }

        // Version 4 teilt bestehende Zubereitungen in Schritte auf. Ein Rezept, das
        // vor dem Update angelegt wurde, muss danach im Kochmodus funktionieren.
        db.query("SELECT text, timerMinutes FROM recipe_steps ORDER BY position").use { cursor ->
            assertTrue("Der alte Text muss in Schritte zerlegt sein", cursor.moveToFirst())
            assertEquals("Kartoffeln raffeln.", cursor.getString(0))
            assertTrue("Der erste Schritt hat keine Wartezeit", cursor.isNull(1))

            assertTrue(cursor.moveToNext())
            assertEquals("20 Minuten braten.", cursor.getString(0))
            assertEquals("Die Wartezeit wird als Timer erkannt", 20, cursor.getInt(1))

            assertEquals("Genau zwei Schritte", 2, cursor.count)
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
        const val CURRENT_VERSION = 4
    }
}
