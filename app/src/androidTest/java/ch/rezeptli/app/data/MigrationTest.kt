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

        // Version 5 bringt den Wochenplan: Alte Rezepte haben keine Portionszahl, der
        // Plan startet leer, und die Einkaufsliste kennt Packung, Preis und Planherkunft.
        db.query("SELECT servings FROM recipes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue("Alte Rezepte haben keine Portionszahl", cursor.isNull(0))
        }
        db.query("SELECT COUNT(*) FROM plan_entries").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Der Plan startet leer", 0, cursor.getInt(0))
        }
        db.execSQL(
            "INSERT INTO shopping_items (name, matchKey, unit, category, isChecked, isManual, addedAt) " +
                "VALUES ('Salz', 'salz', 'NONE', 'SONSTIGES', 0, 1, 1)",
        )
        db.query("SELECT fromPlan, packText, price FROM shopping_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Neue Posten gehoeren nicht zum Plan, solange nichts anderes gesagt ist", 0, cursor.getInt(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
        }

        // Version 6 trennt Sammlung und Plan: Was vor dem Update gespeichert war, bleibt in
        // "Meine Rezepte" - die App kann nicht wissen, was davon bewusst gespeichert wurde.
        db.query("SELECT inCollection FROM recipes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Bestehende Rezepte bleiben in der Sammlung", 1, cursor.getInt(0))
        }
    }

    @Test
    fun einBestehenderWochenplanWirdZuAbendessenUndBekommtPlatzFuersMittagessen() {
        helper.createDatabase(TEST_DB_PLAN, 5).use { db ->
            db.execSQL(
                "INSERT INTO recipes (id, title, instructions, createdAt, updatedAt) VALUES (1, 'Rösti', 'Braten.', 1, 1)",
            )
            db.execSQL(
                "INSERT INTO recipes (id, title, instructions, createdAt, updatedAt) VALUES (2, 'Risotto', 'Rühren.', 1, 1)",
            )
            db.execSQL(
                "INSERT INTO plan_entries (day, recipeId, servings, cost, label) VALUES ('MONTAG', 1, 2, 7.5, 'ALLTAG')",
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB_PLAN, CURRENT_VERSION, true, *RezeptliDatabase.MIGRATIONS)

        // Bisher gab es eine Mahlzeit pro Tag - das war das Abendessen, und gekocht ist sie noch nicht.
        db.query("SELECT day, slot, recipeId, cookedAt FROM plan_entries").use { cursor ->
            assertTrue("Der Plan muss die Migration ueberleben", cursor.moveToFirst())
            assertEquals("MONTAG", cursor.getString(0))
            assertEquals("ABEND", cursor.getString(1))
            assertEquals(1L, cursor.getLong(2))
            assertTrue(cursor.isNull(3))
            assertEquals(1, cursor.count)
        }

        // Am selben Tag hat jetzt auch ein Mittagessen Platz ...
        db.execSQL(
            "INSERT INTO plan_entries (day, slot, recipeId, servings, cost, label) " +
                "VALUES ('MONTAG', 'MITTAG', 2, 2, 6.0, 'ALLTAG')",
        )
        db.query("SELECT COUNT(*) FROM plan_entries WHERE day = 'MONTAG'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }

        // ... aber nicht zwei Abendessen.
        val doppelt = runCatching {
            db.execSQL(
                "INSERT INTO plan_entries (day, slot, recipeId, servings, cost, label) " +
                    "VALUES ('MONTAG', 'ABEND', 2, 2, 6.0, 'ALLTAG')",
            )
        }
        assertTrue("Ein Platz im Plan fasst genau eine Mahlzeit", doppelt.isFailure)
    }

    private companion object {
        const val TEST_DB = "migration-test"
        const val TEST_DB_PLAN = "migration-test-plan"
        const val CURRENT_VERSION = 6
    }
}
