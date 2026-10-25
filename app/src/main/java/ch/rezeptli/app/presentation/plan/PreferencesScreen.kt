package ch.rezeptli.app.presentation.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.presentation.common.components.RezeptliTopBar
import ch.rezeptli.app.presentation.common.components.SectionCard
import ch.rezeptli.app.presentation.common.theme.Tokens

/** Der Vorlieben-Tab: alles, was den Plan formt, auf einer Seite. */
@Composable
fun PreferencesRoute(
    onBuildPlan: () -> Unit,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()

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
            SectionCard(title = stringResource(R.string.vorlieben_laden)) {
                StoreGrid(selected = profile.store, onSelect = viewModel::onStoreSelect)
            }
            SectionCard(title = stringResource(R.string.setup_personen_titel)) {
                PersonStepper(persons = profile.persons, onChange = viewModel::onPersonsChange)
            }
            SectionCard(title = stringResource(R.string.setup_tage_titel)) {
                DayPicker(selected = profile.cookDays, onToggle = viewModel::onDayToggle)
            }
            SectionCard(title = stringResource(R.string.setup_budget_titel)) {
                BudgetSlider(
                    budget = profile.weeklyBudget ?: UserProfile.DEFAULT_BUDGET,
                    persons = profile.persons,
                    meals = profile.mealsPerWeek,
                    onChange = viewModel::onBudgetChange,
                )
            }
            SectionCard(title = stringResource(R.string.setup_stimmung_titel)) {
                MoodPicker(selected = profile.moods, onToggle = viewModel::onMoodToggle)
            }
            SectionCard(title = stringResource(R.string.setup_ernaehrung_titel)) {
                DietPicker(
                    profile = profile,
                    onMainDiet = viewModel::onMainDiet,
                    onToggleDiet = viewModel::onDietToggle,
                    onToggleIntolerance = viewModel::onIntoleranceToggle,
                )
            }
            SectionCard(title = stringResource(R.string.setup_geraete_titel)) {
                AppliancePicker(selected = profile.appliances, onToggle = viewModel::onApplianceToggle)
            }
            Button(
                onClick = onBuildPlan,
                shape = Tokens.Radius.PillShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.vorlieben_plan_neu)) }
        }
    }
}
