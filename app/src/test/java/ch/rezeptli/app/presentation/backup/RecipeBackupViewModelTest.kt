package ch.rezeptli.app.presentation.backup

import ch.rezeptli.app.domain.archive.ArchiveError
import ch.rezeptli.app.domain.archive.RecipeArchiveReader
import ch.rezeptli.app.domain.archive.RecipeArchiveWriter
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.plan.AdjustPlanUseCase
import ch.rezeptli.app.domain.plan.PlanGenerator
import ch.rezeptli.app.domain.plan.PlanPersister
import ch.rezeptli.app.domain.plan.RecipeClassifier
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.settings.ThemeChoice
import ch.rezeptli.app.domain.shopping.ShoppingListAggregator
import ch.rezeptli.app.domain.usecase.ApplyPreferencesUseCase
import ch.rezeptli.app.domain.usecase.ExportRecipesUseCase
import ch.rezeptli.app.domain.usecase.ImportRecipesUseCase
import ch.rezeptli.app.domain.usecase.ObserveRecipeCountUseCase
import ch.rezeptli.app.fake.FakeAppearanceRepository
import ch.rezeptli.app.fake.FakeDocuments
import ch.rezeptli.app.fake.FakePhotoFiles
import ch.rezeptli.app.fake.FakePlanRepository
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.fake.FakeShoppingListRepository
import ch.rezeptli.app.fake.FakeUserProfileRepository
import ch.rezeptli.app.util.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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

    private fun createViewModel(
        vararg rezepte: Recipe,
        profil: FakeUserProfileRepository = FakeUserProfileRepository(UserProfile(onboardingCompleted = true)),
        aussehen: FakeAppearanceRepository = FakeAppearanceRepository(),
    ): RecipeBackupViewModel {
        val sammlung = FakeRecipeRepository(rezepte.toList())
        val plan = FakePlanRepository()
        return RecipeBackupViewModel(
            observeRecipeCount = ObserveRecipeCountUseCase(sammlung),
            exportRecipes = ExportRecipesUseCase(
                sammlung,
                fotos,
                dokumente,
                RecipeArchiveWriter(),
                profil,
                aussehen,
                UnconfinedTestDispatcher(),
            ),
            importRecipes = ImportRecipesUseCase(
                sammlung,
                fotos,
                dokumente,
                RecipeArchiveReader(),
                profil,
                aussehen,
                UnconfinedTestDispatcher(),
            ),
            applyPreferences = ApplyPreferencesUseCase(
                profileRepository = profil,
                appearanceRepository = aussehen,
                adjustPlan = AdjustPlanUseCase(
                    profileRepository = profil,
                    planRepository = plan,
                    recipeRepository = sammlung,
                    classifier = RecipeClassifier(),
                    generator = PlanGenerator(ShoppingListAggregator()),
                    persister = PlanPersister(sammlung, FakeShoppingListRepository(), plan),
                ),
            ),
        )
    }

    private val roesti = Recipe(id = 1L, title = "Rösti", instructions = "Braten.")
    private val gulasch = Recipe(id = 2L, title = "Gulasch", instructions = "Schmoren.")

    @Test
    fun `ohne Rezepte bleiben die Vorlieben zum Sichern - ohne beides gibt es nichts zu exportieren`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.recipeCount)
        // Die Vorlieben gehen mit, solange niemand es abwaehlt.
        assertTrue(viewModel.uiState.value.includePreferences)
        assertTrue(viewModel.uiState.value.canExport)

        viewModel.onIncludePreferencesChange(false)

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

        assertEquals(BackupMessage.Exported(recipes = 2, preferences = true), viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isWorking)
        assertTrue(dokumente.files.getValue(datei).isNotEmpty())
    }

    @Test
    fun `mit ausgeschaltetem Schalter gehen nur die Rezepte in die Datei`() = runTest {
        val viewModel = createViewModel(roesti)
        advanceUntilIdle()
        viewModel.onIncludePreferencesChange(false)

        viewModel.onExportTo(datei)
        advanceUntilIdle()

        assertEquals(BackupMessage.Exported(recipes = 1, preferences = false), viewModel.uiState.value.message)
    }

    // ------------------------------------------------------------------ Vorlieben aus einer Datei

    private val lena =
        UserProfile(firstName = "Lena", intolerances = setOf(Intolerance.NUESSE), onboardingCompleted = true)

    /** Lena exportiert Rezepte und Vorlieben in die Datei, aus der danach gelesen wird. */
    private fun TestScope.lenasDatei() {
        createViewModel(
            roesti,
            gulasch,
            profil = FakeUserProfileRepository(lena),
            aussehen = FakeAppearanceRepository(theme = ThemeChoice.DUNKEL),
        ).also { it.onExportTo(datei) }
        advanceUntilIdle()
    }

    @Test
    fun `stehen in der Datei andere Vorlieben, wird gefragt - und Ja uebernimmt sie`() = runTest {
        lenasDatei()
        val profil = FakeUserProfileRepository(UserProfile(firstName = "Beat", onboardingCompleted = true))
        val aussehen = FakeAppearanceRepository()
        val viewModel = createViewModel(profil = profil, aussehen = aussehen)
        advanceUntilIdle()

        viewModel.onImportFrom(datei)
        advanceUntilIdle()

        // Die Rezepte sind da - die Vorlieben warten auf eine Antwort.
        assertEquals(
            BackupMessage.Imported(imported = 2, skipped = 0, preferences = ImportedPreferences.OFFERED),
            viewModel.uiState.value.message,
        )
        assertEquals(
            "Lena",
            viewModel.uiState.value.offeredPreferences
                ?.profile
                ?.firstName,
        )
        assertEquals("Beat", profil.current.firstName)

        viewModel.onPreferencesAccept()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.offeredPreferences)
        assertEquals(lena, profil.current)
        assertEquals(ThemeChoice.DUNKEL, aussehen.theme.value)
        assertEquals(
            BackupMessage.Imported(imported = 2, skipped = 0, preferences = ImportedPreferences.APPLIED),
            viewModel.uiState.value.message,
        )
    }

    @Test
    fun `wer Nein sagt oder den Dialog wegtippt, behaelt seine Vorlieben`() = runTest {
        lenasDatei()
        val meins = UserProfile(firstName = "Beat", intolerances = setOf(Intolerance.EIER), onboardingCompleted = true)
        val profil = FakeUserProfileRepository(meins)
        val viewModel = createViewModel(profil = profil)
        advanceUntilIdle()
        viewModel.onImportFrom(datei)
        advanceUntilIdle()

        viewModel.onPreferencesDecline()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.offeredPreferences)
        assertEquals(meins, profil.current)
        assertEquals(
            BackupMessage.Imported(imported = 2, skipped = 0, preferences = ImportedPreferences.KEPT),
            viewModel.uiState.value.message,
        )
        // Die Rezepte sind trotzdem da.
        assertEquals(2, viewModel.uiState.value.recipeCount)
    }

    @Test
    fun `gleichen die Vorlieben der Datei den eigenen, kommt keine Frage`() = runTest {
        lenasDatei()
        val viewModel = createViewModel(
            profil = FakeUserProfileRepository(lena),
            aussehen = FakeAppearanceRepository(theme = ThemeChoice.DUNKEL),
        )
        advanceUntilIdle()

        viewModel.onImportFrom(datei)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.offeredPreferences)
        assertEquals(BackupMessage.Imported(imported = 2, skipped = 0), viewModel.uiState.value.message)
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
        assertEquals(BackupMessage.Exported(recipes = 1, preferences = true), viewModel.uiState.value.message)
    }
}
