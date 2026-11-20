package ch.rezeptli.app.domain.usecase

import ch.rezeptli.app.domain.archive.ArchiveError
import ch.rezeptli.app.domain.archive.RecipeArchiveReader
import ch.rezeptli.app.domain.archive.RecipeArchiveWriter
import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.IngredientUnit
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.fake.FakeDocuments
import ch.rezeptli.app.fake.FakePhotoFiles
import ch.rezeptli.app.fake.FakeRecipeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Die Sammlung hinaus in eine Datei und auf einem anderen Telefon wieder herein. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeArchiveUseCasesTest {
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(100)
    private val datei = "content://dokumente/rezeptli-rezepte.zip"

    private val hackbraten = Recipe(
        id = 1L,
        title = "Omas Hackbraten",
        instructions = "Im Ofen backen.",
        ingredients = listOf(
            Ingredient(name = "Hackfleisch", amount = 800.0, unit = IngredientUnit.GRAMM),
            Ingredient(name = "Zwiebeln", amount = 2.0),
        ),
        photoUri = "file:///fotos/hackbraten.jpg",
        lastCookedAt = 1_700_000_000_000L,
    )
    private val roesti = Recipe(
        id = 2L,
        title = "Rösti",
        instructions = "Raffeln und braten.",
        ingredients = listOf(Ingredient(name = "Kartoffeln", amount = 1.0, unit = IngredientUnit.KILOGRAMM)),
        sourceUrl = "https://beispiel.ch/roesti",
    )

    /** Nur fuer den Wochenplan geholt - gehoert nicht zur Sammlung. */
    private val nurImPlan = Recipe(
        id = 3L,
        title = "Gulasch",
        instructions = "Schmoren.",
        sourceUrl = "https://beispiel.ch/gulasch",
        inCollection = false,
    )

    /** Ein Telefon: seine Rezepte, seine Fotos und was im Dateidialog zu sehen ist. */
    private inner class Telefon(
        rezepte: List<Recipe> = emptyList(),
        fotos: Map<String, ByteArray> = emptyMap(),
        val dokumente: FakeDocuments = FakeDocuments(),
    ) {
        val sammlung = FakeRecipeRepository(rezepte)
        val fotos = FakePhotoFiles(fotos)
        val exportieren =
            ExportRecipesUseCase(sammlung, this.fotos, dokumente, RecipeArchiveWriter(), UnconfinedTestDispatcher())
        val importieren =
            ImportRecipesUseCase(sammlung, this.fotos, dokumente, RecipeArchiveReader(), UnconfinedTestDispatcher())

        suspend fun rezepte(): List<Recipe> =
            sammlung.getFilteredRecipeIds(RecipeFilter()).mapNotNull { sammlung.getRecipe(it) }.sortedBy { it.title }
    }

    private fun altesTelefon(dokumente: FakeDocuments = FakeDocuments()) = Telefon(
        rezepte = listOf(hackbraten, roesti, nurImPlan),
        fotos = mapOf("file:///fotos/hackbraten.jpg" to jpeg),
        dokumente = dokumente,
    )

    @Test
    fun `die Sammlung zieht mit einer Datei auf ein neues Telefon um`() = runTest {
        val alt = altesTelefon()
        assertEquals(ExportResult.Done(recipes = 2, photos = 1), alt.exportieren(datei))

        val neu = Telefon(dokumente = alt.dokumente)
        assertEquals(ImportResult.Done(imported = 2, skipped = 0), neu.importieren(datei))

        val (braten, kartoffeln) = neu.rezepte()
        assertEquals("Omas Hackbraten", braten.title)
        assertEquals(
            hackbraten.ingredients.map { it.name to it.amount },
            braten.ingredients.map { it.name to it.amount },
        )
        assertEquals(1_700_000_000_000L, braten.lastCookedAt)
        assertTrue(
            neu.fotos.files
                .getValue(braten.photoUri!!)
                .contentEquals(jpeg),
        )
        assertEquals("https://beispiel.ch/roesti", kartoffeln.sourceUrl)
        // Was nur im Wochenplan stand, ist kein Teil der Sammlung - und reist nicht mit.
        assertTrue(neu.rezepte().none { it.title == "Gulasch" })
    }

    @Test
    fun `dieselbe Datei zweimal einzulesen legt nichts doppelt an`() = runTest {
        val alt = altesTelefon()
        alt.exportieren(datei)
        val neu = Telefon(dokumente = alt.dokumente)
        neu.importieren(datei)

        val nochmal = neu.importieren(datei)

        assertEquals(ImportResult.Done(imported = 0, skipped = 2), nochmal)
        assertEquals(2, neu.rezepte().size)
        // Auch das Foto des uebergangenen Rezepts liegt nicht doppelt auf dem Telefon.
        assertEquals(1, neu.fotos.files.size)
    }

    @Test
    fun `auf dem Telefon, von dem die Datei stammt, gibt es alles schon`() = runTest {
        val alt = altesTelefon()
        alt.exportieren(datei)

        assertEquals(ImportResult.Done(imported = 0, skipped = 2), alt.importieren(datei))
        assertEquals(1, alt.fotos.files.size)
    }

    @Test
    fun `es kommt dazu, was fehlt - bestehende Rezepte bleiben, wie sie sind`() = runTest {
        val alt = altesTelefon()
        alt.exportieren(datei)
        // Auf dem zweiten Telefon gibt es die Rösti schon, inzwischen mit eigener Notiz.
        val meineRoesti = roesti.copy(id = 9L, instructions = "Raffeln und in viel Butter braten.")
        val neu = Telefon(rezepte = listOf(meineRoesti), dokumente = alt.dokumente)

        assertEquals(ImportResult.Done(imported = 1, skipped = 1), neu.importieren(datei))

        assertEquals(listOf("Omas Hackbraten", "Rösti"), neu.rezepte().map { it.title })
        assertEquals("Raffeln und in viel Butter braten.", neu.rezepte().last().instructions)
    }

    @Test
    fun `ein selbst erfasstes Rezept gilt nur mit denselben Zutaten als dasselbe`() = runTest {
        val alt = altesTelefon()
        alt.exportieren(datei)
        val andererBraten = hackbraten.copy(
            id = 9L,
            title = "omas hackbraten ",
            ingredients = listOf(Ingredient(name = "Linsen", amount = 300.0, unit = IngredientUnit.GRAMM)),
            photoUri = null,
        )
        val neu = Telefon(rezepte = listOf(andererBraten), dokumente = alt.dokumente)

        // Gleicher Titel, anderes Gericht: Beide bleiben.
        assertEquals(ImportResult.Done(imported = 2, skipped = 0), neu.importieren(datei))
        assertEquals(3, neu.rezepte().size)
    }

    @Test
    fun `stand ein Rezept bisher nur im Wochenplan, kommt es in die Sammlung statt doppelt zu entstehen`() = runTest {
        val alt = altesTelefon()
        alt.exportieren(datei)
        val neu = Telefon(rezepte = listOf(roesti.copy(id = 9L, inCollection = false)), dokumente = alt.dokumente)

        assertEquals(ImportResult.Done(imported = 2, skipped = 0), neu.importieren(datei))

        assertEquals(1, neu.rezepte().count { it.sourceUrl == roesti.sourceUrl })
        assertEquals(9L, neu.rezepte().first { it.sourceUrl == roesti.sourceUrl }.id)
    }

    @Test
    fun `ohne Rezepte entsteht keine leere Datei`() = runTest {
        val leer = Telefon(rezepte = listOf(nurImPlan))
        leer.dokumente.files[datei] = ByteArray(0)

        assertEquals(ExportResult.Nothing, leer.exportieren(datei))

        assertEquals(listOf(datei), leer.dokumente.discarded)
        assertNull(leer.dokumente.files[datei])
    }

    @Test
    fun `laesst sich die Datei nicht zu Ende schreiben, bleibt keine halbe liegen`() = runTest {
        val alt = altesTelefon()
        alt.dokumente.failAfterBytes = 50

        assertEquals(ExportResult.Failed, alt.exportieren(datei))

        assertEquals(listOf(datei), alt.dokumente.discarded)
        assertNull(alt.dokumente.files[datei])
    }

    @Test
    fun `laesst sich die Datei gar nicht oeffnen, wird das gemeldet`() = runTest {
        val alt = altesTelefon()
        alt.dokumente.locked += datei

        assertEquals(ExportResult.Failed, alt.exportieren(datei))
        assertEquals(ImportResult.Failed(ArchiveError.UNREADABLE), alt.importieren(datei))
        assertEquals(ImportResult.Failed(ArchiveError.UNREADABLE), alt.importieren("content://dokumente/gibt-es-nicht"))
    }

    @Test
    fun `eine fremde Datei aendert nichts an der Sammlung`() = runTest {
        val neu = Telefon(rezepte = listOf(roesti))
        neu.dokumente.files[datei] = "Das ist ein Brief, keine Rezeptsammlung.".toByteArray()

        assertEquals(ImportResult.Failed(ArchiveError.NOT_AN_ARCHIVE), neu.importieren(datei))

        assertEquals(listOf("Rösti"), neu.rezepte().map { it.title })
    }

    @Test
    fun `eine Datei ohne Rezepte wird als leer gemeldet`() = runTest {
        val neu = Telefon()
        neu.dokumente.files[datei] = """{"format":"rezeptli-rezepte","version":1,"rezepte":[]}""".toByteArray()

        assertEquals(ImportResult.Failed(ArchiveError.EMPTY), neu.importieren(datei))
    }

    @Test
    fun `dasselbe Rezept zweimal in einer Datei wird nur einmal angelegt`() = runTest {
        val neu = Telefon()
        neu.dokumente.files[datei] = """{"format":"rezeptli-rezepte","version":1,"rezepte":[
            {"titel":"Rösti","quelleUrl":"https://beispiel.ch/roesti"},
            {"titel":"Rösti nochmal","quelleUrl":"https://beispiel.ch/roesti"},
            {"titel":"Butterbrot","zutaten":[{"name":"Brot"},{"name":"Butter"}]},
            {"titel":"BUTTERBROT","zutaten":[{"name":"butter"},{"name":"brot"}]}
        ]}""".toByteArray()

        assertEquals(ImportResult.Done(imported = 2, skipped = 2), neu.importieren(datei))
        assertEquals(listOf("Butterbrot", "Rösti"), neu.rezepte().map { it.title })
    }
}
