package ch.rezeptli.app.presentation.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.BuildConfig
import ch.rezeptli.app.R
import ch.rezeptli.app.presentation.backup.RecipeBackupSection
import ch.rezeptli.app.presentation.common.components.BouncyButton
import ch.rezeptli.app.presentation.common.components.BouncyTextButton
import ch.rezeptli.app.presentation.common.components.RezeptliTopBar
import ch.rezeptli.app.presentation.common.components.SectionCard
import ch.rezeptli.app.presentation.common.theme.Tokens

/**
 * Der Vorlieben-Tab: alles, was die App ueber einen weiss, auf einer Seite.
 *
 * Vier Gruppen von oben nach unten - wer man ist, was man mag, wie die Woche geplant wird,
 * und wie die App sich verhaelt. Jede Karte sagt in einem Satz, was die Angabe bewirkt:
 * Eine Einstellung, von der niemand weiss, was sie tut, ist keine.
 */
@Composable
fun PreferencesRoute(
    onBuildPlan: () -> Unit,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profile = uiState.profile

    Scaffold(
        topBar = { RezeptliTopBar(title = stringResource(R.string.vorlieben_titel)) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GroupTitle(R.string.vorlieben_gruppe_du)
            SectionCard(title = stringResource(R.string.einstellungen_profil), index = 0) {
                Hint(R.string.einstellungen_profil_text)
                NameField(name = profile.firstName, onChange = viewModel::onNameChange)
            }
            SectionCard(title = stringResource(R.string.onboarding_land_frage), index = 1) {
                Hint(R.string.vorlieben_land_text)
                CountryPicker(selected = profile.country, onSelect = viewModel::onCountrySelect)
            }

            GroupTitle(R.string.vorlieben_gruppe_geschmack)
            SectionCard(title = stringResource(R.string.onboarding_kuechen_titel), index = 2) {
                Hint(R.string.vorlieben_kuechen_text)
                CuisinePicker(selected = profile.cuisines, onToggle = viewModel::onCuisineToggle)
            }
            SectionCard(title = stringResource(R.string.setup_ernaehrung_titel), index = 3) {
                Hint(R.string.setup_ernaehrung_text)
                DietPicker(
                    profile = profile,
                    onMainDiet = viewModel::onMainDiet,
                    onToggleDiet = viewModel::onDietToggle,
                )
            }
            SectionCard(title = stringResource(R.string.setup_abneigung_titel), index = 4) {
                Hint(R.string.setup_abneigung_text)
                DislikePicker(
                    selected = profile.dislikes,
                    custom = profile.customDislikes,
                    onToggle = viewModel::onDislikeToggle,
                    onAddCustom = viewModel::onCustomDislikeAdd,
                    onRemoveCustom = viewModel::onCustomDislikeRemove,
                )
            }
            SectionCard(title = stringResource(R.string.setup_unvertraeglich_titel), index = 5) {
                IntolerancePicker(selected = profile.intolerances, onToggle = viewModel::onIntoleranceToggle)
            }

            GroupTitle(R.string.vorlieben_gruppe_planung)
            SectionCard(title = stringResource(R.string.setup_personen_titel), index = 6) {
                Hint(R.string.setup_personen_text)
                PersonStepper(persons = profile.persons, onChange = viewModel::onPersonsChange)
            }
            SectionCard(title = stringResource(R.string.setup_tage_titel), index = 6) {
                DayPicker(selected = profile.days.toSet(), onToggle = viewModel::onDayToggle)
                MealSlotPicker(profile = profile, onToggle = viewModel::onSlotToggle)
            }
            SectionCard(title = stringResource(R.string.setup_laden_titel), index = 6) {
                StoreGrid(
                    market = profile.market,
                    selected = profile.stores,
                    onToggle = viewModel::onStoreToggle,
                    onMakeMain = viewModel::onMainStore,
                )
            }
            SectionCard(title = stringResource(R.string.setup_budget_titel), index = 6) {
                Hint(R.string.setup_budget_text)
                BudgetSlider(
                    budget = profile.budget,
                    currency = profile.market.currency,
                    persons = profile.persons,
                    meals = profile.mealsPerWeek,
                    onChange = viewModel::onBudgetChange,
                )
            }
            SectionCard(title = stringResource(R.string.setup_stimmung_titel), index = 6) {
                Hint(R.string.setup_stimmung_text)
                MoodPicker(selected = profile.moods, onToggle = viewModel::onMoodToggle)
            }
            SectionCard(title = stringResource(R.string.setup_geraete_titel), index = 6) {
                Hint(R.string.setup_geraete_text)
                AppliancePicker(selected = profile.appliances, onToggle = viewModel::onApplianceToggle)
            }
            Hint(R.string.vorlieben_plan_hinweis)
            BouncyButton(
                onClick = onBuildPlan,
                shape = Tokens.Radius.PillShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.vorlieben_plan_neu)) }

            GroupTitle(R.string.vorlieben_gruppe_app)
            SectionCard(title = stringResource(R.string.design_titel), index = 6) {
                ThemePicker(selected = uiState.theme, onSelect = viewModel::onThemeSelect)
            }
            SectionCard(title = stringResource(R.string.einstellungen_darstellung), spacing = 8, index = 6) {
                MotionSetting(
                    reduceMotion = uiState.reduceMotion,
                    onChange = viewModel::onReduceMotionChange,
                )
            }
            SectionCard(title = stringResource(R.string.sichern_titel), index = 6) {
                RecipeBackupSection()
            }
            BouncyTextButton(
                onClick = viewModel::onResetRequest,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    stringResource(R.string.einstellungen_zuruecksetzen),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // Welcher Build laeuft - damit sich sagen laesst, ob ein Update angekommen ist.
            Text(
                text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
    }

    if (uiState.showResetDialog) {
        AlertDialog(
            onDismissRequest = viewModel::onResetDismiss,
            title = { Text(stringResource(R.string.einstellungen_zuruecksetzen_frage)) },
            text = { Text(stringResource(R.string.einstellungen_zuruecksetzen_text)) },
            confirmButton = {
                BouncyTextButton(onClick = viewModel::onResetConfirm) {
                    Text(
                        stringResource(R.string.einstellungen_zuruecksetzen),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                BouncyTextButton(onClick = viewModel::onResetDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Die Ueberschrift einer Gruppe von Karten. */
@Composable
private fun GroupTitle(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

/** Ein Satz dazu, was die Angabe bewirkt. */
@Composable
private fun Hint(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
