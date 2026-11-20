package ch.rezeptli.app.presentation.recipedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.model.BringLink
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.steps.RecipeStep
import ch.rezeptli.app.presentation.common.ObserveAsEvents
import ch.rezeptli.app.presentation.common.components.BouncyIconButton
import ch.rezeptli.app.presentation.common.components.BouncyOutlinedButton
import ch.rezeptli.app.presentation.common.components.BouncyTextButton
import ch.rezeptli.app.presentation.common.components.IconMotion
import ch.rezeptli.app.presentation.common.components.MotionIcon
import ch.rezeptli.app.presentation.common.components.RecipeImage
import ch.rezeptli.app.presentation.common.components.RezeptliLoader
import ch.rezeptli.app.presentation.common.components.RezeptliTopBar
import ch.rezeptli.app.presentation.common.components.SectionCard
import ch.rezeptli.app.presentation.common.components.entrance
import ch.rezeptli.app.presentation.common.components.pressScale
import ch.rezeptli.app.presentation.common.displayText
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.common.theme.Tokens
import kotlinx.coroutines.launch

/** Detailansicht eines Rezepts mit Zutaten und Zubereitung. */
@Composable
fun RecipeDetailRoute(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onStartCooking: (Long) -> Unit,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val cookedMessage = stringResource(R.string.recipe_marked_cooked)

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            RecipeDetailEvent.Deleted -> onBack()
            RecipeDetailEvent.MarkedAsCooked -> scope.launch {
                snackbarHostState.showSnackbar(cookedMessage)
            }
        }
    }

    RecipeDetailScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onEdit = { uiState.recipe?.let { onEdit(it.id) } },
        onStartCooking = { uiState.recipe?.let { onStartCooking(it.id) } },
        onDeleteRequest = viewModel::onDeleteRequest,
        onDeleteDismiss = viewModel::onDeleteDismiss,
        onDeleteConfirm = viewModel::onDeleteConfirm,
        onMarkCooked = viewModel::onMarkCooked,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    uiState: RecipeDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onStartCooking: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onMarkCooked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recipe = uiState.recipe

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            RezeptliTopBar(
                title = recipe?.title.orEmpty(),
                onBack = onBack,
                actions = {
                    if (recipe != null) {
                        BouncyIconButton(onClick = onMarkCooked) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = stringResource(R.string.recipe_mark_cooked),
                            )
                        }
                        BouncyIconButton(onClick = onDeleteRequest) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (recipe != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val bearbeitenDruck = remember { MutableInteractionSource() }
                    SmallFloatingActionButton(
                        onClick = onEdit,
                        interactionSource = bearbeitenDruck,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .entrance(1)
                            .pressScale(bearbeitenDruck),
                    ) {
                        MotionIcon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.action_edit),
                            motion = IconMotion.WIGGLE,
                            playOnAppear = true,
                        )
                    }
                    // Der Kochmodus ist das, was man am Herd will - deshalb der grosse Knopf.
                    if (recipe.steps.isNotEmpty()) {
                        val kochenDruck = remember { MutableInteractionSource() }
                        ExtendedFloatingActionButton(
                            onClick = onStartCooking,
                            icon = {
                                MotionIcon(
                                    imageVector = Icons.Filled.Restaurant,
                                    contentDescription = null,
                                    motion = IconMotion.HOP,
                                    playOnAppear = true,
                                )
                            },
                            text = { Text(stringResource(R.string.kochmodus_starten)) },
                            shape = Tokens.Radius.PillShape,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            interactionSource = kochenDruck,
                            modifier = Modifier
                                .entrance(2)
                                .pressScale(kochenDruck),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                RezeptliLoader()
            }

            recipe == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.recipe_not_found))
            }

            else -> RecipeDetailContent(
                recipe = recipe,
                persons = uiState.persons,
                contentPadding = innerPadding,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (uiState.isDeleteDialogVisible && recipe != null) {
        AlertDialog(
            onDismissRequest = onDeleteDismiss,
            title = { Text(stringResource(R.string.recipe_delete_title)) },
            text = { Text(stringResource(R.string.recipe_delete_message, recipe.title)) },
            confirmButton = {
                BouncyTextButton(onClick = onDeleteConfirm) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                BouncyTextButton(onClick = onDeleteDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeDetailContent(
    recipe: Recipe,
    persons: Int,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val liste = rememberLazyListState()
    val motion = RezeptliTheme.motion
    LazyColumn(
        modifier = modifier,
        state = liste,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 120.dp,
        ),
    ) {
        item(key = "photo") {
            // Das Foto gleitet beim Scrollen langsamer weg als der Text darunter (Parallaxe)
            // und blendet dabei aus - bei reduzierter Bewegung scrollt es ganz normal mit.
            RecipeImage(
                photoUri = recipe.photoUri,
                title = recipe.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .graphicsLayer {
                        if (!motion.reduced && liste.firstVisibleItemIndex == 0) {
                            val versatz = liste.firstVisibleItemScrollOffset.toFloat()
                            translationY = versatz * PARALLAX
                            alpha = (1f - versatz / size.height).coerceIn(0f, 1f)
                        }
                    },
            )
        }

        item(key = "titel") {
            Text(
                text = recipe.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .entrance(0)
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp),
            )
        }

        item(key = "meta") {
            Column(
                modifier = Modifier
                    .entrance(1)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                recipe.prepTimeMinutes?.let { minutes ->
                    Text(
                        text = stringResource(R.string.recipe_prep_time_long, minutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val uriHandler = LocalUriHandler.current
                val quelle = recipe.sourceUrl
                val quellenName = recipe.sourceName ?: quelle?.substringAfter("//")?.substringBefore('/')
                // Fehlt die Quelle unter den Etiketten, kommt sie dazu - sonst gaebe es keinen Weg zum Original.
                val etiketten = if (quelle != null && quellenName != null && quellenName !in recipe.tags) {
                    recipe.tags + quellenName
                } else {
                    recipe.tags
                }
                if (etiketten.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        etiketten.forEach { tag ->
                            // Das Etikett der Quelle fuehrt zum Originalrezept.
                            val istQuelle = quelle != null && tag == quellenName
                            AssistChip(
                                onClick = { if (istQuelle && quelle != null) uriHandler.openUri(quelle) },
                                label = { Text(tag) },
                                trailingIcon = if (istQuelle) {
                                    {
                                        Icon(
                                            Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = stringResource(R.string.rezept_quelle_oeffnen),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
                // Bring liest die Zutaten selbst von der Quelle und rechnet sie auf die
                // eigene Personenzahl um. Geht nur fuer Rezepte mit Quelladresse.
                BringLink.forRecipe(quelle, recipe.servings, persons)?.let { link ->
                    BouncyOutlinedButton(
                        onClick = { uriHandler.openUri(link) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        MotionIcon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = null,
                            motion = IconMotion.WIGGLE,
                            playOnAppear = true,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(stringResource(R.string.rezept_in_bring), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        // Zutaten und Zubereitung stehen je in einer Karte statt als lose Zeilen mit
        // Trennlinie. Ein Rezept hat selten mehr als ein paar Dutzend Zeilen - die
        // Karte darf deshalb ein einziger Listeneintrag sein.
        item(key = "ingredients") {
            SectionCard(
                title = stringResource(R.string.recipe_ingredients),
                spacing = 8,
                index = 2,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                if (recipe.ingredients.isEmpty()) {
                    Text(
                        text = stringResource(R.string.recipe_no_ingredients),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    recipe.ingredients.forEach { ingredient ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape,
                                    ),
                            )
                            Text(
                                text = ingredient.displayText(),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }

        item(key = "instructions") {
            SectionCard(
                title = stringResource(R.string.recipe_instructions),
                spacing = 14,
                index = 3,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                if (recipe.steps.isEmpty()) {
                    Text(
                        text = recipe.instructions.ifBlank {
                            stringResource(R.string.recipe_no_instructions)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    recipe.steps.forEach { step -> StepRow(step = step) }
                }
            }
        }
    }
}

/** Ein nummerierter Schritt in der Detailansicht. */
@Composable
private fun StepRow(step: RecipeStep) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Die Nummer in einem gruenen Kreis - wie ein Etikett an einem Glas.
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${step.position + 1}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = step.text, style = MaterialTheme.typography.bodyLarge)
            step.timerMinutes?.let { minutes ->
                Text(
                    text = pluralStringResource(R.plurals.kochmodus_timer_minuten, minutes, minutes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Wie viel langsamer das Foto wegscrollt als der Rest. */
private const val PARALLAX = 0.5f
