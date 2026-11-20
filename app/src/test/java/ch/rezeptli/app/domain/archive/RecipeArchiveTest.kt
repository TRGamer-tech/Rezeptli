package ch.rezeptli.app.domain.archive

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.steps.RecipeStep
import ch.rezeptli.app.fake.FakePhotoFiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Die Exportdatei: Was hinausgeht, muss genau so wieder hereinkommen - und was von
 * irgendwoher kommt, darf nichts anrichten.
 */
class RecipeArchiveTest {
    private val jpeg =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(200) { it.toByte() }
    private val png =
        byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) + ByteArray(64)

    private val hackbraten = Recipe(
        id = 4L,
        title = "Omas Hackbraten",
        instructions = "Mischen und formen.\n\n60 Minuten backen.",
        steps = listOf(
            RecipeStep(id = 9L, recipeId = 4L, position = 0, text = "Mischen und formen."),
            RecipeStep(id = 10L, recipeId = 4L, position = 1, text = "60 Minuten backen.", timerMinutes = 60),
        ),
        ingredients = listOf(
            Ingredient(id = 1L, recipeId = 4L, name = "Hackfleisch", amount = 800.0, unit = IngredientUnit.GRAMM),
            Ingredient(
                id = 2L,
                recipeId = 4L,
                name = "Zwiebeln, fein gehackt",
                amount = 1.5,
                note = "\"die grossen\"",
                canonicalName = "Zwiebel",
                position = 1,
            ),
            Ingredient(id = 3L, recipeId = 4L, name = "Salz", position = 2),
        ),
        tags = listOf("Familie", "Ofen"),
        prepTimeMinutes = 90,
        servings = 6,
        photoUri = "file:///fotos/hackbraten.jpg",
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_500_000L,
        lastCookedAt = 1_700_000_900_000L,
    )

    private val roesti = Recipe(
        id = 5L,
        title = "Rösti",
        instructions = "Raffeln und braten.",
        ingredients = listOf(Ingredient(name = "Kartoffeln", amount = 1.0, unit = IngredientUnit.KILOGRAMM)),
        photoUri = "https://beispiel.ch/roesti.jpg",
        sourceUrl = "https://beispiel.ch/roesti",
        sourceName = "Beispiel",
    )

    private val ohneFoto = Recipe(id = 6L, title = "Butterbrot", instructions = "Streichen.")

    private val telefon = FakePhotoFiles(mapOf("file:///fotos/hackbraten.jpg" to jpeg))

    private fun exportiert(vararg rezepte: Recipe, fotos: PhotoFiles = telefon): ByteArray {
        val out = ByteArrayOutputStream()
        RecipeArchiveWriter().write(out, rezepte.toList(), exportedAt = 1_800_000_000_000L, photos = fotos)
        return out.toByteArray()
    }

    private fun gelesen(datei: ByteArray, ziel: FakePhotoFiles = FakePhotoFiles()): List<Recipe> =
        RecipeArchiveReader().read(ByteArrayInputStream(datei), ziel)

    private fun zip(vararg eintraege: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            eintraege.forEach { (name, inhalt) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(inhalt)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun text(vararg rezepte: Recipe): ByteArray =
        RecipeArchive.encode(rezepte.map { ArchivedRecipe(it, null) }, exportedAt = 1L).toByteArray()

    private fun fehler(datei: ByteArray, ziel: FakePhotoFiles = FakePhotoFiles()): ArchiveError =
        assertThrows(ArchiveException::class.java) { gelesen(datei, ziel) }.error

    /** Was ein Rezept ausmacht - ohne das, was nur auf einem Telefon etwas bedeutet. */
    private fun Recipe.kern() = copy(
        id = 0L,
        photoUri = null,
        updatedAt = 0L,
        steps = steps.map { it.copy(id = 0L, recipeId = 0L) },
        ingredients = ingredients.map { it.copy(id = 0L, recipeId = 0L) },
    )

    @Test
    fun `was exportiert wird, kommt beim Einlesen genau so wieder heraus`() {
        val ziel = FakePhotoFiles()

        val zurueck = gelesen(exportiert(hackbraten, roesti, ohneFoto), ziel)

        assertEquals(listOf(hackbraten.kern(), roesti.kern(), ohneFoto.kern()), zurueck.map { it.kern() })
        // Nummern aus der alten Datenbank bedeuten auf dem neuen Telefon nichts.
        assertTrue(zurueck.all { it.id == 0L })
    }

    @Test
    fun `eigene Fotos reisen mit, Bilder aus dem Netz bleiben eine Adresse`() {
        val ziel = FakePhotoFiles()

        val (braten, kartoffeln, brot) = gelesen(exportiert(hackbraten, roesti, ohneFoto), ziel)

        // Das eigene Foto liegt jetzt auf dem neuen Telefon - unter neuem Namen, mit demselben Inhalt.
        assertTrue(braten.photoUri!!.startsWith("file:///fotos/neu-"))
        assertTrue(ziel.files.getValue(braten.photoUri!!).contentEquals(jpeg))
        assertEquals("https://beispiel.ch/roesti.jpg", kartoffeln.photoUri)
        assertNull(brot.photoUri)
        assertEquals(1, ziel.files.size)
    }

    @Test
    fun `die Datei ist ein gewoehnliches ZIP mit lesbarem Text`() {
        val eintraege = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(exportiert(hackbraten, roesti))).use { zip ->
            while (true) {
                val eintrag = zip.nextEntry ?: break
                eintraege[eintrag.name] = zip.readBytes()
            }
        }

        assertEquals(setOf("fotos/1.jpg", "rezepte.json"), eintraege.keys)
        assertTrue(eintraege.getValue("fotos/1.jpg").contentEquals(jpeg))
        val json = eintraege.getValue("rezepte.json").toString(Charsets.UTF_8)
        assertTrue("\"format\": \"rezeptli-rezepte\"" in json, json)
        assertTrue("\"titel\": \"Omas Hackbraten\"" in json)
        assertTrue("\"foto\": \"fotos/1.jpg\"" in json)
        assertTrue("\"einheit\": \"GRAMM\"" in json)
        // Wo die Datei auf dem alten Telefon lag, geht niemanden etwas an.
        assertFalse("hackbraten.jpg" in json)
    }

    @Test
    fun `fehlt ein Foto auf dem Telefon, geht das Rezept ohne Foto mit`() {
        val zurueck = gelesen(exportiert(hackbraten, fotos = FakePhotoFiles()))

        assertEquals("Omas Hackbraten", zurueck.single().title)
        assertNull(zurueck.single().photoUri)
    }

    @Test
    fun `auch die ausgepackte Textdatei laesst sich einlesen`() {
        val zurueck = gelesen(text(hackbraten, roesti))

        assertEquals(listOf("Omas Hackbraten", "Rösti"), zurueck.map { it.title })
        assertEquals(hackbraten.kern(), zurueck.first().kern())
    }

    @Test
    fun `irgendeine Datei ist keine Exportdatei`() {
        assertEquals(ArchiveError.NOT_AN_ARCHIVE, fehler("Liebe Oma, hier das Rezept".toByteArray()))
        assertEquals(ArchiveError.NOT_AN_ARCHIVE, fehler(ByteArray(0)))
        assertEquals(
            ArchiveError.NOT_AN_ARCHIVE,
            fehler("""{"format":"etwas-anderes","version":1,"rezepte":[]}""".toByteArray()),
        )
        assertEquals(ArchiveError.NOT_AN_ARCHIVE, fehler("""["rezeptli-rezepte"]""".toByteArray()))
        // Ein ZIP ohne die Rezepte darin - etwa ein Fotoalbum.
        assertEquals(ArchiveError.NOT_AN_ARCHIVE, fehler(zip("ferien/strand.jpg" to jpeg)))
    }

    @Test
    fun `eine Datei aus einer neueren App wird nicht halb verstanden, sondern abgelehnt`() {
        val neuer = """{"format":"rezeptli-rezepte","version":2,"rezepte":[{"titel":"Rösti"}]}"""

        assertEquals(ArchiveError.NEWER_VERSION, fehler(neuer.toByteArray()))
    }

    @Test
    fun `eine abgeschnittene Datei wird als beschaedigt erkannt und hinterlaesst keine Fotos`() {
        val ganz = exportiert(hackbraten, roesti)
        val ziel = FakePhotoFiles()

        val grund = fehler(ganz.copyOf(ganz.size / 2), ziel)

        assertTrue(grund == ArchiveError.UNREADABLE || grund == ArchiveError.NOT_AN_ARCHIVE, grund.name)
        assertTrue(ziel.files.isEmpty())
    }

    @Test
    fun `Namen aus dem Archiv werden nie zu Pfaden auf dem Telefon`() {
        val json = """{"format":"rezeptli-rezepte","version":1,"rezepte":[
            {"titel":"Falle","foto":"fotos/../../databases/rezeptli.db"},
            {"titel":"Auch Falle","foto":"/etc/hosts"},
            {"titel":"Harmlos","foto":"fotos/a.jpg"}]}"""
        val ziel = FakePhotoFiles()

        val zurueck = gelesen(
            zip(
                "fotos/../../databases/rezeptli.db" to jpeg,
                "fotos/a.jpg" to jpeg,
                "rezepte.json" to json.toByteArray(),
            ),
            ziel,
        )

        // Jedes Foto bekommt einen Namen, den die App selbst vergibt - der aus dem Archiv spielt keine Rolle.
        assertEquals(3, zurueck.size)
        assertTrue(ziel.files.keys.all { it.startsWith("file:///fotos/neu-") })
        assertNull(zurueck.first { it.title == "Auch Falle" }.photoUri)
        assertTrue(zurueck.mapNotNull { it.photoUri }.all { it in ziel.files })
    }

    @Test
    fun `als Foto zaehlt nur, was ein Bild ist`() {
        val json = """{"format":"rezeptli-rezepte","version":1,"rezepte":[
            {"titel":"Mit Programm","foto":"fotos/1.jpg"},{"titel":"Mit Bild","foto":"fotos/2.jpg"}]}"""
        val ziel = FakePhotoFiles()

        val zurueck = gelesen(
            zip(
                "fotos/1.jpg" to "#!/bin/sh\nrm -rf /".toByteArray(),
                "fotos/2.jpg" to png,
                "rezepte.json" to json.toByteArray(),
            ),
            ziel,
        )

        assertNull(zurueck.first { it.title == "Mit Programm" }.photoUri)
        assertTrue(ziel.files.getValue(zurueck.first { it.title == "Mit Bild" }.photoUri!!).contentEquals(png))
        assertEquals(1, ziel.files.size)
    }

    @Test
    fun `Fotos, auf die kein Rezept zeigt, bleiben nicht auf dem Telefon liegen`() {
        val json = """{"format":"rezeptli-rezepte","version":1,"rezepte":[{"titel":"Rösti","foto":"fotos/1.jpg"}]}"""
        val ziel = FakePhotoFiles()

        gelesen(zip("fotos/1.jpg" to jpeg, "fotos/verwaist.jpg" to png, "rezepte.json" to json.toByteArray()), ziel)

        assertEquals(1, ziel.files.size)
        assertTrue(
            ziel.files.values
                .single()
                .contentEquals(jpeg),
        )
    }

    @Test
    fun `ein uebergrosses Foto bricht das Einlesen ab, statt den Speicher zu fuellen`() {
        val json = """{"format":"rezeptli-rezepte","version":1,"rezepte":[{"titel":"Rösti","foto":"fotos/1.jpg"}]}"""
        // Fast nur Nullen: gepackt winzig, ausgepackt groesser als jedes Foto.
        val riesig = jpeg + ByteArray(RecipeArchiveReader.MAX_PHOTO_BYTES.toInt())
        val ziel = FakePhotoFiles()

        val grund =
            fehler(zip("fotos/0.jpg" to jpeg, "fotos/1.jpg" to riesig, "rezepte.json" to json.toByteArray()), ziel)

        assertEquals(ArchiveError.TOO_LARGE, grund)
        assertTrue(ziel.files.isEmpty())
    }

    @Test
    fun `fremde Dateien im Archiv werden uebergangen`() {
        val zurueck = gelesen(
            zip(
                "LIESMICH.txt" to "Hallo".toByteArray(),
                "rezepte.json" to text(roesti),
                "__MACOSX/._rezepte.json" to ByteArray(10),
            ),
        )

        assertEquals(listOf("Rösti"), zurueck.map { it.title })
    }

    @Test
    fun `unvollstaendige und unsinnige Angaben werden zurechtgestutzt statt uebernommen`() {
        val json = """{"format":"rezeptli-rezepte","version":1,"rezepte":[
            {"zubereitung":"Ohne Titel"},
            {"titel":"   "},
            "kein Rezept",
            {"titel":"Seltsam","zeitMinuten":-5,"portionen":9999,"quelleUrl":"javascript:alert(1)",
             "foto":"file:///data/data/geheim","erstellt":-1,"zuletztGekocht":"gestern",
             "schritte":[{"text":"Rühren","timerMinuten":100000},{"timerMinuten":5},"nichts"],
             "zutaten":[{"name":"Mehl","menge":-3,"einheit":"FASS"},{"menge":2},{"name":"Zucker","menge":"viel"}],
             "tags":["a","a","",7]}
        ]}"""

        val seltsam = gelesen(json.toByteArray()).single()

        assertEquals("Seltsam", seltsam.title)
        assertNull(seltsam.prepTimeMinutes)
        assertNull(seltsam.servings)
        assertNull(seltsam.sourceUrl)
        assertNull(seltsam.photoUri)
        assertEquals(0L, seltsam.createdAt)
        assertNull(seltsam.lastCookedAt)
        assertEquals(listOf(RecipeStep(position = 0, text = "Rühren")), seltsam.steps)
        // Der Fliesstext fehlte - er wird aus den Schritten gebaut.
        assertEquals("Rühren", seltsam.instructions)
        assertEquals(
            listOf(Ingredient(name = "Mehl", position = 0), Ingredient(name = "Zucker", position = 1)),
            seltsam.ingredients,
        )
        assertEquals(listOf("a", "7"), seltsam.tags)
    }

    @Test
    fun `Bilder werden an ihrem Anfang erkannt`() {
        fun bild(
            vararg bytes: Int,
        ) = RecipeArchiveReader.looksLikeImage(ByteArray(12) { bytes.getOrElse(it) { 0 }.toByte() })

        assertTrue(bild(0xFF, 0xD8, 0xFF))
        assertTrue(bild(0x89, 'P'.code, 'N'.code, 'G'.code))
        assertTrue(bild('G'.code, 'I'.code, 'F'.code, '8'.code))
        assertTrue(RecipeArchiveReader.looksLikeImage("RIFF....WEBP".toByteArray()))
        assertTrue(RecipeArchiveReader.looksLikeImage("\u0000\u0000\u0000\u0018ftypheic".toByteArray()))
        assertFalse(RecipeArchiveReader.looksLikeImage("PK\u0003\u0004........".toByteArray()))
        assertFalse(RecipeArchiveReader.looksLikeImage("<html><body>".toByteArray()))
        // Zu kurz, um irgendetwas zu sein.
        assertFalse(RecipeArchiveReader.looksLikeImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte()), length = 2))
    }
}
