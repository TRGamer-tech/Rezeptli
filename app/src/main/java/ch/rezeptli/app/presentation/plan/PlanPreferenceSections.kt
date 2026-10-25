package ch.rezeptli.app.presentation.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.profile.Appliance
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.Mood
import ch.rezeptli.app.domain.profile.StoreChain
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.domain.profile.mainDiet
import ch.rezeptli.app.presentation.common.components.ChoiceChipGroup
import ch.rezeptli.app.presentation.common.label
import ch.rezeptli.app.presentation.common.theme.Tokens

/*
 * Die Bausteine, aus denen Einrichtung und Vorlieben bestehen.
 *
 * Beide Bildschirme fragen dasselbe - einmal gefuehrt, einmal als Liste. Damit sie sich
 * nie auseinanderentwickeln, gibt es jede Frage nur einmal.
 */

/** Eine antippbare Kachel mit Auswahlzustand: Rand und Fuellung zeigen, ob sie gewaehlt ist. */
@Composable
private fun SelectableTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = Tokens.Radius.XlShape
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
            ).border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else Tokens.Border.Soft,
                shape = shape,
            ).clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun StoreGrid(
    selected: StoreChain?,
    onSelect: (StoreChain) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StoreChain.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { chain ->
                    SelectableTile(
                        selected = chain == selected,
                        onClick = { onSelect(chain) },
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.7f),
                    ) {
                        Text(
                            text = chain.label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                // Eine ungerade Kachelzahl soll die letzte nicht auf volle Breite ziehen.
                if (row.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun PersonStepper(
    persons: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledIconButton(onClick = { onChange(-1) }, enabled = persons > 1, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.setup_personen_weniger))
        }
        Column(
            modifier = Modifier.heightIn(min = 96.dp).fillMaxWidth(0.5f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = persons.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (persons == 1) {
                    stringResource(R.string.setup_personen_eine)
                } else {
                    stringResource(R.string.setup_personen_anzahl, persons)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledIconButton(
            onClick = { onChange(1) },
            enabled = persons < UserProfile.MAX_HOUSEHOLD_SIZE,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.setup_personen_mehr))
        }
    }
}

@Composable
fun DayPicker(
    selected: Set<Weekday>,
    onToggle: (Weekday) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Weekday.entries.forEach { day ->
                val isSelected = day in selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.72f)
                        .clip(Tokens.Radius.PillShape)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                        ).border(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else Tokens.Border.Soft,
                            Tokens.Radius.PillShape,
                        ).clickable(role = Role.Checkbox) { onToggle(day) }
                        .semantics { this.selected = isSelected },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.short,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
        Text(
            text = stringResource(
                R.string.setup_tage_anzahl,
                selected.size.takeIf { it > 0 } ?: UserProfile.DEFAULT_COOK_DAYS,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun BudgetSlider(
    budget: Int,
    persons: Int,
    meals: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = UserProfile.MIN_BUDGET..UserProfile.MAX_BUDGET
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.setup_budget_betrag, budget),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Slider(
            value = budget.toFloat(),
            onValueChange = { onChange((it / BUDGET_STEP).toInt() * BUDGET_STEP) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / BUDGET_STEP - 1,
        )
        val perMeal = budget.toDouble() / (persons.coerceAtLeast(1) * meals.coerceAtLeast(1))
        Text(
            text = stringResource(R.string.setup_budget_pro_mahlzeit, "%.2f".format(perMeal)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun MoodPicker(
    selected: Set<Mood>,
    onToggle: (Mood) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChipGroup(
            options = Mood.entries,
            selected = selected,
            label = { it.label },
            onToggle = onToggle,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.setup_stimmung_zaehler, selected.size, UserProfile.MAX_MOODS),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun DietPicker(
    profile: UserProfile,
    onMainDiet: (Diet?) -> Unit,
    onToggleDiet: (Diet) -> Unit,
    onToggleIntolerance: (Intolerance) -> Unit,
    modifier: Modifier = Modifier,
    showIntolerances: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val options = listOf<Diet?>(null, Diet.VEGETARISCH, Diet.VEGAN, Diet.PESCETARISCH)
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { diet ->
                    SelectableTile(
                        selected = profile.mainDiet == diet,
                        onClick = { onMainDiet(diet) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 72.dp),
                    ) {
                        Text(
                            text = diet?.label() ?: stringResource(R.string.setup_ernaehrung_keine),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        ChoiceChipGroup(
            options = listOf(Diet.KOHLENHYDRATARM),
            selected = profile.diets,
            label = { it.label() },
            onToggle = onToggleDiet,
        )
        if (showIntolerances) {
            Text(
                text = stringResource(R.string.setup_unvertraeglich_titel),
                style = MaterialTheme.typography.titleMedium,
            )
            ChoiceChipGroup(
                options = Intolerance.entries,
                selected = profile.intolerances,
                label = { it.label() },
                onToggle = onToggleIntolerance,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.setup_unvertraeglich_hinweis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AppliancePicker(
    selected: Set<Appliance>,
    onToggle: (Appliance) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Appliance.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { appliance ->
                    SelectableTile(
                        selected = appliance in selected,
                        onClick = { onToggle(appliance) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 88.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = appliance.emoji(), style = MaterialTheme.typography.headlineMedium)
                            Text(
                                text = appliance.label,
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                if (row.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun Appliance.emoji(): String = when (this) {
    Appliance.BACKOFEN -> "🔥"
    Appliance.HERD -> "🍳"
    Appliance.MIKROWELLE -> "⚡"
    Appliance.HEISSLUFT -> "🌬️"
    Appliance.SLOWCOOKER -> "🍲"
    Appliance.REISKOCHER -> "🍚"
}

private const val BUDGET_STEP = 10
