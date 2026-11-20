package ch.rezeptli.app.presentation.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.archive.ArchiveError
import ch.rezeptli.app.domain.usecase.ExportRecipesUseCase
import ch.rezeptli.app.domain.usecase.ExportResult
import ch.rezeptli.app.domain.usecase.ImportRecipesUseCase
import ch.rezeptli.app.domain.usecase.ImportResult
import ch.rezeptli.app.domain.usecase.ObserveRecipeCountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Was nach dem Exportieren oder Importieren zu sagen ist. */
sealed interface BackupMessage {
    data class Exported(
        val recipes: Int,
    ) : BackupMessage

    data class Imported(
        val imported: Int,
        val skipped: Int,
    ) : BackupMessage

    data object ExportFailed : BackupMessage

    data class ImportFailed(
        val reason: ArchiveError,
    ) : BackupMessage

    val isError: Boolean get() = this is ExportFailed || this is ImportFailed
}

data class RecipeBackupUiState(
    val recipeCount: Int = 0,
    val isWorking: Boolean = false,
    val message: BackupMessage? = null,
) {
    /** Ohne Rezepte gibt es nichts zu sichern - der Knopf sagt das, statt eine leere Datei anzulegen. */
    val canExport: Boolean get() = recipeCount > 0 && !isWorking

    val canImport: Boolean get() = !isWorking
}

/**
 * Die Sammlung als Datei: hinaus zum Aufbewahren, herein auf einem anderen Telefon.
 *
 * Die Datei selbst waehlt die Person im Dateidialog von Android - hier kommt nur noch ihre
 * Adresse an.
 */
@HiltViewModel
class RecipeBackupViewModel @Inject constructor(
    observeRecipeCount: ObserveRecipeCountUseCase,
    private val exportRecipes: ExportRecipesUseCase,
    private val importRecipes: ImportRecipesUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecipeBackupUiState())
    val uiState: StateFlow<RecipeBackupUiState> = _uiState.asStateFlow()

    init {
        observeRecipeCount()
            .onEach { count -> _uiState.update { it.copy(recipeCount = count) } }
            .launchIn(viewModelScope)
    }

    /** [target] ist die im Dateidialog angelegte Datei - `null`, wenn die Person abgebrochen hat. */
    fun onExportTo(target: String?) {
        if (target == null || _uiState.value.isWorking) return
        work {
            when (val result = exportRecipes(target)) {
                is ExportResult.Done -> BackupMessage.Exported(result.recipes)
                // Kann nur geschehen, wenn das letzte Rezept waehrend des Dateidialogs geloescht wurde.
                ExportResult.Nothing -> null
                ExportResult.Failed -> BackupMessage.ExportFailed
            }
        }
    }

    /** [source] ist die im Dateidialog gewaehlte Datei - `null`, wenn die Person abgebrochen hat. */
    fun onImportFrom(source: String?) {
        if (source == null || _uiState.value.isWorking) return
        work {
            when (val result = importRecipes(source)) {
                is ImportResult.Done -> BackupMessage.Imported(result.imported, result.skipped)
                is ImportResult.Failed -> BackupMessage.ImportFailed(result.reason)
            }
        }
    }

    private fun work(block: suspend () -> BackupMessage?) {
        _uiState.update { it.copy(isWorking = true, message = null) }
        viewModelScope.launch {
            val message = block()
            _uiState.update { it.copy(isWorking = false, message = message) }
        }
    }
}
