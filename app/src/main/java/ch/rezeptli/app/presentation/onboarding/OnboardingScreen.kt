package ch.rezeptli.app.presentation.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.presentation.common.ObserveAsEvents
import ch.rezeptli.app.presentation.common.components.ChoiceChipGroup
import ch.rezeptli.app.presentation.common.components.RezeptliTopBar
import ch.rezeptli.app.presentation.common.label

/**
 * Die Fragen beim ersten Start.
 *
 * Jede Frage darf unbeantwortet bleiben, und "Ueberspringen" ist auf jedem Schritt
 * sichtbar - nicht versteckt. Niemand soll das Gefuehl haben, sich durch ein Formular
 * kaempfen zu muessen, bevor die App etwas tut.
 */
@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            OnboardingEvent.Finished -> onFinished()
        }
    }

    OnboardingScreen(
        uiState = uiState,
        onFirstNameChange = viewModel::onFirstNameChange,
        onCountrySelect = viewModel::onCountrySelect,
        onCuisineToggle = viewModel::onCuisineToggle,
        onDietToggle = viewModel::onDietToggle,
        onIntoleranceToggle = viewModel::onIntoleranceToggle,
        onHouseholdSizeChange = viewModel::onHouseholdSizeChange,
        onBack = viewModel::onBack,
        onNext = viewModel::onNext,
        onSkip = viewModel::onSkip,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onFirstNameChange: (String) -> Unit,
    onCountrySelect: (Country) -> Unit,
    onCuisineToggle: (Cuisine) -> Unit,
    onDietToggle: (Diet) -> Unit,
    onIntoleranceToggle: (Intolerance) -> Unit,
    onHouseholdSizeChange: (Int?) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    Scaffold(
        topBar = {
            RezeptliTopBar(
                title = stringResource(
                    R.string.onboarding_schritt,
                    uiState.stepNumber,
                    uiState.stepCount,
                ),
                titleStyle = MaterialTheme.typography.labelLarge,
                actions = {
                    TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.onboarding_ueberspringen))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LinearProgressIndicator(
                progress = { uiState.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (uiState.step) {
                    OnboardingStep.WILLKOMMEN -> WelcomeStep(
                        profile = uiState.profile,
                        onFirstNameChange = onFirstNameChange,
                        onCountrySelect = onCountrySelect,
                    )

                    OnboardingStep.KUECHEN -> CuisineStep(
                        profile = uiState.profile,
                        onCuisineToggle = onCuisineToggle,
                    )

                    OnboardingStep.ERNAEHRUNG -> DietStep(
                        profile = uiState.profile,
                        onDietToggle = onDietToggle,
                        onIntoleranceToggle = onIntoleranceToggle,
                    )

                    OnboardingStep.HAUSHALT -> HouseholdStep(
                        profile = uiState.profile,
                        onHouseholdSizeChange = onHouseholdSizeChange,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!uiState.step.isFirst) {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.onboarding_zurueck))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onNext,
                    enabled = !uiState.isSaving,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(
                            if (uiState.step.isLast) R.string.onboarding_fertig else R.string.onboarding_weiter,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepHeading(title: String, explanation: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Text(
        explanation,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WelcomeStep(
    profile: UserProfile,
    onFirstNameChange: (String) -> Unit,
    onCountrySelect: (Country) -> Unit,
) {
    StepHeading(
        title = stringResource(R.string.onboarding_willkommen_titel),
        explanation = stringResource(R.string.onboarding_willkommen_text),
    )

    OutlinedTextField(
        value = profile.firstName,
        onValueChange = onFirstNameChange,
        label = { Text(stringResource(R.string.onboarding_vorname)) },
        supportingText = { Text(stringResource(R.string.onboarding_vorname_hinweis)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(8.dp))

    Text(
        stringResource(R.string.onboarding_land_frage),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        stringResource(R.string.onboarding_land_hinweis),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ChoiceChipGroup(
        options = Country.entries,
        selected = setOfNotNull(profile.country),
        label = { it.label() },
        onToggle = onCountrySelect,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CuisineStep(profile: UserProfile, onCuisineToggle: (Cuisine) -> Unit) {
    StepHeading(
        title = stringResource(R.string.onboarding_kuechen_titel),
        explanation = stringResource(R.string.onboarding_kuechen_text),
    )
    ChoiceChipGroup(
        options = Cuisine.entries,
        selected = profile.cuisines,
        label = { it.label() },
        onToggle = onCuisineToggle,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DietStep(
    profile: UserProfile,
    onDietToggle: (Diet) -> Unit,
    onIntoleranceToggle: (Intolerance) -> Unit,
) {
    StepHeading(
        title = stringResource(R.string.onboarding_ernaehrung_titel),
        explanation = stringResource(R.string.onboarding_ernaehrung_text),
    )
    ChoiceChipGroup(
        options = Diet.entries,
        selected = profile.diets,
        label = { it.label() },
        onToggle = onDietToggle,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(8.dp))

    Text(
        stringResource(R.string.onboarding_unvertraeglichkeiten_frage),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        stringResource(R.string.onboarding_unvertraeglichkeiten_hinweis),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ChoiceChipGroup(
        options = Intolerance.entries,
        selected = profile.intolerances,
        label = { it.label() },
        onToggle = onIntoleranceToggle,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HouseholdStep(profile: UserProfile, onHouseholdSizeChange: (Int?) -> Unit) {
    StepHeading(
        title = stringResource(R.string.onboarding_haushalt_titel),
        explanation = stringResource(R.string.onboarding_haushalt_text),
    )

    OutlinedTextField(
        value = profile.householdSize?.toString().orEmpty(),
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
