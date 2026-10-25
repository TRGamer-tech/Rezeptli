package ch.rezeptli.app.presentation.plan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.presentation.common.ObserveAsEvents
import ch.rezeptli.app.presentation.common.components.RezeptliTopBar
import ch.rezeptli.app.presentation.common.theme.Tokens

/**
 * Die Einrichtung: acht kurze Fragen, dann baut Rezeptli den Plan.
 *
 * Jede Frage nimmt den ganzen Bildschirm ein und hat genau eine Aufgabe - so bleibt jede
 * Antwort ein Fingertipp. Nichts ist Pflicht, das Vorbelegte ist ein brauchbarer Plan.
 */
@Composable
fun PlanSetupRoute(
    onFinished: () -> Unit,
    viewModel: PlanSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            PlanSetupEvent.Finished -> onFinished()
        }
    }

    PlanSetupScreen(uiState = uiState, viewModel = viewModel)
}

@Composable
private fun PlanSetupScreen(uiState: PlanSetupUiState, viewModel: PlanSetupViewModel) {
    val profile = uiState.profile

    Scaffold(
        topBar = {
            RezeptliTopBar(
                title = stringResource(R.string.setup_schritt, uiState.stepNumber, uiState.stepCount),
                titleStyle = MaterialTheme.typography.labelLarge,
                onBack = if (uiState.step.isFirst) null else viewModel::onBack,
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!uiState.step.isFirst) {
                    OutlinedButton(
                        onClick = viewModel::onBack,
                        shape = Tokens.Radius.PillShape,
                        modifier = Modifier.heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.setup_zurueck)) }
                }
                Button(
                    onClick = viewModel::onNext,
                    enabled = !uiState.isSaving,
                    shape = Tokens.Radius.PillShape,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (uiState.step.isLast) R.string.setup_plan_erstellen else R.string.setup_weiter,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LinearProgressIndicator(
                progress = { uiState.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )

            AnimatedContent(
                targetState = uiState.step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val direction = if (forward) 1 else -1
                    (
                        slideInHorizontally(tween(Tokens.Duration.Base, easing = Tokens.Ease.OutQuad)) {
                            it / 4 * direction
                        } + fadeIn(tween(Tokens.Duration.Base))
                    ).togetherWith(
                        slideOutHorizontally(tween(Tokens.Duration.Base, easing = Tokens.Ease.OutQuad)) {
                            -it / 4 * direction
                        } + fadeOut(tween(Tokens.Duration.Fast)),
                    )
                },
                modifier = Modifier.weight(1f),
                label = "Einrichtungsschritt",
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    when (step) {
                        SetupStep.POSTLEITZAHL -> StepFrame(R.string.setup_plz_titel, R.string.setup_plz_text) {
                            OutlinedTextField(
                                value = profile.zip,
                                onValueChange = viewModel::onZipChange,
                                label = { Text(stringResource(R.string.setup_plz_label)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        SetupStep.LADEN -> StepFrame(R.string.setup_laden_titel, R.string.setup_laden_text) {
                            StoreGrid(selected = profile.store, onSelect = viewModel::onStoreSelect)
                        }

                        SetupStep.PERSONEN -> StepFrame(R.string.setup_personen_titel, R.string.setup_personen_text) {
                            PersonStepper(persons = profile.persons, onChange = viewModel::onPersonsChange)
                        }

                        SetupStep.TAGE -> StepFrame(R.string.setup_tage_titel, R.string.setup_tage_text) {
                            DayPicker(selected = profile.cookDays, onToggle = viewModel::onDayToggle)
                        }

                        SetupStep.BUDGET -> StepFrame(R.string.setup_budget_titel, R.string.setup_budget_text) {
                            BudgetSlider(
                                budget = profile.weeklyBudget ?: UserProfile.DEFAULT_BUDGET,
                                persons = profile.persons,
                                meals = profile.mealsPerWeek,
                                onChange = viewModel::onBudgetChange,
                            )
                        }

                        SetupStep.STIMMUNG -> StepFrame(R.string.setup_stimmung_titel, R.string.setup_stimmung_text) {
                            MoodPicker(selected = profile.moods, onToggle = viewModel::onMoodToggle)
                        }

                        SetupStep.ERNAEHRUNG -> StepFrame(
                            R.string.setup_ernaehrung_titel,
                            R.string.setup_ernaehrung_text,
                        ) {
                            DietPicker(
                                profile = profile,
                                onMainDiet = viewModel::onMainDiet,
                                onToggleDiet = viewModel::onDietToggle,
                                onToggleIntolerance = viewModel::onIntoleranceToggle,
                            )
                        }

                        SetupStep.GERAETE -> StepFrame(R.string.setup_geraete_titel, R.string.setup_geraete_text) {
                            AppliancePicker(selected = profile.appliances, onToggle = viewModel::onApplianceToggle)
                        }
                    }
                }
            }
        }
    }
}

/** Titel und Erklaerung einer Frage, darunter die Antwortflaeche. */
@Composable
private fun StepFrame(
    titleRes: Int,
    textRes: Int,
    content: @Composable () -> Unit,
) {
    Text(text = stringResource(titleRes), style = MaterialTheme.typography.headlineMedium)
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    content()
}
