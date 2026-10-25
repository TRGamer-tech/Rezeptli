package ch.rezeptli.app.presentation.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.plan.MealLabel
import ch.rezeptli.app.domain.plan.PlanEntry
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.presentation.common.ObserveAsEvents
import ch.rezeptli.app.presentation.common.components.PrepTimeLabel
import ch.rezeptli.app.presentation.common.components.RecipeImage
import ch.rezeptli.app.presentation.common.theme.Tokens
import ch.rezeptli.app.presentation.common.theme.cardSurface
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale

/** Die Plan-Seite: Gruss, Kosten gegen Budget, Einkaufsliste, dann Tag fuer Tag die Mahlzeiten. */
@Composable
fun PlanRoute(
    onOpenRecipe: (Long) -> Unit,
    onOpenShoppingList: () -> Unit,
    onBuildPlan: () -> Unit,
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noAlternative = stringResource(R.string.plan_tauschen_keine)

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            PlanEvent.NoAlternative -> scope.launch { snackbarHostState.showSnackbar(noAlternative) }
        }
    }

    PlanScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onOpenRecipe = onOpenRecipe,
        onOpenShoppingList = onOpenShoppingList,
        onBuildPlan = onBuildPlan,
        onReplace = viewModel::onReplace,
        onRebuildRequest = viewModel::onRebuildRequest,
        onRebuildDismiss = viewModel::onRebuildDismiss,
    )
}

@Composable
fun PlanScreen(
    uiState: PlanUiState,
    snackbarHostState: SnackbarHostState,
    onOpenRecipe: (Long) -> Unit,
    onOpenShoppingList: () -> Unit,
    onBuildPlan: () -> Unit,
    onReplace: (Weekday) -> Unit,
    onRebuildRequest: () -> Unit,
    onRebuildDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }

            !uiState.hasPlan -> EmptyPlan(onBuildPlan, Modifier.padding(padding))

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "kopf") { Header(uiState) }
                item(key = "kosten") { CostCard(uiState) }
                item(key = "einkauf") { ShoppingCard(uiState, onOpenShoppingList) }
                if (uiState.meals.size < uiState.expectedMeals) {
                    item(key = "unvollstaendig") {
                        Text(
                            text = stringResource(
                                R.string.plan_unvollstaendig,
                                uiState.meals.size,
                                uiState.expectedMeals,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item(key = "woche") {
                    Text(
                        text = stringResource(R.string.plan_woche_titel),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(uiState.meals, key = { it.day.name }) { meal ->
                    MealCard(
                        meal = meal,
                        isReplacing = uiState.replacingDay == meal.day,
                        onClick = { onOpenRecipe(meal.recipeId) },
                        onReplace = { onReplace(meal.day) },
                    )
                }
                item(key = "neu") {
                    OutlinedButton(
                        onClick = onRebuildRequest,
                        shape = Tokens.Radius.PillShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.plan_neu_bauen)) }
                }
            }
        }
    }

    if (uiState.showRebuildDialog) {
        AlertDialog(
            onDismissRequest = onRebuildDismiss,
            title = { Text(stringResource(R.string.plan_neu_bauen_titel)) },
            text = { Text(stringResource(R.string.plan_neu_bauen_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onRebuildDismiss()
                    onBuildPlan()
                }) { Text(stringResource(R.string.plan_neu_bauen_bestaetigen)) }
            },
            dismissButton = {
                TextButton(onClick = onRebuildDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun EmptyPlan(onBuildPlan: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.plan_leer_titel),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.plan_leer_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onBuildPlan,
            shape = Tokens.Radius.PillShape,
            modifier = Modifier.heightIn(min = 52.dp),
        ) { Text(stringResource(R.string.plan_leer_aktion)) }
    }
}

@Composable
private fun Header(uiState: PlanUiState) {
    val hour = LocalTime.now().hour
    val name = uiState.name.ifBlank { stringResource(R.string.plan_gruss_name_standard) }
    val greeting = stringResource(
        when {
            hour < MIDDAY -> R.string.plan_gruss_morgen
            hour < EVENING -> R.string.plan_gruss_tag
            else -> R.string.plan_gruss_abend
        },
        name,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = greeting, style = MaterialTheme.typography.headlineMedium)
        uiState.store?.let { store ->
            Text(
                text = stringResource(R.string.plan_geplant_fuer, store.label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(Tokens.Radius.PillShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun CostCard(uiState: PlanUiState) {
    val barColor = if (uiState.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(Tokens.Radius.LgShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.plan_kosten_titel),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                R.string.plan_kosten_von,
                String.format(Locale.GERMAN, "%.2f", uiState.estimatedCost),
                uiState.budget,
            ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        LinearProgressIndicator(
            progress = { uiState.budgetShare },
            color = barColor,
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(Tokens.Radius.PillShape),
        )
        Text(
            text = stringResource(
                if (uiState.isOverBudget) R.string.plan_kosten_ueber else R.string.plan_kosten_hinweis,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (uiState.isOverBudget) barColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShoppingCard(uiState: PlanUiState, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardSurface(Tokens.Radius.LgShape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Filled.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(R.string.plan_einkauf_titel), style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (uiState.itemsTotal == 0) {
                    stringResource(R.string.plan_einkauf_leer)
                } else {
                    stringResource(R.string.plan_einkauf_stand, uiState.itemsChecked, uiState.itemsTotal)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun MealCard(
    meal: PlanEntry,
    isReplacing: Boolean,
    onClick: () -> Unit,
    onReplace: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = meal.day.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .cardSurface(Tokens.Radius.LgShape)
                .clickable(onClick = onClick),
        ) {
            Box {
                RecipeImage(
                    photoUri = meal.photoUri,
                    title = meal.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
                IconButton(
                    onClick = onReplace,
                    enabled = !isReplacing,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(44.dp)
                        .clip(Tokens.Radius.CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                ) {
                    if (isReplacing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.plan_tauschen),
                            tint = Color.White,
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = meal.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LabelChip(meal.label)
                    meal.prepTimeMinutes?.let { PrepTimeLabel(it) }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(
                            R.string.plan_karte_preis,
                            String.format(Locale.GERMAN, "%.2f", meal.cost),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Text(
                    text = if (meal.servings == 1) {
                        stringResource(R.string.plan_karte_portion)
                    } else {
                        stringResource(R.string.plan_karte_portionen, meal.servings)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Der farbige Chip einer Mahlzeit - "Schnell", "Proteinreich", "Fakeaway" … */
@Composable
fun LabelChip(label: MealLabel, modifier: Modifier = Modifier) {
    val color = label.color()
    Text(
        text = label.text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
            .clip(Tokens.Radius.PillShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun MealLabel.color(): Color = when (this) {
    MealLabel.PROTEIN -> Tokens.Mesh.Orange
    MealLabel.SCHNELL -> Tokens.Mesh.Yellow
    MealLabel.FAKEAWAY -> Tokens.Mesh.Pink
    MealLabel.FAMILIE -> Tokens.Mesh.Mint
    MealLabel.COMFORT -> Tokens.Mesh.Violet
    MealLabel.LEICHT -> Tokens.Mesh.Teal
    MealLabel.DARM -> Tokens.Mesh.Cyan
    MealLabel.VEGAN -> Tokens.Mesh.Mint
    MealLabel.VEGETARISCH -> Tokens.Mesh.Mint
    MealLabel.ALLTAG -> Tokens.Purple.Purple300
}

private const val MIDDAY = 11
private const val EVENING = 18
