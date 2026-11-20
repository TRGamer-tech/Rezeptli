package ch.rezeptli.app.presentation.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.rezeptli.app.domain.archive.ArchiveError
import ch.rezeptli.app.domain.archive.ArchivedPreferences
import ch.rezeptli.app.domain.usecase.ApplyPreferencesUseCase
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

/** Was aus den Vorlieben einer eingelesenen Datei geworden ist. */
enum class ImportedPreferences {
    /** Die Datei hatte keine - oder dieselben, die schon gelten. */
    NONE,

    /** Die Frage steht noch offen. */
    OFFERED,

    /** Sie gelten jetzt. */
    APPLIED,

    /** Die eigenen sind geblieben. */
    KEPT,
}

/** Was nach dem Exportieren oder Importieren zu sagen ist. */
sealed interface BackupMessage {
    data class Exported(
        val recipes: Int,
        /** Stehen auch die Vorlieben in der Datei? */
        val preferences: Boolean = false,
    ) : BackupMessage

    data class Imported(
        val imported: Int,
        val skipped: Int,
        val preferences: ImportedPreferences = ImportedPreferences.NONE,
    ) : BackupMessage

    data object ExportFailed : BackupMessage

    data class ImportFailed(
        val reason: ArchiveError,
    ) : BackupMessage

    val isError: Boolean get() = this is ExportFailed || this is ImportFailed
}

data class RecipeBackupUiState(
    val recipeCount: Int = 0,
    /**
     * Gehen die Vorlieben mit in die Datei? Ja, solange niemand es abwaehlt - etwa, weil die
     * Datei fuer jemand anderen gedacht ist.
     */
    val includePreferences: Boolean = true,
    val isWorking: Boolean = false,
    val message: BackupMessage? = null,
    /** Vorlieben aus der eben gelesenen Datei, die auf eine Antwort warten: uebernehmen oder nicht? */
    val offeredPreferences: ArchivedPreferences? = null,
) {
    /** Ohne Rezepte und ohne Vorlieben gibt es nichts zu sichern - der Knopf sagt das, statt eine leere Datei anzulegen. */
    val canExport: Boolean get() = hasSomethingToExport && !isWorking

    val hasSomethingToExport: Boolean get() = recipeCount > 0 || includePreferences

    val canImport: Boolean get() = !isWorking
}

/**
 * Die Sammlung als Datei: hinaus zum Aufbewahren, herein auf einem anderen Telefon.
 *
 * Die Datei selbst waehlt die Person im Dateidialog von Android - hier kommt nur noch ihre
 * Adresse an.
 *
 * Rezepte aus einer Datei kommen einfach dazu. Vorlieben nicht: Sie wuerden die eigenen
 * ersetzen, und die Datei kann von jemand anderem sein. Darum wird gefragt.
 */
@HiltViewModel
class RecipeBackupViewModel @Inject constructor(
    observeRecipeCount: ObserveRecipeCountUseCase,
    private val exportRecipes: ExportRecipesUseCase,
    private val importRecipes: ImportRecipesUseCase,
    private val applyPreferences: ApplyPreferencesUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecipeBackupUiState())
    val uiState: StateFlow<RecipeBackupUiState> = _uiState.asStateFlow()

    init {
        observeRecipeCount()
            .onEach { count -> _uiState.update { it.copy(recipeCount = count) } }
            .launchIn(viewModelScope)
    }

    fun onIncludePreferencesChange(include: Boolean) {
        _uiState.update { it.copy(includePreferences = include) }
    }

    /** [target] ist die im Dateidialog angelegte Datei - `null`, wenn die Person abgebrochen hat. */
    fun onExportTo(target: String?) {
        val state = _uiState.value
        if (target == null || state.isWorking) return
        work {
            when (val result = exportRecipes(target, includePreferences = state.includePreferences)) {
                is ExportResult.Done -> BackupMessage.Exported(result.recipes, result.preferences)
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
                is ImportResult.Done -> {
                    val offered = result.preferences
                    _uiState.update { it.copy(offeredPreferences = offered) }
                    BackupMessage.Imported(
                        imported = result.imported,
                        skipped = result.skipped,
                        preferences = if (offered != null) ImportedPreferences.OFFERED else ImportedPreferences.NONE,
                    )
                }

                is ImportResult.Failed -> BackupMessage.ImportFailed(result.reason)
            }
        }
    }

    /** Die Vorlieben aus der Datei sollen die eigenen ersetzen. */
    fun onPreferencesAccept() {
        val offered = _uiState.value.offeredPreferences ?: return
        _uiState.update { it.copy(offeredPreferences = null) }
        viewModelScope.launch {
            applyPreferences(offered)
            answered(ImportedPreferences.APPLIED)
        }
    }

    /** Die eigenen Vorlieben bleiben - auch, wer den Dialog einfach wegtippt, behaelt sie. */
    fun onPreferencesDecline() {
        if (_uiState.value.offeredPreferences == null) return
        _uiState.update { it.copy(offeredPreferences = null) }
        answered(ImportedPreferences.KEPT)
    }

    private fun answered(outcome: ImportedPreferences) {
        _uiState.update { state ->
            val message = state.message as? BackupMessage.Imported ?: return@update state
            state.copy(message = message.copy(preferences = outcome))
        }
    }

    private fun work(block: suspend () -> BackupMessage?) {
        _uiState.update { it.copy(isWorking = true, message = null, offeredPreferences = null) }
        viewModelScope.launch {
            val message = block()
            _uiState.update { it.copy(isWorking = false, message = message) }
        }
    }
}
