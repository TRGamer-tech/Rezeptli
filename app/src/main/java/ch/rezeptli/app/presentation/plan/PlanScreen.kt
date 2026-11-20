package ch.rezeptli.app.presentation.plan

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import ch.rezeptli.app.domain.profile.Currency
import ch.rezeptli.app.domain.profile.MealSlot
import ch.rezeptli.app.domain.profile.Weekday
import ch.rezeptli.app.presentation.common.ObserveAsEvents
import ch.rezeptli.app.presentation.common.components.BouncyButton
import ch.rezeptli.app.presentation.common.components.BouncyIconButton
import ch.rezeptli.app.presentation.common.components.BouncyOutlinedButton
import ch.rezeptli.app.presentation.common.components.BouncyTextButton
import ch.rezeptli.app.presentation.common.components.CookingPot
import ch.rezeptli.app.presentation.common.components.PrepTimeLabel
import ch.rezeptli.app.presentation.common.components.RecipeImage
import ch.rezeptli.app.presentation.common.components.RezeptliLoader
import ch.rezeptli.app.presentation.common.components.RezeptliProgressBar
import ch.rezeptli.app.presentation.common.components.SpinningIcon
import ch.rezeptli.app.presentation.common.components.bouncyClickable
import ch.rezeptli.app.presentation.common.components.entrance
import ch.rezeptli.app.presentation.common.components.rememberEntranceState
import ch.rezeptli.app.presentation.common.components.wiggleOnChange
import ch.rezeptli.app.presentation.common.formatMoney
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.common.theme.TagColors
import ch.rezeptli.app.presentation.common.theme.Tokens
import ch.rezeptli.app.presentation.common.theme.cardSurface
import kotlinx.coroutines.launch
import java.time.LocalTime

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
    onReplace: (Weekday, MealSlot) -> Unit,
    onRebuildRequest: () -> Unit,
    onRebuildDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eintritt = rememberEntranceState()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            uiState.isLoading -> RezeptliLoader(modifier = Modifier.padding(padding))

            !uiState.hasPlan -> EmptyPlan(onBuildPlan, Modifier.padding(padding))

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "kopf") { Header(uiState, Modifier.entrance(0, eintritt, "kopf")) }
                item(key = "kosten") { CostCard(uiState, Modifier.entrance(1, eintritt, "kosten")) }
                item(key = "einkauf") {
                    ShoppingCard(uiState, onOpenShoppingList, Modifier.entrance(2, eintritt, "einkauf"))
                }
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
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .entrance(3, eintritt, "woche"),
                    )
                }
                // Tag fuer Tag: erst der Wochentag, darunter Mittag- und Abendessen.
                uiState.days.forEachIndexed { tagIndex, tag ->
                    item(key = "tag-" + tag.day.name) {
                        Text(
                            text = tag.day.label,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .entrance(tagIndex + 4, eintritt, "tag-" + tag.day.name),
                        )
                    }
                    tag.meals.forEach { meal ->
                        val schluessel = meal.day.name + "-" + meal.slot.name
                        item(key = schluessel) {
                            // Wird ein Gericht getauscht, gleitet die Karte an ihren Platz statt zu springen.
                            val gleiten = if (RezeptliTheme.motion.reduced) Modifier else Modifier.animateItem()
                            MealCard(
                                meal = meal,
                                currency = uiState.currency,
                                showSlot = uiState.showsSlots,
                                isReplacing = uiState.replacing == meal.place,
                                doesNotFit = meal.place in uiState.unfitting,
                                onClick = { onOpenRecipe(meal.recipeId) },
                                onReplace = { onReplace(meal.day, meal.slot) },
                                modifier = gleiten.entrance(tagIndex + 4, eintritt, schluessel),
                            )
                        }
                    }
                }
                item(key = "neu") {
                    BouncyOutlinedButton(
                        onClick = onRebuildRequest,
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
                BouncyTextButton(onClick = {
                    onRebuildDismiss()
                    onBuildPlan()
                }) { Text(stringResource(R.string.plan_neu_bauen_bestaetigen)) }
            },
            dismissButton = {
                BouncyTextButton(onClick = onRebuildDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Noch kein Plan: der Topf brodelt und wartet auf die erste Woche. */
@Composable
private fun EmptyPlan(onBuildPlan: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CookingPot(size = 120.dp, modifier = Modifier.entrance(0))
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.plan_leer_titel),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.entrance(1),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.plan_leer_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.entrance(2),
        )
        Spacer(Modifier.height(28.dp))
        BouncyButton(
            onClick = onBuildPlan,
            modifier = Modifier
                .entrance(3)
                .heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.plan_leer_aktion), style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun Header(uiState: PlanUiState, modifier: Modifier = Modifier) {
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = greeting, style = MaterialTheme.typography.headlineLarge)
        uiState.store?.let { store ->
            val farbe = RezeptliTheme.tags.herb
            Row(
                modifier = Modifier
                    .clip(Tokens.Radius.PillShape)
                    .background(farbe.container)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Storefront,
                    contentDescription = null,
                    tint = farbe.content,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.plan_gerechnet_mit, store.label),
                    style = MaterialTheme.typography.labelLarge,
                    color = farbe.content,
                )
            }
        }
    }
}

/**
 * Was die Woche kostet - die Zahl zaehlt hoch, und der Balken fuellt sich bis zum Budget.
 * Ueber dem Budget wird beides tomatenrot.
 */
@Composable
private fun CostCard(uiState: PlanUiState, modifier: Modifier = Modifier) {
    val motion = RezeptliTheme.motion
    val warnung = uiState.isOverBudget
    val barColor = if (warnung) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val angezeigt by animateFloatAsState(
        targetValue = uiState.estimatedCost.toFloat(),
        animationSpec = motion.progress(),
        label = "kosten",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface(Tokens.Radius.LgShape)
            .padding(18.dp),
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
                formatMoney(angezeigt.toDouble(), uiState.currency),
                formatMoney(uiState.budget, uiState.currency),
            ),
            style = MaterialTheme.typography.headlineSmall,
            color = if (warnung) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        RezeptliProgressBar(progress = uiState.budgetShare, color = barColor)
        Text(
            text = stringResource(
                if (warnung) R.string.plan_kosten_ueber else R.string.plan_kosten_hinweis,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (warnung) barColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Die anderen gewaehlten Laeden zum Vergleich - gerechnet wird mit dem Hauptladen.
        if (uiState.comparisons.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.plan_vergleich_titel),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                uiState.comparisons.forEach { vergleich ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = vergleich.store.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(
                                R.string.plan_vergleich_betrag,
                                formatMoney(vergleich.cost, uiState.currency),
                            ),
                            style = RezeptliTheme.amountStyle,
                        )
                    }
                }
            }
        }
    }
}

/** Die Einkaufsliste der Woche - der Wagen wackelt, sobald etwas abgehakt wurde. */
@Composable
private fun ShoppingCard(uiState: PlanUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
            .cardSurface(Tokens.Radius.LgShape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(Tokens.Radius.CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.wiggleOnChange(uiState.itemsChecked),
                )
            }
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
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (uiState.itemsTotal > 0) {
            RezeptliProgressBar(
                progress = uiState.itemsChecked.toFloat() / uiState.itemsTotal,
                height = 6.dp,
            )
        }
    }
}

@Composable
private fun MealCard(
    meal: PlanEntry,
    currency: Currency,
    showSlot: Boolean,
    isReplacing: Boolean,
    doesNotFit: Boolean,
    onClick: () -> Unit,
    onReplace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
            .cardSurface(Tokens.Radius.XlShape),
    ) {
        Box {
            RecipeImage(
                photoUri = meal.photoUri,
                title = meal.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp),
            )
            // Die Tageszeit als Etikett auf dem Foto - wie ein Zettel am Marktstand.
            if (showSlot) {
                Text(
                    text = meal.slot.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(Tokens.Radius.PillShape)
                        .background(RezeptliTheme.accents.cardFill)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            // Gekocht: ein gruener Haken unten auf dem Foto.
            if (meal.isCooked) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                        .clip(Tokens.Radius.PillShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.plan_gekocht),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            BouncyIconButton(
                onClick = onReplace,
                enabled = !isReplacing,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(44.dp)
                    .clip(Tokens.Radius.CircleShape)
                    .background(RezeptliTheme.accents.cardFill),
            ) {
                SpinningIcon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.plan_tauschen),
                    spinning = isReplacing,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = meal.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Die Vorlieben haben sich geaendert, der Plan nicht: Dann steht es hier, statt dass
            // die Mahlzeit stillschweigend verschwindet oder stillschweigend bleibt.
            if (doesNotFit) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.plan_passt_nicht),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LabelChip(meal.label)
                meal.prepTimeMinutes?.let { PrepTimeLabel(it) }
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatMoney(meal.cost, currency),
                    style = RezeptliTheme.amountStyle,
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

/** Der farbige Chip einer Mahlzeit - "Schnell", "Proteinreich", "Fakeaway" … */
@Composable
fun LabelChip(label: MealLabel, modifier: Modifier = Modifier) {
    val farbe = label.colors()
    Text(
        text = label.text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = farbe.content,
        modifier = modifier
            .clip(Tokens.Radius.PillShape)
            .background(farbe.container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun MealLabel.colors(): TagColors {
    val tags = RezeptliTheme.tags
    return when (this) {
        MealLabel.PROTEIN -> tags.tomato
        MealLabel.SCHNELL -> tags.butter
        MealLabel.FAKEAWAY -> tags.berry
        MealLabel.FAMILIE -> tags.apricot
        MealLabel.COMFORT -> tags.apricot
        MealLabel.LEICHT -> tags.lake
        MealLabel.DARM -> tags.sage
        MealLabel.VEGAN -> tags.herb
        MealLabel.VEGETARISCH -> tags.herb
        MealLabel.ALLTAG -> tags.sage
    }
}

private const val MIDDAY = 11
private const val EVENING = 18
