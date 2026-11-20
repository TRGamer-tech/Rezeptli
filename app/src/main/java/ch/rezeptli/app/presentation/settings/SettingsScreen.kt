package ch.rezeptli.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.presentation.common.components.BouncyTextButton
import ch.rezeptli.app.presentation.common.components.ChoiceChipGroup
import ch.rezeptli.app.presentation.common.components.IconMotion
import ch.rezeptli.app.presentation.common.components.MotionIcon
import ch.rezeptli.app.presentation.common.components.RezeptliTopBar
import ch.rezeptli.app.presentation.common.components.SectionCard
import ch.rezeptli.app.presentation.common.components.entrance
import ch.rezeptli.app.presentation.common.label
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme

@Composable
fun SettingsRoute(
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        onBack = onBack,
        onFirstNameChange = viewModel::onFirstNameChange,
        onCountrySelect = viewModel::onCountrySelect,
        onCuisineToggle = viewModel::onCuisineToggle,
        onReduceMotionChange = viewModel::onReduceMotionChange,
        onResetRequest = viewModel::onResetRequest,
        onResetDismiss = viewModel::onResetDismiss,
        onResetConfirm = viewModel::onResetConfirm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: (() -> Unit)?,
    onFirstNameChange: (String) -> Unit,
    onCountrySelect: (Country) -> Unit,
    onCuisineToggle: (Cuisine) -> Unit,
    onReduceMotionChange: (Boolean) -> Unit,
    onResetRequest: () -> Unit,
    onResetDismiss: () -> Unit,
    onResetConfirm: () -> Unit,
) {
    Scaffold(
        topBar = {
            RezeptliTopBar(
                title = stringResource(R.string.einstellungen_titel),
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.einstellungen_profil), spacing = 8, index = 0) {
                Text(
                    stringResource(R.string.einstellungen_profil_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = uiState.profile.firstName,
                    onValueChange = onFirstNameChange,
                    label = { Text(stringResource(R.string.onboarding_vorname)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = stringResource(R.string.onboarding_land_frage), spacing = 8, index = 1) {
                ChoiceChipGroup(
                    options = Country.entries,
                    selected = setOfNotNull(uiState.profile.country),
                    label = { it.label() },
                    onToggle = onCountrySelect,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = stringResource(R.string.onboarding_kuechen_titel), spacing = 8, index = 2) {
                ChoiceChipGroup(
                    options = Cuisine.entries,
                    selected = uiState.profile.cuisines,
                    label = { it.label() },
                    onToggle = onCuisineToggle,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionCard(title = stringResource(R.string.einstellungen_darstellung), spacing = 8, index = 3) {
                MotionSetting(
                    reduceMotion = uiState.reduceMotion,
                    onChange = onReduceMotionChange,
                )
            }

            BouncyTextButton(
                onClick = onResetRequest,
                modifier = Modifier
                    .entrance(4)
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    stringResource(R.string.einstellungen_zuruecksetzen),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (uiState.showResetDialog) {
        AlertDialog(
            onDismissRequest = onResetDismiss,
            title = { Text(stringResource(R.string.einstellungen_zuruecksetzen_frage)) },
            text = { Text(stringResource(R.string.einstellungen_zuruecksetzen_text)) },
            confirmButton = {
                BouncyTextButton(onClick = onResetConfirm) {
                    Text(
                        stringResource(R.string.einstellungen_zuruecksetzen),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                BouncyTextButton(onClick = onResetDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * Der Schalter fuer weniger Bewegung.
 *
 * Hat Android "Animationen entfernen" eingeschaltet, ist Rezeptli ohnehin ruhig - dann
 * steht der Schalter auf an und laesst sich nicht ausschalten. Die Android-Einstellung
 * ist eine Bedienungshilfe; eine App sollte sie nicht uebergehen.
 */
@Composable
private fun MotionSetting(
    reduceMotion: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val systemReduced = RezeptliTheme.motion.reduced && !reduceMotion
    val checked = reduceMotion || systemReduced

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked,
                enabled = !systemReduced,
                role = Role.Switch,
                onValueChange = onChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MotionIcon(
            imageVector = Icons.Outlined.SlowMotionVideo,
            contentDescription = null,
            motion = IconMotion.WIGGLE,
            trigger = checked,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.einstellungen_bewegung),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (systemReduced) R.string.einstellungen_bewegung_system else R.string.einstellungen_bewegung_text,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Die ganze Zeile schaltet - der Schalter selbst ist nur die Anzeige.
        Switch(checked = checked, onCheckedChange = null, enabled = !systemReduced)
    }
}
