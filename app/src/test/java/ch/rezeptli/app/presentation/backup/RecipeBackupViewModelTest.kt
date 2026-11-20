package ch.rezeptli.app.presentation.backup

import ch.rezeptli.app.domain.archive.ArchiveError
import ch.rezeptli.app.domain.archive.RecipeArchiveReader
import ch.rezeptli.app.domain.archive.RecipeArchiveWriter
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.usecase.ExportRecipesUseCase
import ch.rezeptli.app.domain.usecase.ImportRecipesUseCase
import ch.rezeptli.app.domain.usecase.ObserveRecipeCountUseCase
import ch.rezeptli.app.fake.FakeDocuments
import ch.rezeptli.app.fake.FakePhotoFiles
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.util.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** Die Karte "Rezepte sichern und uebertragen" auf der Vorlieben-Seite. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeBackupViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val datei = "content://dokumente/rezepte.zip"
    private val dokumente = FakeDocuments()
    private val fotos = FakePhotoFiles()

    private fun createViewModel(vararg rezepte: Recipe): RecipeBackupViewModel {
        val sammlung = FakeRecipeRepository(rezepte.toList())
        return RecipeBackupViewModel(
            observeRecipeCount = ObserveRecipeCountUseCase(sammlung),
            exportRecipes = ExportRecipesUseCase(
                sammlung,
                fotos,
                dokumente,
                RecipeArchiveWriter(),
                UnconfinedTestDispatcher(),
            ),
            importRecipes = ImportRecipesUseCase(
                sammlung,
                fotos,
                dokumente,
                RecipeArchiveReader(),
                UnconfinedTestDispatcher(),
            ),
        )
    }

    private val roesti = Recipe(id = 1L, title = "Rösti", instructions = "Braten.")
    private val gulasch = Recipe(id = 2L, title = "Gulasch", instructions = "Schmoren.")

    @Test
    fun `ohne Rezepte gibt es nichts zu exportieren - importieren geht trotzdem`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.recipeCount)
        assertFalse(viewModel.uiState.value.canExport)
        assertTrue(viewModel.uiState.value.canImport)
    }

    @Test
    fun `exportieren schreibt die Datei und sagt, wie viele Rezepte darin sind`() = runTest {
        val viewModel = createViewModel(roesti, gulasch)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canExport)

        viewModel.onExportTo(datei)
        advanceUntilIdle()

        assertEquals(BackupMessage.Exported(recipes = 2), viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isWorking)
        assertTrue(dokumente.files.getValue(datei).isNotEmpty())
    }

    @Test
    fun `wer den Dateidialog abbricht, bekommt keine Meldung`() = runTest {
        val viewModel = createViewModel(roesti)
        advanceUntilIdle()

        viewModel.onExportTo(null)
        viewModel.onImportFrom(null)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isWorking)
        assertTrue(dokumente.files.isEmpty())
    }

    @Test
    fun `importieren sagt, was dazugekommen ist und was es schon gab`() = runTest {
        createViewModel(roesti, gulasch).also { it.onExportTo(datei) }
        advanceUntilIdle()
        val viewModel = createViewModel(roesti)
        advanceUntilIdle()

        viewModel.onImportFrom(datei)
        advanceUntilIdle()

        assertEquals(BackupMessage.Imported(imported = 1, skipped = 1), viewModel.uiState.value.message)
        assertFalse(
            viewModel.uiState.value.message!!
                .isError,
        )
        assertEquals(2, viewModel.uiState.value.recipeCount)
    }

    @Test
    fun `eine fremde Datei wird als Fehler gemeldet`() = runTest {
        dokumente.files[datei] = "kein Archiv".toByteArray()
        val viewModel = createViewModel(roesti)
        advanceUntilIdle()

        viewModel.onImportFrom(datei)
        advanceUntilIdle()

        assertEquals(BackupMessage.ImportFailed(ArchiveError.NOT_AN_ARCHIVE), viewModel.uiState.value.message)
        assertTrue(
            viewModel.uiState.value.message!!
                .isError,
        )
        assertEquals(1, viewModel.uiState.value.recipeCount)
    }

    @Test
    fun `laesst sich die Datei nicht schreiben, steht das da - bis zur naechsten Aktion`() = runTest {
        dokumente.locked += datei
        val viewModel = createViewModel(roesti)
        advanceUntilIdle()

        viewModel.onExportTo(datei)
        advanceUntilIdle()
        assertEquals(BackupMessage.ExportFailed, viewModel.uiState.value.message)

        dokumente.locked.clear()
        viewModel.onExportTo(datei)
        advanceUntilIdle()
        assertEquals(BackupMessage.Exported(recipes = 1), viewModel.uiState.value.message)
    }
}
