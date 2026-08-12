package ch.rezeptli.app.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.presentation.common.components.ChoiceChipGroup
import ch.rezeptli.app.presentation.common.components.RezeptliTopBar
import ch.rezeptli.app.presentation.common.components.SectionCard
import ch.rezeptli.app.presentation.common.label

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        uiState = uiState,
        onBack = onBack,
        onFirstNameChange = viewModel::onFirstNameChange,
        onCountrySelect = viewModel::onCountrySelect,
        onCuisineToggle = viewModel::onCuisineToggle,
        onDietToggle = viewModel::onDietToggle,
        onIntoleranceToggle = viewModel::onIntoleranceToggle,
        onHouseholdSizeChange = viewModel::onHouseholdSizeChange,
        onResetRequest = viewModel::onResetRequest,
        onResetDismiss = viewModel::onResetDismiss,
        onResetConfirm = viewModel::onResetConfirm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onFirstNameChange: (String) -> Unit,
    onCountrySelect: (Country) -> Unit,
    onCuisineToggle: (Cuisine) -> Unit,
    onDietToggle: (Diet) -> Unit,
    onIntoleranceToggle: (Intolerance) -> Unit,
    onHouseholdSizeChange: (Int?) -> Unit,
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = stringResource(R.string.einstellungen_profil), spacing = 8) {
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
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SettingsSection(stringResource(R.string.onboarding_land_frage)) {
                ChoiceChipGroup(
                    options = Country.entries,
                    selected = setOfNotNull(uiState.profile.country),
                    label = { it.label() },
                    onToggle = onCountrySelect,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SettingsSection(stringResource(R.string.onboarding_kuechen_titel)) {
                ChoiceChipGroup(
                    options = Cuisine.entries,
                    selected = uiState.profile.cuisines,
                    label = { it.label() },
                    onToggle = onCuisineToggle,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SettingsSection(stringResource(R.string.onboarding_ernaehrung_titel)) {
                ChoiceChipGroup(
                    options = Diet.entries,
                    selected = uiState.profile.diets,
                    label = { it.label() },
                    onToggle = onDietToggle,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SettingsSection(stringResource(R.string.onboarding_unvertraeglichkeiten_frage)) {
                Text(
                    stringResource(R.string.onboarding_unvertraeglichkeiten_hinweis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChoiceChipGroup(
                    options = Intolerance.entries,
                    selected = uiState.profile.intolerances,
                    label = { it.label() },
                    onToggle = onIntoleranceToggle,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SettingsSection(stringResource(R.string.onboarding_haushalt_titel)) {
                OutlinedTextField(
                    value = uiState.profile.householdSize
                        ?.toString()
                        .orEmpty(),
                    onValueChange = { text ->
                        onHouseholdSizeChange(text.filter { it.isDigit() }.take(2).toIntOrNull())
                    },
                    label = { Text(stringResource(R.string.onboarding_haushalt_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider()

            TextButton(
                onClick = onResetRequest,
                modifier = Modifier.heightIn(min = 48.dp),
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
                TextButton(onClick = onResetConfirm) {
                    Text(
                        stringResource(R.string.einstellungen_zuruecksetzen),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onResetDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    SectionCard(title = title, spacing = 8) { content() }
}
