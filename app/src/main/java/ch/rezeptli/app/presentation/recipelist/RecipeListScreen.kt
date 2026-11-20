package ch.rezeptli.app.presentation.recipelist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import ch.rezeptli.app.R
import ch.rezeptli.app.domain.model.RecipeSummary
import ch.rezeptli.app.presentation.common.ObserveAsEvents
import ch.rezeptli.app.presentation.common.components.BouncyIconButton
import ch.rezeptli.app.presentation.common.components.BouncyTextButton
import ch.rezeptli.app.presentation.common.components.CookedDialog
import ch.rezeptli.app.presentation.common.components.EmptyState
import ch.rezeptli.app.presentation.common.components.IconMotion
import ch.rezeptli.app.presentation.common.components.MotionIcon
import ch.rezeptli.app.presentation.common.components.RecipeListCard
import ch.rezeptli.app.presentation.common.components.RemoveRecipeDialog
import ch.rezeptli.app.presentation.common.components.RezeptliFilterChip
import ch.rezeptli.app.presentation.common.components.RezeptliLoader
import ch.rezeptli.app.presentation.common.components.RezeptliTopBar
import ch.rezeptli.app.presentation.common.components.entrance
import ch.rezeptli.app.presentation.common.components.pressScale
import ch.rezeptli.app.presentation.common.components.rememberEntranceState
import ch.rezeptli.app.presentation.common.theme.RezeptliTheme
import ch.rezeptli.app.presentation.common.theme.Tokens
import kotlinx.coroutines.launch

/**
 * Die eigene Sammlung: alle Rezepte, Suche, Filter und der Weg in den Swipe-Modus.
 *
 * Ein Rezept laesst sich in der Liste wischen: nach rechts ist es gekocht, nach links wird
 * es entfernt. Beides fragt erst nach - ein Wisch beim Scrollen ist schnell passiert.
 */
@Composable
fun RecipeListRoute(
    onRecipeClick: (Long) -> Unit,
    onCreateRecipe: () -> Unit,
    onImportRecipe: () -> Unit,
    onSearchWeb: () -> Unit,
    onStartSwipe: () -> Unit,
    viewModel: RecipeListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recipes = viewModel.recipes.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val swipeNeedsRecipes = stringResource(R.string.recipes_swipe_needs_recipes)
    val entfernt = stringResource(R.string.rezept_entfernt)
    val gekocht = stringResource(R.string.rezept_gekocht_vermerkt)
    val zurueckgenommen = stringResource(R.string.rezept_gekocht_zurueckgenommen)

    ObserveAsEvents(viewModel.events) { event ->
        val meldung = when (event) {
            is RecipeListEvent.Removed -> entfernt.format(event.title)
            is RecipeListEvent.MarkedCooked -> gekocht.format(event.title)
            is RecipeListEvent.CookedCleared -> zurueckgenommen.format(event.title)
        }
        scope.launch { snackbarHostState.showSnackbar(meldung) }
    }

    RecipeListScreen(
        uiState = uiState,
        recipes = recipes,
        snackbarHostState = snackbarHostState,
        onQueryChange = viewModel::onQueryChange,
        onToggleTag = viewModel::onToggleTag,
        onMaxPrepTimeChange = viewModel::onMaxPrepTimeChange,
        onCookedFilterChange = viewModel::onCookedFilterChange,
        onFilterVisibilityChange = viewModel::onFilterVisibilityChange,
        onResetFilter = viewModel::onResetFilter,
        onRecipeClick = onRecipeClick,
        onCreateRecipe = onCreateRecipe,
        onImportRecipe = onImportRecipe,
        onSearchWeb = onSearchWeb,
        onRemoveRequest = viewModel::onRemoveRequest,
        onCookedRequest = viewModel::onCookedRequest,
        onStartSwipe = {
            if (uiState.hasAnyRecipes) {
                onStartSwipe()
            } else {
                scope.launch { snackbarHostState.showSnackbar(swipeNeedsRecipes) }
            }
        },
    )

    uiState.pendingRemoval?.let { recipe ->
        RemoveRecipeDialog(
            title = recipe.title,
            onConfirm = viewModel::onRemoveConfirm,
            onDismiss = viewModel::onDialogDismiss,
        )
    }
    uiState.pendingCooked?.let { recipe ->
        CookedDialog(
            title = recipe.title,
            lastCookedAt = recipe.lastCookedAt,
            onConfirm = viewModel::onCookedConfirm,
            onClear = viewModel::onCookedClear,
            onDismiss = viewModel::onDialogDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(
    uiState: RecipeListUiState,
    recipes: LazyPagingItems<RecipeSummary>,
    snackbarHostState: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onMaxPrepTimeChange: (Int?) -> Unit,
    onCookedFilterChange: (Boolean?) -> Unit,
    onFilterVisibilityChange: (Boolean) -> Unit,
    onResetFilter: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onCreateRecipe: () -> Unit,
    onImportRecipe: () -> Unit,
    onSearchWeb: () -> Unit,
    onRemoveRequest: (RecipeSummary) -> Unit,
    onCookedRequest: (RecipeSummary) -> Unit,
    onStartSwipe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = RezeptliTheme.motion
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            RezeptliTopBar(
                title = if (uiState.firstName.isBlank()) {
                    stringResource(R.string.recipes_title)
                } else {
                    stringResource(R.string.begruessung_mit_name, uiState.firstName)
                },
                actions = {
                    BouncyIconButton(onClick = onImportRecipe) {
                        Icon(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = stringResource(R.string.recipes_import),
                        )
                    }
                    BouncyIconButton(onClick = { onFilterVisibilityChange(!uiState.isFilterVisible) }) {
                        MotionIcon(
                            imageVector = Icons.Filled.FilterList,
                            contentDescription = stringResource(R.string.recipes_filter),
                            motion = IconMotion.SLIDE,
                            trigger = uiState.isFilterVisible,
                            tint = if (uiState.isFilterActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Beide Knoepfe springen beim Erscheinen auf und geben unter dem Finger nach.
                val neuDruck = remember { MutableInteractionSource() }
                FloatingActionButton(
                    onClick = onCreateRecipe,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    shape = Tokens.Radius.LgShape,
                    interactionSource = neuDruck,
                    modifier = Modifier
                        .entrance(1)
                        .pressScale(neuDruck),
                ) {
                    MotionIcon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.recipes_add),
                        motion = IconMotion.SPIN,
                        playOnAppear = true,
                    )
                }
                val wischDruck = remember { MutableInteractionSource() }
                ExtendedFloatingActionButton(
                    onClick = onStartSwipe,
                    icon = {
                        MotionIcon(
                            imageVector = Icons.Filled.SwipeRight,
                            contentDescription = null,
                            motion = IconMotion.WIGGLE,
                            playOnAppear = true,
                        )
                    },
                    text = { Text(stringResource(R.string.recipes_start_swipe)) },
                    shape = Tokens.Radius.PillShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    interactionSource = wischDruck,
                    modifier = Modifier
                        .entrance(2)
                        .pressScale(wischDruck),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SearchField(
                query = uiState.query,
                onQueryChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Gekocht oder noch nicht: die Frage, die man an eine Sammlung am haeufigsten stellt -
            // darum steht sie immer da und nicht erst hinter dem Filterknopf.
            if (uiState.hasAnyRecipes) {
                CookedFilter(
                    cooked = uiState.cooked,
                    onChange = onCookedFilterChange,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            AnimatedVisibility(
                visible = uiState.isFilterVisible,
                enter = if (motion.reduced) {
                    fadeIn(motion.fade())
                } else {
                    expandVertically(motion.gentle()) +
                        fadeIn(motion.fade())
                },
                exit = if (motion.reduced) {
                    fadeOut(motion.fade())
                } else {
                    shrinkVertically(motion.gentle()) +
                        fadeOut(motion.fade())
                },
            ) {
                FilterSection(
                    uiState = uiState,
                    onToggleTag = onToggleTag,
                    onMaxPrepTimeChange = onMaxPrepTimeChange,
                    onResetFilter = onResetFilter,
                )
            }

            RecipeListContent(
                uiState = uiState,
                recipes = recipes,
                onRecipeClick = onRecipeClick,
                onCreateRecipe = onCreateRecipe,
                onImportRecipe = onImportRecipe,
                onSearchWeb = onSearchWeb,
                onResetFilter = onResetFilter,
                onRemoveRequest = onRemoveRequest,
                onCookedRequest = onCookedRequest,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RecipeListContent(
    uiState: RecipeListUiState,
    recipes: LazyPagingItems<RecipeSummary>,
    onRecipeClick: (Long) -> Unit,
    onCreateRecipe: () -> Unit,
    onImportRecipe: () -> Unit,
    onSearchWeb: () -> Unit,
    onResetFilter: () -> Unit,
    onRemoveRequest: (RecipeSummary) -> Unit,
    onCookedRequest: (RecipeSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoading = recipes.loadState.refresh is LoadState.Loading
    val isEmpty = recipes.itemCount == 0 && !isLoading
    val motion = RezeptliTheme.motion
    val eintritt = rememberEntranceState()

    when {
        isEmpty && !uiState.hasAnyRecipes -> EmptyState(
            icon = Icons.Filled.MenuBook,
            title = stringResource(R.string.recipes_empty_title),
            message = stringResource(R.string.recipes_empty_message),
            primaryActionLabel = stringResource(R.string.recipes_empty_action_create),
            onPrimaryAction = onCreateRecipe,
            secondaryActionLabel = stringResource(R.string.websearch_open),
            onSecondaryAction = onSearchWeb,
            modifier = modifier,
        )

        isEmpty -> EmptyState(
            icon = Icons.Filled.SearchOff,
            title = stringResource(R.string.recipes_no_results_title),
            message = stringResource(R.string.recipes_no_results_message),
            primaryActionLabel = stringResource(R.string.recipes_filter_reset),
            onPrimaryAction = onResetFilter,
            modifier = modifier,
        )

        isLoading -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            RezeptliLoader()
        }

        else -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "wischhinweis") {
                Text(
                    text = stringResource(R.string.rezepte_wischhinweis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            items(
                count = recipes.itemCount,
                key = recipes.itemKey { it.id },
            ) { index ->
                val recipe = recipes[index]
                if (recipe != null) {
                    val gleiten = if (motion.reduced) Modifier else Modifier.animateItem()
                    SwipeableRecipeCard(
                        recipe = recipe,
                        onClick = { onRecipeClick(recipe.id) },
                        onRemove = { onRemoveRequest(recipe) },
                        onCooked = { onCookedRequest(recipe) },
                        modifier = gleiten.entrance(index, eintritt, recipe.id),
                    )
                }
            }
        }
    }
}

/**
 * Eine Rezeptkarte, die sich wischen laesst: nach rechts gekocht, nach links entfernen.
 *
 * Die Karte schnappt nach dem Wisch immer zurueck - entschieden wird in der Rueckfrage,
 * nicht durch die Geste. Wer nicht wischen kann oder will, findet beides als Aktion fuer
 * den Screenreader und auf der Detailseite.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRecipeCard(
    recipe: RecipeSummary,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onCooked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entfernen by rememberUpdatedState(onRemove)
    val kochen by rememberUpdatedState(onCooked)
    val zustand = rememberSwipeToDismissBoxState(
        confirmValueChange = { ziel ->
            when (ziel) {
                SwipeToDismissBoxValue.StartToEnd -> kochen()
                SwipeToDismissBoxValue.EndToStart -> entfernen()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
        positionalThreshold = { breite -> breite * SWIPE_THRESHOLD },
    )
    val gekochtText = stringResource(R.string.rezept_gekocht_bestaetigen)
    val entfernenText = stringResource(R.string.rezept_entfernen)

    SwipeToDismissBox(
        state = zustand,
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(gekochtText) {
                    kochen()
                    true
                },
                CustomAccessibilityAction(entfernenText) {
                    entfernen()
                    true
                },
            )
        },
        backgroundContent = { SwipeBackground(direction = zustand.dismissDirection) },
    ) {
        RecipeListCard(recipe = recipe, onClick = onClick)
    }
}

/** Was hinter der Karte zum Vorschein kommt: gruen mit Haken oder rot mit Papierkorb. */
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val gekocht = direction == SwipeToDismissBoxValue.StartToEnd
    val farbe = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
        SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surfaceContainer
    }
    val inhalt = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(Tokens.Radius.LgShape)
            .background(farbe)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (gekocht) Arrangement.Start else Arrangement.End,
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            Icon(
                imageVector = if (gekocht) Icons.Filled.CheckCircle else Icons.Filled.Delete,
                contentDescription = null,
                tint = inhalt,
            )
            Text(
                text = stringResource(if (gekocht) R.string.rezept_gekocht_kurz else R.string.rezept_entfernen),
                style = MaterialTheme.typography.labelLarge,
                color = inhalt,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Alle, nur Gekochte, nur noch nie Gekochte. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CookedFilter(
    cooked: Boolean?,
    onChange: (Boolean?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RezeptliFilterChip(
            selected = cooked == null,
            onClick = { onChange(null) },
            label = stringResource(R.string.rezepte_filter_alle),
        )
        RezeptliFilterChip(
            selected = cooked == true,
            onClick = { onChange(if (cooked == true) null else true) },
            label = stringResource(R.string.rezepte_filter_gekocht),
        )
        RezeptliFilterChip(
            selected = cooked == false,
            onClick = { onChange(if (cooked == false) null else false) },
            label = stringResource(R.string.rezepte_filter_ungekocht),
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.recipes_search_hint)) },
        shape = Tokens.Radius.PillShape,
        leadingIcon = {
            Icon(imageVector = Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                BouncyIconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.recipes_search_clear),
                    )
                }
            }
        },
        singleLine = true,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    uiState: RecipeListUiState,
    onToggleTag: (String) -> Unit,
    onMaxPrepTimeChange: (Int?) -> Unit,
    onResetFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (uiState.availableTags.isNotEmpty()) {
            Text(
                text = stringResource(R.string.recipes_filter_tags),
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.availableTags.forEach { tag ->
                    RezeptliFilterChip(
                        selected = tag in uiState.selectedTags,
                        onClick = { onToggleTag(tag) },
                        label = tag,
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.recipes_filter_time),
            style = MaterialTheme.typography.labelLarge,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PREP_TIME_OPTIONS.forEach { minutes ->
                RezeptliFilterChip(
                    selected = uiState.maxPrepTimeMinutes == minutes,
                    onClick = {
                        onMaxPrepTimeChange(if (uiState.maxPrepTimeMinutes == minutes) null else minutes)
                    },
                    label = if (minutes == null) {
                        stringResource(R.string.filter_time_any)
                    } else {
                        stringResource(R.string.filter_time_minutes, minutes)
                    },
                )
            }
        }

        if (uiState.isFilterActive) {
            BouncyTextButton(onClick = onResetFilter) {
                Text(stringResource(R.string.recipes_filter_reset))
            }
        }
    }
}

private val PREP_TIME_OPTIONS: List<Int?> = listOf(null, 15, 30, 45, 60)

/** So weit muss eine Karte gezogen werden, bis der Wisch zaehlt. */
private const val SWIPE_THRESHOLD = 0.35f
