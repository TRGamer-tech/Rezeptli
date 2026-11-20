package ch.rezeptli.app.presentation.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.archive.ArchiveError
import ch.rezeptli.app.domain.archive.ArchivedPreferences
import ch.rezeptli.app.presentation.common.components.BouncyFilledTonalButton
import ch.rezeptli.app.presentation.common.components.BouncyOutlinedButton
import ch.rezeptli.app.presentation.common.components.BouncyTextButton
import java.time.LocalDate

/**
 * Exportieren und Importieren der Rezeptsammlung - der Inhalt einer Karte auf der
 * Vorlieben-Seite.
 *
 * Wohin die Datei kommt und welche gelesen wird, entscheidet die Person im Dateidialog von
 * Android. Die App braucht dafuer keine Berechtigung fuer den Speicher.
 *
 * Die Vorlieben gehen mit in die Datei, solange der Schalter an ist. Beim Einlesen ersetzen
 * sie die eigenen nie von selbst: Es wird gefragt.
 */
@Composable
fun RecipeBackupSection(viewModel: RecipeBackupViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP)) { uri ->
        viewModel.onExportTo(uri?.toString())
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onImportFrom(uri?.toString())
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.sichern_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PreferencesSwitch(
            checked = uiState.includePreferences,
            enabled = !uiState.isWorking,
            onChange = viewModel::onIncludePreferencesChange,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BouncyFilledTonalButton(
                // Das Datum im Namen: Wer oefter sichert, sieht auf einen Blick, welche Datei die neuste ist.
                onClick = { exporter.launch("rezeptli-rezepte-${LocalDate.now()}.zip") },
                enabled = uiState.canExport,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.sichern_export), maxLines = 1)
            }
            BouncyOutlinedButton(
                onClick = { importer.launch(IMPORT_TYPES) },
                enabled = uiState.canImport,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.sichern_import), maxLines = 1)
            }
        }

        val status = when {
            uiState.isWorking -> stringResource(R.string.sichern_laeuft)
            uiState.message != null -> uiState.message?.text()
            !uiState.hasSomethingToExport -> stringResource(R.string.sichern_leer)
            else -> null
        }
        if (!status.isNullOrBlank()) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.message?.isError == true && !uiState.isWorking) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                // Das Ergebnis erscheint ohne Zutun - wer vorgelesen bekommt, soll es auch erfahren.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

    uiState.offeredPreferences?.let { offered ->
        PreferencesQuestion(
            offered = offered,
            onAccept = viewModel::onPreferencesAccept,
            onDecline = viewModel::onPreferencesDecline,
        )
    }
}

/** Der Schalter "Vorlieben mit in die Datei" - die ganze Zeile schaltet. */
@Composable
private fun PreferencesSwitch(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(R.string.sichern_vorlieben), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.sichern_vorlieben_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Die Zeile schaltet - der Schalter selbst ist nur die Anzeige.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * Die Frage nach dem Einlesen: Sollen die Vorlieben aus der Datei die eigenen ersetzen?
 *
 * Wer den Dialog wegtippt, behaelt die eigenen - die sichere Antwort, falls die Datei von
 * jemand anderem stammt.
 */
@Composable
private fun PreferencesQuestion(offered: ArchivedPreferences, onAccept: () -> Unit, onDecline: () -> Unit) {
    val name = offered.profile.firstName
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.sichern_vorlieben_frage_titel)) },
        text = {
            Text(
                listOfNotNull(
                    stringResource(R.string.sichern_vorlieben_frage_text),
                    if (name.isBlank()) null else stringResource(R.string.sichern_vorlieben_frage_von, name),
                ).joinToString(" "),
            )
        },
        confirmButton = {
            BouncyTextButton(onClick = onAccept) { Text(stringResource(R.string.sichern_vorlieben_uebernehmen)) }
        },
        dismissButton = {
            BouncyTextButton(onClick = onDecline) { Text(stringResource(R.string.sichern_vorlieben_behalten)) }
        },
    )
}

@Composable
private fun BackupMessage.text(): String = when (this) {
    is BackupMessage.Exported -> when {
        !preferences -> pluralStringResource(R.plurals.sichern_exportiert, recipes, recipes)
        recipes == 0 -> stringResource(R.string.sichern_nur_vorlieben_exportiert)
        else -> pluralStringResource(R.plurals.sichern_exportiert_mit_vorlieben, recipes, recipes)
    }

    is BackupMessage.Imported -> {
        // Zwei Saetze, jeder nur, wenn er etwas zu sagen hat: was mit den Rezepten geschah und
        // was mit den Vorlieben.
        val rezepte = when {
            imported == 0 && skipped == 0 -> null

            imported == 0 -> pluralStringResource(R.plurals.sichern_alle_bekannt, skipped, skipped)

            skipped == 0 -> pluralStringResource(R.plurals.sichern_importiert, imported, imported)

            else -> pluralStringResource(R.plurals.sichern_importiert, imported, imported) + " " +
                pluralStringResource(R.plurals.sichern_uebergangen, skipped, skipped)
        }
        val vorlieben = when (preferences) {
            ImportedPreferences.APPLIED -> stringResource(R.string.sichern_vorlieben_uebernommen)
            ImportedPreferences.KEPT -> stringResource(R.string.sichern_vorlieben_geblieben)
            ImportedPreferences.OFFERED -> null
            ImportedPreferences.NONE ->
                if (rezepte == null) stringResource(R.string.sichern_vorlieben_gleich) else null
        }
        listOfNotNull(rezepte, vorlieben).joinToString(" ")
    }

    BackupMessage.ExportFailed -> stringResource(R.string.sichern_fehler_schreiben)

    is BackupMessage.ImportFailed -> stringResource(
        when (reason) {
            ArchiveError.UNREADABLE -> R.string.sichern_fehler_lesen
            ArchiveError.NOT_AN_ARCHIVE -> R.string.sichern_fehler_format
            ArchiveError.NEWER_VERSION -> R.string.sichern_fehler_neuer
            ArchiveError.TOO_LARGE -> R.string.sichern_fehler_gross
            ArchiveError.EMPTY -> R.string.sichern_fehler_leer
        },
    )
}

private const val ZIP = "application/zip"

/** Manche Dateiverwaltungen kennen ZIP-Dateien nur als "irgendeine Datei" - die sollen auch waehlbar sein. */
private val IMPORT_TYPES = arrayOf(ZIP, "application/x-zip-compressed", "application/octet-stream", "application/json")
